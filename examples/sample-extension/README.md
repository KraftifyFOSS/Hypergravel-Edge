# sample-hello

A bare-bones HyperGravel Edge server-side plugin ("extension"). Shows commands,
events, the scheduler, per-extension config, and `dataDirectory()` in ~50 lines.

## Build

The API must be in your Maven local repository first:

```sh
# from the HypergravelEdge repo root
./gradlew :hypergravel-api:publishToMavenLocal
```

Then:

```sh
./gradlew jar
cp build/libs/sample-extension-1.0.0.jar /path/to/proxy-config/extensions/
```

Restart the proxy. You should see:

```text
extension 'sample-hello' v1.0.0 enabled: Sample Greeter
```

Type `/hello` in-game (backend chat is forwarded so the proxy sees it) to get a
greeting, and watch the `[exti]` log line with the one-minute heartbeat.

## Per-extension config

Create `extensions/sample-hello.toml` next to the jar:

```toml
greeting = "<gradient:#a78bfa:#fb923c>Welcome, <player>!</gradient>"
```

It is read live at `onEnable` via `config()` - no reload machinery needed.

## Conventions used here

* `compileOnly("pdx.dev.hypergravel:hypergravel-api:...")` - the proxy provides
  the API (and Adventure, log4j) at runtime, so the extension jar stays thin.
* Only **one** `@Extension`-annotated class per jar.
* `onEnable()` registers everything; `onDisable()` tears it down.