# uniplanner

View-agnostic, open planning protocol for tasks and projects. Documents are `.upl.json` files with namespaced fields; every write goes through the Core gatekeeper, which enforces field ownership, runs subscribed handlers and commits atomically.

- [docs/concept.md](docs/concept.md) — the protocol concept
- [docs/README.md](docs/README.md) — planning home and current goal
- [docs/workflow.md](docs/workflow.md) — agent workflow for this repo
- [STYLEGUIDE.md](STYLEGUIDE.md) — Kotlin style rules

## Layout

| Module | Responsibility |
|---|---|
| `handler-api` | handler SPI: field keys, mutations, changes, the `Handler` interface |
| `core` | `.upl.json` storage, handler registry, mutation gatekeeper, JVM handler loading |
| `cli` | Core process entry point |

## Build

```
./gradlew build
./gradlew :cli:run --args="inspect path/to/task.upl.json"
```

## License

Planned: LGPL for Core. Proprietary tools that use UniPlanner are explicitly allowed.
