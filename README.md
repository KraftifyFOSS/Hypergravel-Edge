# HyperGravel

A Minecraft: Java Edition reverse proxy. Fronts every Paper backend behind one
address, authenticates players once, and moves them between backends without a
re-login.

Wire-compatible with Velocity, so backends need no changes.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design and the
reasoning behind it.

## What's built in

- **Proxy core** — handshake/status/login state machines, online-mode auth,
  zero-copy PLAY passthrough, multi-server routing with health-based fallback.
- **Via** — ViaVersion + ViaBackwards run on the proxy, so backends keep
  speaking one protocol version and old clients are translated at the edge.
- **Voice chat** — a self-contained voice relay (in `pdx.dev.hypergravel.voice`)
  with server-owned invite packets, so nobody needs a third-party voice plugin.
- **Resource pack** — a pack builder (`pack/`) that renders the menu panels,
  tab icons, glyphs and backgrounds at runtime, plus a pack editor endpoint.
- **Tab** — a server-driven tab list (`tab/`) with custom head icons, live
  player sections and world cards.
- **Network chat** — cross-server chat relay over the plugin channel.
- **Login queue** — a queue service that parks players while the destination
  backend is at capacity.

## Status

| | |
| --- | --- |
| Handshake / status / login state machines | done |
| Online-mode auth, encryption, compression | done |
| PROXY protocol v1/v2 with CIDR trust list | done |
| Velocity modern forwarding (+ BungeeGuard, legacy) | done |
| Backend routing, try-order, `/server` | done |
| Zero-copy PLAY passthrough | done |
| Health monitor + fallback on backend loss | done |
| Multi-version: 1.9 → backend version, proxy-side Via | done |
| Voice relay, pack builder/editor, tab, chat, queue | done |

**Not yet:** system chat to players (`HyperGravelProxy.Messenger` logs and drops
until the PLAY-state ids are verified), `extensions/` directory scanning, and
Bedrock/Floodgate support.

### The one thing to verify before production

`hypergravel-proxy/src/main/resources/protocol/mappings.json` holds the packet
ids. Handshake/status/login/configuration ids are stable and reliable. **The PLAY
ids were written from memory and must be checked against a generated protocol
dump.**

A wrong id there does not corrupt the stream — `MinecraftDecoder` falls back to
raw forwarding on any decode failure — but it does silently disable that
interception. Verify rather than assume.

## Build

Needs a JDK 21 or newer.

```sh
./gradlew build
```

Produces `hypergravel-proxy/build/libs/hypergravel-proxy-<version>-all.jar`.

## Run

```sh
cp -r config /opt/hypergravel/config
$EDITOR /opt/hypergravel/config/hypergravel.toml    # set forwarding.secret at minimum
java -jar hypergravel.jar /opt/hypergravel/config
```

The argument is the config *directory*; it must contain `hypergravel.toml`. A
`systemd` unit is in [`ops/hypergravel.service`](ops/hypergravel.service).

### Minimum config to change

- `forwarding.secret` — must match `forwarding.secret` on every Paper backend.
  Generate with `head -c 32 /dev/urandom | base64`.
- `servers.*` — the backends and their ports.
- `proxy-protocol.trusted` — the CIDRs your port forwarders come from.

Config errors are reported by name and stop startup rather than producing a
half-working proxy.

## Backend setup

Each Paper backend needs, in `config/paper-global.yml`:

```yaml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: <the same forwarding.secret>
```

and `online-mode=false` in `server.properties`, with the backend firewalled so it
is only reachable through the proxy.

## BungeeCord plugin channel

Backends drive the proxy over `bungeecord:main` (and the legacy `BungeeCord`
alias) using the standard `DataInputStream` framing, so Bukkit-side code written
against BungeeCord or Velocity works unchanged. Register the outgoing channel in
your plugin's `onEnable`, then:

```java
ByteArrayDataOutput out = ByteStreams.newDataOutput();
out.writeUTF("Connect");
out.writeUTF("hub");
player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
```

Supported subchannels:

| | |
| --- | --- |
| `Connect`, `ConnectOther` | move a player to a server |
| `Forward`, `ForwardToPlayer` | relay a payload to `ALL` / `ONLINE` / one server / one player |
| `GetServer`, `GetServers`, `GetPlayerServer` | topology queries |
| `PlayerCount`, `PlayerList` | population, per server or `ALL` |
| `IP`, `IPOther`, `ServerIP` | addresses |
| `UUID`, `UUIDOther` | undashed, per the BungeeCord wiki |
| `Message`, `MessageRaw` | chat to one player or `ALL` (legacy §-codes / JSON) |
| `KickPlayer` | disconnect with a legacy-formatted reason |

Replies come back down the requesting player's own backend connection, on the
channel the request arrived on.

**Delivery caveat.** A plugin message can only ride a player's connection, so a
`Forward` to a server with nobody on it goes nowhere — the same limitation
BungeeCord has. To hand data to a server a player is *about to* join, send
`Connect` and then `ForwardToPlayer`: that rides the player's own connection and
therefore lands wherever they now are.

Messages on this channel are accepted **only from backends**. A client that sends
one is dropped, so a player cannot move themselves or anyone else.

## Ops

```
GET /health    always 200 while the process is alive
GET /ready     200 if at least one backend is UP, else 503
GET /metrics   Prometheus text exposition
```

Bound to `127.0.0.1:9100` by default. It has no authentication and exposes
player counts and backend topology — keep it off public interfaces.

## Layout

```
hypergravel-api/     extension API — events, commands, scheduler, config, permissions
hypergravel-proxy/   the proxy
  protocol/     versions, packets, the id registry
  network/      Netty pipeline, codecs, session handlers
  backend/      server registry, connector, health monitor
  auth/         encryption, Mojang session verification
  forwarding/   Velocity modern, BungeeGuard, legacy
  pdx.dev.hypergravel/  via, voice, pack, tab, chat
config/         example configuration
docs/           architecture
ops/            systemd unit
```

## Tests

```sh
./gradlew test
```

Covers the packet id registry (every state/direction/version resolves, no
duplicate ids) and the codec pipeline over an `EmbeddedChannel` (framing,
encoding, coalesced-read state transitions, raw passthrough).

## License

GPL-3.0.