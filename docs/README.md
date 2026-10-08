# uniplanner planning

UniPlanner is a view-agnostic, open planning protocol for tasks and projects. Tools read `.upl.json` documents directly; every write goes through the Core gatekeeper, which enforces field ownership, runs subscribed handlers and commits atomically.

- [concept.md](concept.md) — the protocol concept summary
- [workflow.md](workflow.md) — how work is planned, executed, reviewed and committed
- `tasks/` — tickets, one file per unit of work
- `reviews/` — review reports per ticket
- `manual-test.md` — acceptance criteria that cannot be automated

## Current state

Core v1 works: `.upl.json` read/write with `task`/`workspace` kinds, prefix-based ownership and subscription with group-key rejection, no-op suppression, `(handler, field)` cycle detection, and three built-in handlers:

- `kanban` — columns, ordering, board config in `kanban.columns`, close/reopen propagation
- `dayplanner` — week placement per day, cleared when the task closes
- `gantt` — `std.time.start`/`duration` canonical, `end` derived

Handler SPI is in-process (`ServiceLoader` for externals); WASM hosting is a later layer behind the same interface.

Next candidates: multi-document transactions (workspace ↔ tasks), `std.relations` dependency scheduling and `gantt.float`, handler registry/versioning.

Out of scope for now: the WASM registry and ABI, richer conflict resolution.

Design and task breakdown are produced by `architect`; tickets are written by `scribe`; implementation by `coder`.
