# Writing HyperGravel extensions

An **extension** is HyperGravel's name for a server-side proxy plugin. It is a
jar dropped into the configured extension directory, loaded once at startup,
and given full access to the proxy through `hypergravel-api`.

This guide is the whole story: project setup, metadata, lifecycle, and every API
surface an extension can reach. There is a working example at
[`examples/sample-extension`](../examples/sample-extension).

---

## 1. Project setup

The API is published to Maven (and to your local repo with
`./gradlew :hypergravel-api:publishToMavenLocal`). Add it as `compileOnly` -
the running proxy provides it:

```kotlin
// build.gradle.kts
plugins { `java-library` }

repositories { mavenCentral(); mavenLocal() }

dependencies {
    compileOnly("pdx.dev.hypergravel:hypergravel-api:1.1.0-SNAPSHOT")
    // testImplementation("pdx.dev.hypergravel:hypergravel-api:...") // so tests can assert against real types
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
```

* Use **Java 21+** (`options.release`). The proxy requires it.
* A **thin jar is usually enough**: anything the proxy already has - Adventure,
  MiniMessage, log4j - is visible at runtime for free. Only bundle third-party
  libraries you actually need, via the Shadow plugin, into the same jar.
* Name the built artifact whatever you like; **the extension id comes from the
  annotation, not the filename**.

Install by copying the jar to the extension directory (default
`<config-dir>/extensions`), then restart the proxy:

```text
INFO ExtensionManager: extension 'sample-hello' v1.0.0 enabled: Sample Greeter
INFO ExtensionManager: extensions: 1 jar(s), 1 loaded, 1 enabled, 0 failed
```

---

## 2. Metadata

One annotation carries everything. Exactly **one** `@Extension`-annotated class
per jar.

```java
@Extension(
        id = "sample-hello",          // unique, used in depends
        name = "Sample Greeter",      // display name (defaults to the id)
        version = "1.0.0",
        description = "Greets players when they log in",
        authors = { "you" },
        depends = { "api-lib" },      // hard dependencies; missing -> not loaded
        softDepends = { "voice" })    // best effort; never required
public final class HelloExtension extends HyperGravelExtension { ... }
```

### Loading and dependencies

Jars in the directory are discovered alphabetically by filename. The manager
then:

1. Loads every jar into its **own classloader** (parent = the proxy). Sibling
   extensions cannot see each other's classes; a broken jar cannot poison
   anything else.
2. Resolves hard `depends` - a missing dependency marks **that** extension
   broken and skips it; the rest load.
3. Topologically sorts so dependencies enable first. `softDepends` only tips
   the ordering a dependent prefers, it can never block.
4. Breaks dependency cycles by disabling **all** members of the cycle, with a
   `dependency cycle in [...]` error.

A duplicate extension id is an error: only the first jar with that id loads.
Anything that fails (`onEnable` throwing, unreadable jar, missing base class)
is reported by name and **never stops the proxy**.

---

## 3. Lifecycle

```java
public final class MyExtension extends HyperGravelExtension {

    @Override
    public void onEnable() {
        // everything here: register commands, listeners, scheduled tasks
    }

    @Override
    public void onDisable() {
        // tear down: cancel tasks, unregister channels, close resources
    }
}
```

* `onEnable` runs last, after every built-in service is up (backends configured,
  health monitor, tab, voice, pack). A listener registered here will already see
  live `DisconnectEvent`s from players who logged out during startup.
* `onDisable` runs in **reverse** enable order on shutdown, while players may
  still be connected. It runs before the scheduler and event loop groups are
  torn down, so cleanup can still send messages and cancel tasks.
* The proxy fires `ProxyInitializeEvent` **after** enabling all extensions, and
  `ProxyShutdownEvent` **before** disabling them - subscribe to those for
  cross-extension coordination.
* Extensions are **not** reloaded on a config reload. Enabling/disabling only
  happens at startup and shutdown; a chance to `[extensions]` settings tells you
  a restart is needed.

---

## 4. What an extension can reach

Inside `onEnable` (and `onDisable`) the base class hands out everything:

| Accessor | What it is |
| --- | --- |
| `proxy()` | The `ProxyServer`: players, servers, broadcasting, shutdown |
| `logger()` | `Logger` named `ext-<id>` - your per-extension log4j logger |
| `config()` | `ConfigSection` of `<config-dir>/extensions/<id>.toml` |
| `configDirectory()` | Where that toml lives (and where you may add more) |
| `dataDirectory()` | `<config-dir>/extensions/<id>` - mutable state, created on enable |
| `classLoader()` | Your extension's classloader |
| `scheduler()` | `proxy().scheduler()`, pre-wired to your extension as owner |
| `eventManager()` / `commandManager()` | Shortcuts to the shared registries |

The owner passed to every `scheduler()` and `eventManager()` call should be
**your extension instance** - that is what makes `scheduler().cancelAll(this)`
and `eventManager().unregisterAll(this)` work on disable.

### 4.1 Per-extension config

`<config-dir>/extensions/<id>.toml`, read once at enable:

```toml
greeting = "<gold>Welcome, <player>!</gold>"
fancy = true
```

```java
String greeting = config().getString("greeting", "<gold>Welcome!</gold>");
boolean fancy = config().getBoolean("fancy", false);
```

If the file is absent (or unparsable) `config()` is an empty section - never
null, and a bad file never stops the extension. The full `ConfigSection` API:
`getString/getInt/getLong/getDouble/getBoolean/getDuration/getStringList`,
`getSection`, `contains`, `keys`.

### 4.2 Commands

