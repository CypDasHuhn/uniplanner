# uniplanner planning

UniPlanner is a view-agnostic, open planning protocol for tasks and projects. Tools read `.upl.json` documents directly; every write goes through the Core gatekeeper, which enforces field ownership, runs subscribed handlers and commits atomically.

- [concept.md](concept.md) — the protocol concept summary
- [workflow.md](workflow.md) — how work is planned, executed, reviewed and committed
- `tasks/` — tickets, one file per unit of work
- `reviews/` — review reports per ticket
- `manual-test.md` — acceptance criteria that cannot be automated

## Current state

Core v1 works: `.upl.json` read/write with `task`/`workspace`/`container` kinds, prefix-based ownership and subscription with group-key rejection, no-op suppression, `(handler, field)` cycle detection, and three built-in handlers:

- `kanban` — columns, ordering, board config in `kanban.columns`, close/reopen propagation
- `dayplanner` — week placement per day, cleared when the task closes
- `gantt` — `std.time.start`/`duration` canonical, `end` derived

Handler SPI is in-process (`ServiceLoader` for externals); WASM hosting is a later layer behind the same interface.

Containers hold multiple task documents in one file. A mutation targets a task by id; creation and removal are part of the same atomic request. `js-api` exposes the same path as stateless `applyMutation`/`previewMutation` for browser tools.

`server-auth` provides the server-side auth primitives: static bearer tokens mapped to principals, hashed token storage, expiring in-memory sessions, and framework-agnostic HTTP header/cookie helpers for login, session auth, and logout.

`server` is the first Ktor host. It exposes `/health`, `/auth/login`, `/auth/logout`, and `/auth/me`; the static token comes from `UPL_TOKEN`, with `UPL_PORT`, `UPL_COOKIE_NAME`, `UPL_COOKIE_SECURE`, and `UPL_SESSION_MINUTES` optional.

The planning API is `/api/document`, `/api/preview`, and `/api/mutation`, all relative to `UPL_VAULT`. Paths must stay inside the vault and end in `.upl.json`; mutations run through the Core gatekeeper and are serialized in-process before the atomic write.

Time fields are ISO instants stored in UTC; gantt still accepts date-only values for whole-day plans and treats them as UTC
start-of-day when paired with an instant.

Next candidates: `std.relations` dependency scheduling and `gantt.float`, handler registry/versioning.

Out of scope for now: the WASM registry and ABI, richer conflict resolution.

Design and task breakdown are produced by `architect`; tickets are written by `scribe`; implementation by `coder`.
