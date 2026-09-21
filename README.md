<p align="center">
  <img src="assets/banner.png" alt="HyperGravel banner">
</p>

<p align="center">
  <a href="https://github.com/KraftifyFOSS/Hypergravel-Edge/stargazers">
    <img src="https://img.shields.io/github/stars/KraftifyFOSS/Hypergravel-Edge?style=for-the-badge" alt="GitHub stars">
  </a>
  <a href="https://github.com/KraftifyFOSS/Hypergravel-Edge/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/KraftifyFOSS/Hypergravel-Edge?style=for-the-badge" alt="License">
  </a>
  <a href="https://github.com/KraftifyFOSS/Hypergravel-Edge/commits/main">
    <img src="https://img.shields.io/github/last-commit/KraftifyFOSS/Hypergravel-Edge?style=for-the-badge&label=last%20commit" alt="Last commit">
  </a>
  <a href="https://github.com/KraftifyFOSS/Hypergravel-Edge">
    <img src="https://img.shields.io/github/repo-size/KraftifyFOSS/Hypergravel-Edge?style=for-the-badge" alt="Repository size">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-Java%20Edition-62B47A?style=flat-square" alt="Minecraft Java Edition">
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21+">
  <img src="https://img.shields.io/badge/Gradle-Build-02303A?style=flat-square&logo=gradle&logoColor=white" alt="Gradle">
</p>

A Minecraft: Java Edition reverse proxy. Fronts every Paper backend behind one
address, authenticates players once, and moves them between backends without a
re-login.

Wire-compatible with Velocity, so backends need no changes.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design and the
reasoning behind it.

## What's built in

* **Proxy core** - handshake/status/login state machines, online-mode auth,
  zero-copy PLAY passthrough, multi-server routing with health-based fallback.
* **Via** - ViaVersion + ViaBackwards run on the proxy, so backends keep
  speaking one protocol version and old clients are translated at the edge.
* **Voice chat** - a self-contained voice relay (in `pdx.dev.hypergravel.voice`)
  with server-owned invite packets, so nobody needs a third-party voice plugin.
* **Resource pack** - a pack builder (`pack/`) that renders the menu panels,
  tab icons, glyphs and backgrounds at runtime, plus a pack editor endpoint.
* **Tab** - a server-driven tab list (`tab/`) with custom head icons, live
  player sections and world cards.
* **Network chat** - cross-server chat relay over the plugin channel.
* **Login queue** - a queue service that parks players while the destination
  backend is at capacity.
* **Extensions** - a plugin API (`hypergravel-api`) plus a loader that scans
  `extensions/` for jars, isolates each in its own classloader, sorts by
  declared dependencies, and drives `onEnable`/`onDisable`. The built-in pack,
  tab, chat and queue services sit on the same API.

## Status

| Component                                            | Status |
| ---------------------------------------------------- | ------ |
| Handshake / status / login state machines            | Done   |
| Online-mode auth, encryption, compression            | Done   |
| PROXY protocol v1/v2 with CIDR trust list            | Done   |
| Velocity modern forwarding (+ BungeeGuard, legacy)   | Done   |
| Backend routing, try-order, `/server`                | Done   |
| Zero-copy PLAY passthrough                           | Done   |
| Health monitor + fallback on backend loss            | Done   |
| Multi-version: 1.9 → backend version, proxy-side Via | Done   |
| Voice relay, pack builder/editor, tab, chat, queue    | Done   |
| Extensions (`extensions/` scanning, deps, lifecycle)  | Done   |

**Not yet:** system chat to players (`HyperGravelProxy.Messenger` logs and drops
until the PLAY-state ids are verified), and Bedrock/Floodgate support.

### The one thing to verify before production

`hypergravel-proxy/src/main/resources/protocol/mappings.json` holds the packet
ids. Handshake/status/login/configuration ids are stable and reliable. **The PLAY
ids were written from memory and must be checked against a generated protocol
dump.**

A wrong id there does not corrupt the stream - `MinecraftDecoder` falls back to
raw forwarding on any decode failure - but it does silently disable that
interception. Verify rather than assume.

## Build

Needs a JDK 21 or newer.

```sh
./gradlew build
```

Produces:

```text
hypergravel-proxy/build/libs/hypergravel-proxy-<version>-all.jar
```

## Run

```sh
cp -r config /opt/hypergravel/config
$EDITOR /opt/hypergravel/config/hypergravel.toml
java -jar hypergravel.jar /opt/hypergravel/config
```

The argument is the config *directory*; it must contain `hypergravel.toml`.

A `systemd` unit is available at
[`ops/hypergravel.service`](ops/hypergravel.service).

### Minimum config to change

* `forwarding.secret` - must match `forwarding.secret` on every Paper backend.
  Generate with:

