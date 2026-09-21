# HyperGravel — Architecture

A Minecraft: Java Edition reverse proxy. Fronts every
Paper backend behind one address, authenticates once, and moves players
between backends without a re-login.

Replaces Velocity. Tuned for our workload: frequent backend restarts, a TCP
tunnel in front of us, Bedrock players, and integrations we own.

---

## 1. Decisions (locked)

| Decision | Choice | Why |
| --- | --- | --- |
| Core runtime | **Java 21+ / Netty 4.2** | Everything we must interoperate with is JVM: Velocity modern-forwarding HMAC, Adventure/MiniMessage, ViaVersion, Geyser/Floodgate, LuckPerms. Rust/Go means reimplementing four ecosystems. A tuned JVM on generational ZGC holds sub-ms p99 on a passthrough path that does no allocation. |
| Multi-version | **Bundled Via layer (done)** | ~40 protocol mappings maintained by someone else, day-one support each MC release. Only old clients pay the re-encode; current-gen stays zero-copy. |
| Bedrock | **Geyser sidecar; Floodgate handling in-core** | HyperGravel parses the Floodgate handshake payload and forwards Bedrock identity to backends. No RakNet in our core. |
| Backend compat | **Wire-compatible with Paper + Velocity modern forwarding** | Backends need no companion plugin. `velocity-modern`, `bungeeguard`, `legacy`, and `none` are all selectable per-server. A companion plugin is optional, for extras only. |
| Scale target | **No fixed ceiling** | Every pool, table, and queue is config-sized. Nothing in the hot path is O(n) in player count. Node capacity is a memory/FD question, not a code question. |

### Why the JVM will hold the latency target

The passthrough path for a player already on a backend performs, per packet:
one `readableBytes()` check, one varint read for the packet id, one bitset
lookup, and a `retainedSlice()` write to the peer channel. No copy, no decode,
no allocation outside the pooled arena. The only GC pressure at steady state is
whatever the *inspected* packets produce, and those are a low-single-digit
percentage of traffic. Generational ZGC's sub-millisecond pauses are then
irrelevant to p99 because the pauses are shorter than a scheduler quantum.

The place Java would lose is per-connection memory. That is addressed in §4.

---

## 2. Threading model

Three thread groups. Nothing else runs on a Netty thread.

```
 ┌─ boss group ────────┐   1 thread per bind address.
 │  accept() only      │   Accept, apply the connection throttle, hand off.
 └─────────────────────┘

 ┌─ worker group ──────┐   default: availableProcessors(), config-tunable.
 │  all packet I/O     │   Client channel and its backend channel are PINNED
 │  all state machines │   to the SAME EventLoop. A server switch never
 │  all forwarding     │   migrates loops.
 └─────────────────────┘

 ┌─ scheduler / work ──┐   virtual threads (Java 21 Loom).
 │  Mojang session API │   Anything that blocks, waits on a socket we don't
 │  Discord webhooks   │   own, or touches the filesystem.
 │  config reload      │   Results are handed back with channel.eventLoop()
 │  extension tasks    │   .execute(...) — never touched cross-thread.
 └─────────────────────┘
```

**The pinning rule is the load-bearing one.** Because a player's client channel
and backend channel share an EventLoop:

- forwarding is a plain `write()` with no cross-thread handoff and no queue,
- per-player state needs no locks and no `volatile`,
- a server switch is a local operation on one thread.

`BackendConnector` bootstraps the backend channel with
`.group(clientChannel.eventLoop())` to guarantee this.

**Transport selection**, in preference order: `io_uring` → `epoll` → NIO.
Detected at startup, logged once. io_uring is opt-in via config because kernel
support varies; epoll is the default on Linux.

**Blocking is a bug.** `HyperGravelBootstrap` installs a debug-mode
`BlockHound`-style guard in dev profiles; in production the rule is enforced by
review and by the fact that every blocking API is behind `Scheduler`.

---

## 3. Connection lifecycle