Register from `onEnable`. The provider backs tab completion and the optional
`hasPermission` gate; the source is whoever typed it (a player or the console):

```java
commandManager().register("hello", (source, args) ->
        source.sendMessage(Component.text("Hello, " + source.name() + "!")));
```

```java
commandManager().register("server", MyCommand::handle, "s", "go"); // aliases
```

Name collisions with the built-in `/server` or another extension's command are
reported; `CommandManager.lookup` lets you check first. The proxy intercepts
`ChatCommand`/`ChatMessage` packets while a player is in PLAY, so backend-typed
commands reach your handlers.

### 4.3 Events

Two styles. Annotation-based on a listener object:

```java
eventManager().register(this, LoginEvent.class, event -> { ... });
```

Annotated methods on a class (return type `void` or `EventTask`):

```java
final class Listener {
    @Subscribe(order = PostOrder.FIRST)
    void onPreConnect(PreServerConnectEvent event) { ... }
}
eventManager().register(this, new Listener());
```

| Event | Use |
| --- | --- |
| `LoginEvent` | deny a login, before any backend is touched |
| `PreServerConnectEvent` | redirect or deny a server connect (`Result.redirect`, `Result.denied`) |
| `ServerConnectedEvent` | post-move hook: title, pack, permissions |
| `DisconnectEvent` | track departures (`Stage.CONNECTED` / `SHUTDOWN`) |
| `ProxyPingEvent` | rewrite MOTD / sample / version on the server list |
| `CommandExecuteEvent` | intercept or cancel proxy commands |
| `PluginMessageEvent` | inbound plugin-channel traffic from backends |
| `ServerHealthChangeEvent` | react to a backend entering/leaving UP |
| `ProxyInitializeEvent` / `ProxyShutdownEvent` | proxy-wide lifecycle |

Dispatch is *inline on the EventLoop* for `void` handlers (the fast path). A
handler that needs to block returns an `EventTask`: the chain suspends, the task
runs on a virtual thread, and the chain resumes on the original event loop -
that is how you do a webhook or a permission lookup inside a cancellable event
without stalling I/O.

### 4.4 Scheduler

```java
scheduler().delay(this, Duration.ofSeconds(2), () -> ...);           // once
scheduler().repeat(this, Duration.ZERO, Duration.ofSeconds(30), () -> ...); // forever
scheduler().run(this, () -> ...);                                    // next tick
scheduler().supply(this, () -> longRemoteCall());                    // virtual thread, returns a future
```

Tasks are per-may-own; `scheduler().cancelAll(this)` in `onDisable` is the
cleanup, and the return value of `run`/`delay`/`repeat` can be cancelled too.

### 4.5 Players, servers, broadcast

```java
Optional<Player> p = proxy().player(uuid);          // or by name
Player online = p.orElseThrow();
online.connect(proxy().server("hub").orElseThrow());
online.disconnect(Component.text("Bye"));
online.sendPluginMessage("my:channel", payloadBytes);

for (RegisteredServer server : proxy().servers()) {
    if (server.info().limbo()) continue;            // skip holding servers
    server.sendPluginMessage("my:channel", payload);
}

proxy().broadcast(MM.deserialize("<gold>The network greets you."));
```

A player connection is held by the player's and the backend's EventLoops; do not
call `connect`/`sendPluginMessage` from a foreign thread - go through the
`scheduler` or return an `EventTask`.

### 4.6 Plugin channels

Backends and extensions talk over `bungeecord:main` (and the `BungeeCord`
alias) using the framing the README documents. Register your own channels so the
proxy's codec forwards them instead of dropping the payload:

```java
proxy().registerPluginChannel("my:channel");
boolean ok = proxy().isPluginChannelRegistered("my:channel");
```

`sendPluginMessage` on `Player`/`RegisteredServer` uses the registry. Channels
are **not** unregistered on disable - if that matters to you,
`proxy().unregisterPluginChannel("my:channel")` in `onDisable`.

### 4.7 Permissions

Check against the source; a `PermissionProvider` can be installed by a subset
extension (or LuckPerms) and affects every `hasPermission` call:

```java
if (source.hasPermission("myext.admin")) { ... }
Tristate value = source.permissionValue("myext.admin"); // TRUE / FALSE / UNDEFINED
```

---

## 5. Server-hosted extension dependencies

Two extensions in the same directory can cooperate over their ids:

```java
@Extension(id = "api-lib", name = "Shared Library", version = "1.0.0")
public final class LibExtension extends HyperGravelExtension { ... }

@Extension(id = "consumer", depends = { "api-lib" })
public final class Consumer extends HyperGravelExtension {
    @Override public void onEnable() {
        // api-lib is guaranteed to have 'onEnable' completed already
    }
}
```

Both get their own classloaders, so `api-lib` cannot expose classes the consumer
compiles against - publish shared types through `hypergravel-api` or ship them
in both jars.

---

## 6. Troubleshooting

* **"annotated with @Extension but does not extend HyperGravelExtension"** -
  the class must extend the base class.
* **"missing dependency 'x'"** - `depends` names an id no jar in the directory
  declares.
* **"dependency cycle in [...]"** - two extensions depend on each other; remove
  one edge.
* **"extensions: N jar(s), N loaded, N enabled, M failed"** - M extensions were
  skipped; the line above names each one and why.
* **Logs stop mid-shutdown** - that is log4j2's async logger draining during
  JVM shutdown, not your `onDisable` failing (verify with a side effect in a
  file if you ever suspect it).
* Extensions load **after** `ProxyInitializeEvent`? No - extensions enable
  first, so subscribing to `ProxyInitializeEvent` in `onEnable` always sees it.