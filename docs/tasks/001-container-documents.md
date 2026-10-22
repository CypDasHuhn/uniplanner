---
name: Container documents
status: done
depends-on: []
reviewers: [correctness]
---

# Container documents

## Goal

Allow one `.upl.json` to hold multiple task documents and mutate them atomically.

## Scope

- `kind: container` with a top-level `tasks` array (object form accepted on read).
- Task-targeted mutations via `Mutation.target` and `FieldChange.target`.
- `MutationRequest.createTasks` / `removeTasks`, committed together with field mutations.
- Shared JSON codecs in common code: `UplJson` for documents and `UplRequestJson` for requests.
- JS API accepts `createTasks` with nested `fields`, and the flat task form.
- UTC ISO instants in gantt; date-only values stay supported.

## Acceptance criteria

- A container round-trips through `UplFile`.
- A container mutation touches only the targeted task.
- A rejected mutation rolls back task creation and all other changes.
- Handler reactions stay scoped to the triggering task.
- `js-api` exposes stateless `applyMutation` and `previewMutation`.
- `./gradlew build` is green.

## Review

- [Correctness report](../reviews/001-container-documents/correctness.md)

## Out of scope

- Nested containers.
- Multi-file transactions.
- Container-aware validation rules (minimum duration, overlap).