```
TCP accept
  │
  ├─ [optional] HAProxyMessageDecoder      ← only if remote ∈ proxy-protocol trust list
  │                                          replaces the channel's remote address
  ├─ ReadTimeoutHandler                     ← handshake must arrive inside N ms
  ├─ VarIntFrameDecoder ────┐
  ├─ VarIntLengthEncoder    │ framing
  ├─ CipherDecoder/Encoder  │ inserted after EncryptionResponse
  ├─ CompressionDecoder/Enc │ inserted after SetCompression
  ├─ MinecraftDecoder/Enc   │ id ↔ Packet, state-aware
  └─ MinecraftConnection    ← holds the current SessionHandler
        │
        ├─ HandshakeSessionHandler  → reads intent
        │     ├─ STATUS → StatusSessionHandler   (cached response, sub-ms, never touches a backend)
        │     ├─ LOGIN  → LoginSessionHandler
        │     └─ TRANSFER → LoginSessionHandler with transfer cookie
        │
        ├─ LoginSessionHandler
        │     LoginStart → EncryptionRequest → EncryptionResponse
        │        → verify token, derive AES key, install cipher
        │        → Mojang hasJoined (virtual thread, off-loop)
        │        → SetCompression, LoginSuccess
        │        → LoginAcknowledged → CONFIGURATION
        │        → anti-bot gate (Phase 3) must pass before any backend is touched
        │        → route: try-order from config, PreServerConnectEvent, connect
        │
        └─ ClientPlaySessionHandler ⇄ BackendPlaySessionHandler
              steady state — see §5
```

### Login state notes

- **Encryption**: 1024-bit RSA keypair generated at startup, never persisted.
  AES/CFB8/NoPadding with the shared secret as both key and IV, matching vanilla.
- **Compression**: threshold from config (default 256). `SetCompression` is
  sent before `LoginSuccess`, as the protocol requires.
- **Offline mode**: UUID is `OfflinePlayer:<name>` MD5, version-3, same as vanilla.
- **Floodgate**: Bedrock players arrive from the Geyser sidecar with the
  Floodgate payload appended to the handshake's server-address field, split on
  `\0`. HyperGravel parses it, verifies the signature against the Floodgate key file,
  and marks the player as Bedrock. Their skin/xuid data rides along to backends.

---

## 4. Buffer strategy

**Allocator.** One `PooledByteBufAllocator` with direct buffers, sized at
startup. Arena count is pinned to the worker count so allocation is
thread-local and lock-free; the default heuristic of `2 × cores` creates arenas
that no thread ever touches and wastes address space.

```java
new PooledByteBufAllocator(
    /* preferDirect */ true,
    /* nHeapArena  */ 0,              // nothing on-heap in the hot path
    /* nDirectArena*/ workerCount,
    /* pageSize    */ 8192,
    /* maxOrder    */ 9,              // 4 MiB chunks — smaller than the 16 MiB
                                      // default, so idle memory returns sooner
    ...);
```

**Ownership rules.** Exactly one owner per buffer, always.

1. `VarIntFrameDecoder` produces a `retainedSlice` of the accumulator. No copy.
2. A passthrough packet is written straight to the peer channel. The write
   promise releases it. It is never touched again.
3. A packet we *inspect* is decoded into a `Packet` object, and the source
   buffer is released immediately. Packet objects are plain records; the ones on
   the true hot path (`KeepAlive`) are singletons with a mutable long.
4. `MinecraftEncoder` writes into the channel's own allocator, never a fresh one.

**Leak posture.** Leak detection is `PARANOID` in tests, `SIMPLE` in staging,
`disabled` in production. A leak in staging fails the deploy.

**Sizing.** Steady-state per-player footprint is two 8 KiB pooled regions (one
per direction) plus ~400 bytes of Java object state. 5,000 players ≈ 80 MiB of
direct memory plus a small heap. `MaxDirectMemorySize` is set explicitly so an
allocation storm fails loudly instead of driving the container OOM-killer.

---

## 5. The hot path

Once both sides are in PLAY, the goal is that a packet crosses the proxy having
been *looked at* but never *understood*.

```
backend ──► [frame] ──► peek packet id ──► id ∈ INSPECT set?
                                            │
                                   no ──────┴──► retainedSlice → client.write()
                                            │       (zero copy, zero alloc)
                                   yes ─────┴──► decode → handler → maybe rewrite
```

`INSPECT` is a per-state, per-direction `long[]` bitset built once at startup
from the packet registry. Membership is a shift and a mask.

**What we actually inspect** — and nothing else:

| Direction | Packet | Why |
| --- | --- | --- |
| ↔ | `KeepAlive` | We answer them ourselves; the backend's are absorbed. |
| ↔ | `PluginMessage` | BungeeCord channel, our `hypergravel:*` channels. |
| ← backend | `Disconnect` | Turn a kick into a fallback instead of a dropped player. |
| ← backend | `JoinGame` / `Respawn` | Dimension state needed for a clean switch. |
| ← backend | `StartConfiguration` | Backend wants to reconfigure — we mirror it. |
| ← backend | `Transfer` | Backend-initiated move. |
| → client | `ChatCommand` / `ChatMessage` | Command interception, Discord relay. |

