# Changelog

All notable changes to HyperGravel Edge are documented here. Entries note
whether a change is breaking for the extension API, the plugin channel, or the
multi-version 1.9+ guarantee.

## [Unreleased]

### Added
- Server-side plugin system: `hypergravel-api` extension API (`HyperGravelExtension`,
  `ExtensionContext`, `ExtensionDescription`) plus a proxy loader that scans
  `extensions/`, isolates each jar in its own classloader, sorts by `depends`
  and `softDepends`, and drives `onEnable`/`onDisable`.
- `README.md` docs pointing to [`docs/EXTENSIONS.md`](docs/EXTENSIONS.md), the
  extension API guide.
- `CHANGELOG.md` and a buildable `examples/sample-extension` skeleton.
- Continuous integration (`.github/workflows/build.yml`): full build + tests +
  sample compile on JDK 21, 22, 23, 24, and 25; fat jar published to GitHub
  Releases on `v*` tags.
- Issue and PR templates that require automatic agents to identify
  themselves (model, tooling, date), plus Dependabot for Gradle and GitHub
  Actions updates.
- API published to GitHub Packages and `hypergravel-api` javadoc deployed to
  GitHub Pages on tagged releases.
- `Dockerfile` producing a container image pushed to GHCR on tagged releases.

### Changed
- Version bumped from `0.1.0-SNAPSHOT` to `1.1.0-SNAPSHOT`.
- Release artifacts strip the `-SNAPSHOT` suffix: a `v1.1.0` tag produces
  `hypergravel-proxy-1.1.0-all.jar`.
- Tab now behaves like normal Minecraft by default: the backend player list is
  forwarded untouched. The server-driven network list is opt-in via
  `[tab] network-list = true` (the legacy `enabled` key is still honored). When
  the network list is on, backend player-info packets are dropped so the two
  lists never collide.

### Fixed
- Dependency ordering in the extension loader: stable Kahn ordering is no
  longer reversed by a post-sort insertion pass.
- Duplicate extension ids: the first jar wins and the healthy copy is no
  longer misreported as a dependency cycle.