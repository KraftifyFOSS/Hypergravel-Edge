# AGENTS.md

Instructions for AI agents (Claude, ChatGPT, Copilot, Codex, opencode, and any
other coding assistant) that work on this repository, automatically or on
request.

## 1. Identify yourself in every report, fix, PR, or commit

If you are an agent, say so, and be specific. Lead every bug report, change
summary, PR description, or commit message with a line like:

```text
[agent] model: anthropic/claude-4.7, tooling: opencode 0.2.x, date: 2026-09-21
```

Include at minimum: model name + version, tooling (IDE/CLI/opencode/copilot),
and the date. This lets humans tell automated work apart from their own, and
roll back confidently. Do not silently commit or report as if you were a human
maintainer.

## 2. What this project is

HyperGravel Edge is a Minecraft: Java Edition **reverse proxy** (a fork of
HyperGravel / Bungee-style lineage). It fronts Paper backends with:

-**Chat**: cross-server chat relay over the plugin channel.
- **Queue**: parks players while a destination backend is at capacity.

The project version is `1.1.0-SNAPSHOT`. It targets **Java 21 bytecode**
(`options.release.set(21)`, so compile/runtime is fine on JDK 21+).

## 3. Repo layout

```text
hypergravel-api/     Extension API - events, commands, scheduler, config,
                     permissions. This is what plugins compile against.
hypergravel-proxy/   The proxy.
  protocol/          Versions, packets, the ID registry.
  network/           Netty pipeline, codecs, session handlers.
  backend/           Server registry, connector, health monitor.
  auth/              Encryption, Mojang session verification.
  forwarding/        Velocity modern, BungeeGuard, legacy.
  extension/         The plugin loader - jar scan, classloaders, dep order.
  via/ voice/ pack/ tab/ chat/
                     Built-in services (protocol translation, voice relay,
                     resource packs, tab list, network chat).
examples/            sample-extension - a buildable plugin skeleton.
config/              Example configuration.
docs/                Extension API guide + architecture.
.github/workflows/   CI (see section 7).
ops/                 systemd unit.
```

## 4. Build, test, and run

All commands run from the repo root. Uses the Gradle wrapper (9.5.1).

```sh
./gradlew --no-daemon build                       # compile + test everything
./gradlew --no-daemon build --refresh-dependencies
./gradlew --no-daemon :hypergravel-api:publishToMavenLocal
./gradlew --no-daemon -p examples/sample-extension jar   # sample plugin
./gradlew --no-daemon :hypergravel-proxy:test --rerun-tasks   # just proxy tests
```

The **runnable** thing is the fat jar, NOT the thin jar:

```text
hypergravel-proxy/build/libs/hypergravel-proxy-1.1.0-SNAPSHOT-all.jar
```

Run it:

```sh
java -jar hypergravel-proxy/build/libs/hypergravel-proxy-<version>-all.jar /path/to/config
```

Config dir needs `hypergravel.toml` and `log4j2.xml` (see `config/`). Main class:
`pdx.dev.hypergravel.proxy.HyperGravelBootstrap`.

## 5. Rules and gotchas

- **Version is one source of truth? Not quite.** `build.gradle.kts` sets
  `version = "1.1.0-SNAPSHOT"`; `HyperGravelProxy.VERSION` is the display
  string (`"1.1.0"`). Keep them in sync when bumping. Third-party
  viaversion/viabackwards `-SNAPSHOT` coordinates are pinned on purpose, do not
  float them.
- **Do not edit `via/`** protocol translation blindly. It carries the
  multi-version 1.9+ guarantee; run tests and the smoke test after touching it.
- **A failing extension must never take down the proxy.** The loader marks that
  extension BROKEN, logs its name, and keeps going. Preserve that invariant.
- **log4j2 uses the async logger** (`AsyncLoggerContextSelector`). During JVM
  shutdown, the last `onDisable` / "stopped" log lines are routinely dropped.
  This is a known, pre-existing quirk. Never "fix" it by making the logger
  synchronous for the whole app.
- **Plugin-channel messages are accepted only from backends.** A client sending
  one gets dropped. Do not loosen this.
- **Extension jars**: one `@Extension` class per jar; the class must extend
  `HyperGravelExtension`. Loader sorts by `depends`; `softDepends` only breaks
  ties; missing dep / cycle / duplicate id disables that extension only.
- **Tests** are JUnit 5 (`useJUnitPlatform()`). Test fixtures for the loader
  live in `hypergravel-proxy/src/test/java/pdx/dev/hypergravel/proxy/extension/`.
- **Java** compiles with `-Xlint:all` minus a few warnings, `-parameters`, and
  release 21. Write code that builds on **every** JDK in CI (21-25); do not use
  APIs newer than 21 unless you also bump the release target.
- **Markdown style**: no em dashes (`--`), use a plain hyphen for emphasis
  breaks. Keep docs in `docs/`; update `README.md` fixture tables when feature
  status changes (rows like "Multi-version ... | Done").
- **Never commit** secrets, API keys, or `config/` files containing real
  credentials. `config/` in this repo is an example only.
- Do not commit build output; `.gitignore` already covers `build/` and `.gradle/`
  at any depth.

## 6. Conventions

- Group: `pdx.dev.hypergravel`, version `1.1.0-SNAPSHOT` (root `version`, API
  artifact `hypergravel-api`).
- Package conventions: API types in `pdx.dev.hypergravel.api.*`, proxy
  internals in `pdx.dev.hypergravel.proxy.*` (subpackages per concern).
- No comments unless they explain a non-obvious invariant. Name code to read
  itself.
- Side effects are confined to the proxy module; `hypergravel-api` is a plain,
  dependency-light API surface. Do not pull Netty or proxy internals into the
  API.
- Events: handlers returning `void` run inline on the EventLoop (fast path);
  handlers returning `EventTask` run on a virtual thread and resume on the
  EventLoop. Prefer the `void` form unless you must block.
- Fit the existing style: the file you edit sets the conventions you follow.

## 7. CI / GitHub Actions

`.github/workflows/build.yml`:

- Runs on push to `main`, all PRs, and `workflow_dispatch`.
- Matrix: `java: [21, 22, 23, 24, 25]`, each entry builds the API + proxy, runs
  all tests, publishes the API to Maven Local, and compiles the sample
  extension (so API breakage fails loudly).
- `release` job triggers on `v*` tags, builds on JDK 25, and uploads the fat jar
  to a GitHub Release.
- A repo member must push code; automatic agents should open PRs instead of
  pushing to `main` unless they have write access and a human asked.

## 8. When a human hands you a bug or feature

1. Reproduce (`./gradlew --no-daemon build` + the run command above) before and
   after any change.
2. State model/tooling/date (section 1) in the report.
3. Smallest, least invasive fix that preserves the invariants in section 5.
4. Add or update a test that fails without the fix.
5. Run the full build; say the exact command output that proves it is green.
6. If docs, README status tables, or the extension example are affected, update
   them in the same PR.

## 9. Where to ask

- Project docs: `docs/EXTENSIONS.md` (plugin dev guide) and
  `docs/ARCHITECTURE.md` (deep architecture).
- Issues/PRs: this repository's GitHub page
  (https://github.com/KraftifyFOSS/Hypergravel-Edge).
- Live server/dev environment notes live in `ops/` if present.