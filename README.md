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
| `core` | `.upl.json` storage (single documents and containers), handler registry, mutation gatekeeper, JVM handler loading |
| `handlers` | built-in kanban, dayplanner and gantt handlers |
| `cli` | Core process entry point |
| `js-api` | stateless browser API: `applyMutation`/`previewMutation` over JSON strings |
| `web-demo` | browser spike for the mutation gatekeeper |
| `server-auth` | static server-side token auth, principals, sessions, and HTTP cookie helpers |
| `server` | Ktor auth/session and planning API host for the hosted planner |

## Build

```
./gradlew build
./gradlew :cli:run --args="inspect path/to/task.upl.json"
UPL_TOKEN=change-me UPL_VAULT=/path/to/vault UPL_COOKIE_SECURE=false UPL_STATIC=/path/to/dist ./gradlew :server:run
```

## License

Planned: LGPL for Core. Proprietary tools that use UniPlanner are explicitly allowed.
