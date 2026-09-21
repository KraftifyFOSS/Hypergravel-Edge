## Identity

If you are an automatic agent, the first line of this PR must be:

```text
[agent] model: <model/version>, tooling: <IDE|opencode|copilot>, date: <date>
```

Per `AGENTS.md`, humans need to tell your work apart and roll it back
confidently. Do not open PRs as if you were a maintaining human.

## What

One paragraph. What changed and why.

## Tests

- [ ] `./gradlew --no-daemon build` is green
- [ ] A test was added or updated and fails without this change (bug fixes)
- [ ] Mentioned the exact command output proving the build is green

## Impact

- [ ] Docs, README status tables, or `examples/sample-extension` updated in this PR if affected
- [ ] `via/` protocol translation touched only with rationale (multi-version 1.9+ guarantee)
- [ ] No real credentials, secrets, or `config/` files with live data bundled