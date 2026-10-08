# uniplanner planning

UniPlanner is a view-agnostic, open planning protocol for tasks and projects. Tools read `.upl.json` documents directly; every write goes through the Core gatekeeper, which enforces field ownership, runs subscribed handlers and commits atomically.

- [concept.md](concept.md) — the protocol concept summary
- [workflow.md](workflow.md) — how work is planned, executed, reviewed and committed
- `tasks/` — tickets, one file per unit of work
- `reviews/` — review reports per ticket
- `manual-test.md` — acceptance criteria that cannot be automated

## Current goal

A Kotlin/JVM Core v1: `.upl.json` read/write, a mutation gatekeeper with ownership checks, transitive handler dispatch and loop detection, and an in-process handler SPI. WASM handler hosting is a later layer behind the same interface.

Out of scope for now: the WASM registry and ABI, conflict resolution for bidirectional dependencies, and any view/tool implementation.

Design and task breakdown are produced by `architect`; tickets are written by `scribe`; implementation by `coder`.