```sh
head -c 32 /dev/urandom | base64
```

* `servers.*` - the backends and their ports.
* `proxy-protocol.trusted` - the CIDRs your port forwarders come from.

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

Set `online-mode=false` in `server.properties`, with the backend firewalled so it
is only reachable through the proxy.

## BungeeCord plugin channel

Backends drive the proxy over `bungeecord:main` (and the legacy `BungeeCord`
alias) using the standard `DataInputStream` framing, so Bukkit-side code written
against BungeeCord or Velocity works unchanged.

Register the outgoing channel in your plugin's `onEnable`, then:

```java
ByteArrayDataOutput out = ByteStreams.newDataOutput();
out.writeUTF("Connect");
out.writeUTF("hub");
player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
```

### Supported subchannels

| Subchannel                                   | Description                                                   |
| -------------------------------------------- | ------------------------------------------------------------- |
| `Connect`, `ConnectOther`                    | Move a player to a server                                     |
| `Forward`, `ForwardToPlayer`                 | Relay a payload to `ALL`, `ONLINE`, one server, or one player |
| `GetServer`, `GetServers`, `GetPlayerServer` | Topology queries                                              |
| `PlayerCount`, `PlayerList`                  | Population, per server or `ALL`                               |
| `IP`, `IPOther`, `ServerIP`                  | Addresses                                                     |
| `UUID`, `UUIDOther`                          | UUID queries                                                  |
| `Message`, `MessageRaw`                      | Chat to one player or `ALL`                                   |
| `KickPlayer`                                 | Disconnect with a legacy-formatted reason                     |

Replies come back down the requesting player's own backend connection, on the
channel the request arrived on.

**Delivery caveat.** A plugin message can only ride a player's connection, so a
`Forward` to a server with nobody on it goes nowhere - the same limitation
BungeeCord has.

To hand data to a server a player is *about to* join, send `Connect` and then
`ForwardToPlayer`: that rides the player's own connection and therefore lands
wherever they now are.

Messages on this channel are accepted **only from backends**. A client that sends
one is dropped, so a player cannot move themselves or anyone else.

## Extensions (server-side plugins)

HyperGravel loads your proxy plugins from the `extensions/` directory at
startup. A plugin is a jar with one `@Extension`-annotated class extending
`HyperGravelExtension`; the proxy gives it the full `ProxyServer` API -
commands, events, scheduler, per-extension config, plugin channels - and sorts
enabled extensions by their declared dependencies. A broken one is skipped,
never fatal.

```java
@Extension(id = "sample-hello", name = "Sample Greeter", version = "1.0.0")
public final class HelloExtension extends HyperGravelExtension {

    @Override public void onEnable() {
        logger().info("Hello enabled on {}", proxy().version());
        commandManager().register("hello", (source, args) ->
                source.sendMessage(Component.text("Hello, " + source.name())));
    }
}
```

See [`docs/EXTENSIONS.md`](docs/EXTENSIONS.md) for the full plugin-dev guide,
and [`examples/sample-extension`](examples/sample-extension) for a buildable
skeleton. The API is published with `./gradlew :hypergravel-api:publishToMavenLocal`
so plugin projects can depend on `pdx.dev.hypergravel:hypergravel-api`.

## Ops

```text
GET /health
GET /ready
GET /metrics
```

* `/health` - always returns `200` while the process is alive.
* `/ready` - returns `200` if at least one backend is UP, otherwise `503`.
* `/metrics` - Prometheus text exposition.

Bound to `127.0.0.1:9100` by default.

The endpoint has no authentication and exposes player counts and backend
topology. Keep it off public interfaces.

## Layout

```text
hypergravel-api/     extension API - events, commands, scheduler, config, permissions
hypergravel-proxy/   the proxy
  protocol/          versions, packets, the id registry
  network/           Netty pipeline, codecs, session handlers
  backend/           server registry, connector, health monitor
  auth/              encryption, Mojang session verification
  forwarding/        Velocity modern, BungeeGuard, legacy
  extension/         the plugin loader - jar scan, classloaders, dependency order
  pdx.dev.hypergravel/
    via/             protocol translation
    voice/           voice relay
    pack/            resource pack system
    tab/             tab list
    chat/            network chat
examples/            sample-extension - a buildable plugin skeleton
config/              example configuration
docs/                architecture + extension API docs
ops/                 systemd unit
```

## Tests

```sh
./gradlew test
```

Tests cover:

* Packet ID registry across states, directions, and versions.
* Duplicate packet ID detection.
* Codec pipeline using `EmbeddedChannel`.
* Packet framing and encoding.
* Coalesced-read state transitions.
* Raw packet passthrough.

## License

MIT License.

See [`LICENSE`](LICENSE) for the full license text.