Everything else — movement, block changes, chunk data, entity updates, the
overwhelming bulk of the byte volume — is forwarded as a slice.

**Compression interaction.** When the client and the backend agree on the same
compression threshold (we force this at connect time), compressed frames are
forwarded *still compressed*. We do not inflate to inspect a packet id, because
the id lives in the first varint of the decompressed body — so for compressed
frames we inflate only the first few bytes with a bounded partial inflate, and
pass the original compressed frame through if the id is uninteresting.

**Backpressure.** Both channels use a `WriteBufferWaterMark` (32 KiB low /
64 KiB high). When a peer's `isWritable()` goes false, we clear the source's
`AUTO_READ`. When it recovers, we restore it. This turns a slow client into a
slow read from its backend rather than an unbounded queue in our heap. There is
no unbounded queue anywhere in HyperGravel.

---

## 6. Backend forwarding

Selectable per-server; the default matches what Paper expects.

| Mode | Wire format |
| --- | --- |
| `velocity-modern` | Login-plugin-request on `velocity:player_info`; response is HMAC-SHA256(secret) ‖ varint version ‖ address ‖ uuid ‖ name ‖ properties. Constant-time compare. **Default.** |
| `bungeeguard` | Legacy handshake with `\0`-joined host, uuid, properties + a guard token property. |
| `legacy` | BungeeCord `\0` handshake, no token. |
| `none` | Backend is offline-mode and trusts the network path. |

Floodgate data is prepended to the handshake address field when the player is
Bedrock, before any of the above is applied — that ordering is what Floodgate on
the backend expects.

Modern forwarding is what keeps us drop-in: a Paper server already configured
for Velocity needs no change beyond pointing at us.

---

## 7. Seamless backend restart

The top-priority behaviour. A backend going down must never produce a kick.

Each `RegisteredServer` carries a health state:

```
UP ──(3 failed pings | connection refused | clean shutdown notice)──► DRAINING
DRAINING ──(players moved out)──► DOWN
DOWN ──(2 successful pings + a successful test connect)──► UP
```

When a server leaves `UP`:

1. Its players are moved to `holding` — the configured limbo (`updating` :25601)
   or the lobby, per the server's `on-down` policy.
2. Each moved player records `returnTo = <server>` with a TTL.
3. While holding, the player is a fully live connection. They see a
   configurable actionbar/title. Nothing about their session is torn down.
4. When the server returns to `UP`, every player with a live `returnTo` for it
   is pulled back, rate-limited by `restore-rate` (default 20/s) so the freshly
   started backend is not thundering-herded.

The move itself uses the same machinery as `/server`: a new backend connection
is dialled, the login/config handshake completes against it, and only then is
the old connection closed and the pipeline re-pointed. If the dial fails, the
player stays where they are. **A failed switch is always a no-op, never a kick.**

On the client side the switch is a CONFIGURATION round-trip plus a respawn —
the standard modern-protocol server switch, no re-login.

`Transfer` (1.20.5+) is used instead where both the client and the destination
support it, since it is cheaper than a full re-handshake.

---

## 8. Status ping

Fully precomputed. A ping never allocates a Component, never renders
MiniMessage, and never touches a backend.

`StatusCache` holds a ready-to-write JSON byte array, rebuilt on:
- config reload,
- player count change (debounced, 1 Hz),
- favicon file change.

Gradient/animated MOTDs are rendered ahead of time into a small ring of
pre-serialized frames and selected by a clock tick. The favicon is read from
`server-icon.png`, validated as 64×64 PNG, and base64'd once at startup.

Cost of a ping at steady state: one varint length prefix and a
`Unpooled.wrappedBuffer` of a cached array.

---

## 9. Extension API (`hypergravel-api`)

Annotation-driven, Velocity-shaped so our existing logic ports with minimal
edits.

```java
@Extension(id = "hypergravel", name = "HyperGravel Integrations", version = "1.0.0")
public final class HyperGravelExtension {
    @Inject ProxyServer proxy;

    @Subscribe
    void onPreConnect(PreServerConnectEvent event) {
        if (event.target().name().equals("event") && !eventOpen) {
            event.setResult(PreServerConnectEvent.Result.denied(
                MiniMessage.miniMessage().deserialize("<red>The event is closed.")));
        }
    }
}
```

**Events** — `ProxyInitializeEvent`, `ProxyShutdownEvent`, `LoginEvent`,
`PreServerConnectEvent` (cancellable + redirectable), `ServerConnectedEvent`,
`DisconnectEvent`, `CommandExecuteEvent` (cancellable), `ProxyPingEvent`,
`PluginMessageEvent`, `ServerHealthChangeEvent`.

**Dispatch.** Handlers that return `void` run inline on the calling EventLoop —
that is the fast path and most handlers use it. A handler that needs to block
returns an `EventTask`, which suspends the event chain, runs on a virtual
thread, and resumes on the original EventLoop. This is how a permission lookup
or a webhook call participates in a cancellable event without stalling I/O.

**Also provided:** command registration with Brigadier-style completion,
`Scheduler` (delayed + repeating, virtual-thread backed), typed TOML config
binding, and an optional `PermissionProvider` that LuckPerms can back.

### Example integrations

| Ours | Hooks used |
| --- | --- |
| Network-wide `/server` routing rules | `CommandExecuteEvent` + permission bypass |
| Server entry denied when target is restricted | `PreServerConnectEvent` deny |
| Tab world cards / player counts | `ServerRegistry` counts + BungeeCord `PlayerCount` |

---

## 10. Security

- **Connection throttle** — per-IP token bucket at accept time, before any
  allocation. A throttled connection is closed without a response.
- **Concurrent caps** — per-IP and global, enforced in the boss group.
- **Login throttle** — separate, tighter bucket keyed on the post-PROXY-protocol
  real IP.
- **Protocol validation** — every varint is length-bounded, every string is
  length-checked against its protocol maximum, and a frame exceeding
  `max-packet-size` closes the connection.
- **Decompression bombs** — the inflater is bounded to the declared
  uncompressed size, which is itself capped. A frame that inflates past its
  declaration is fatal.
- **PROXY protocol trust list** — CIDR allowlist, default deny. Without it,
  PROXY protocol support is an IP-spoofing primitive: anyone could claim any
  source address and defeat every per-IP limit and every IP ban.

  Handling is decided per connection, on its first bytes:

  | Source | Header present | `required` | Outcome |
  | --- | --- | --- | --- |
  | trusted | yes | either | header honoured, real IP recovered |
  | trusted | no | `false` | **passes through on its socket address** |
  | trusted | no | `true` | closed |
  | untrusted | yes | either | header never parsed; connection drops |
  | untrusted | no | `false` | proceeds on its socket address |
  | untrusted | no | `true` | closed |

  The trusted/no-header/`required=false` row is the one that matters in
  practice: it is what lets you turn PROXY protocol on while forwarders are
  still being migrated. Detection uses `HAProxyMessageDecoder.detectProtocol`
  and the detector removes itself either way, so there is no per-packet cost
  once the decision is made.

  Every per-IP limit, log line, and forwarded address reads through
  `MinecraftConnection.remoteAddress()`, which prefers the recovered address —
  one place that has to be right about whose IP this is.
- **Anti-bot (Phase 3)** — Sonar-style. Before any backend is dialled, a
  suspected bot is held in an in-proxy verification limbo and must demonstrate
  correct gravity-affected position packets and keepalive round-trips.
- **Maintenance mode** — allowlist by UUID/permission, with a distinct MOTD.

---

## 11. Ops

- **Config** — TOML (night-config). Hot reload for routing, MOTD, limits, and
  extension config. Reload is atomic: a new immutable config object is built and
  swapped by reference. Listen addresses and forwarding secrets require a
  restart, and the reload says so instead of silently ignoring the change.
- **Metrics** — Prometheus text exposition on a separate Netty HTTP listener:
  player count by server, connections/s, login outcomes, per-backend ping
  latency histogram, buffer-pool usage from Netty's `PooledByteBufAllocatorMetric`,
  GC and direct-memory gauges.
- **Health** — `/health` (process liveness) and `/ready` (at least one backend UP).
- **Logs** — Log4j2 async loggers, structured JSON layout available for shipping.
- **Shutdown** — SIGTERM starts a drain: stop accepting, move everyone to limbo,
  wait for the configured grace period, then close. `systemd` unit ships in
  `ops/`.

---

## 12. Phasing

**Phase 1 (MVP)** — accept 1.21.x, online-mode auth, route to a backend, cached
ping + favicon, `/server`, PROXY protocol, modern forwarding.

**Phase 2** — backend transfers, plugin-message channels, extension API and the
six ported integrations.

**Phase 3** — Via layer (done), Floodgate/Bedrock, anti-bot, seamless restart, metrics.

Benchmarks close each phase: concurrent-connection ramp and a latency histogram
against a real Paper backend, reported at p50/p99/p99.9.
