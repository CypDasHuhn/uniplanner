# UniPlanner — Concept Summary

## What is UniPlanner?

UniPlanner is a **view-agnostic, open planning protocol** for tasks and
projects. It defines no UI, no framework, no cloud architecture — just a shared
data format and a write gatekeeper through which arbitrary tools (Kanban, Gantt,
Eisenhower matrix, etc.) can coordinate.

---

## File Format

Tasks are stored as `.upl.json` (double extension convention — right side:
format, left side: semantics).

```json
{
  "id": "abc123",
  "std.name": "Fix login bug",
  "std.status": "in-progress",
  "std.priority": "high",
  "std.deadline": "2026-04-29",
  "gantt.start": "2026-04-01",
  "gantt.float": "2d",
  "kanban.column": "doing",
  "eisenhower.urgency": true
}
```

### Containers

A single `.upl.json` can hold many tasks. The container has its own id and
fields; each child task keeps the normal task shape and is addressed by id:

```json
{
  "id": "week_01",
  "kind": "container",
  "std.name": "Week 16",
  "tasks": [
    { "id": "t1", "std.name": "Fix login bug", "std.time.start": "2026-04-01T08:00:00Z" },
    { "id": "t2", "std.name": "Review PR", "std.time.start": "2026-04-01T10:00:00Z" }
  ]
}
```

Mutations carry a `target` task id. A request can create and remove tasks
alongside field mutations; the whole request still commits atomically. Reading
a `tasks` object keyed by id is also accepted.

```json
{
  "requester": "gantt",
  "mutations": [
    { "field": "std.time.start", "target": "t1", "value": "2026-04-02T09:00:00Z" }
  ],
  "createTasks": [
    { "id": "t3", "fields": { "std.name": "Write tests", "std.time.duration": "PT1H" } }
  ],
  "removeTasks": ["t2"]
}
```

`createTasks` uses a nested `fields` object; the flat form is also accepted.
`UplRequestJson` is the shared parser behind the JS API. `id`, `kind` and `tasks` are structural keys, not fields.

### Field Hierarchy

| Level              | Description                           | Example                      |
| ------------------ | ------------------------------------- | ---------------------------- |
| **Core**           | Immutable identity                    | `id`, `std.name`             |
| **Elevated / std** | Shared vocabulary, no exclusive owner | `std.status`, `std.deadline` |
| **Namespace**      | Tool-specific, owned by handler       | `gantt.*`, `kanban.*`        |

---

## Architecture

### Core as Write Gatekeeper

**Tools read directly** from the JSON. **Tools never write directly** — they
send mutation requests to the UniPlanner Core process:

```
core.request_mutation(
  requester: "kanban",
  mutations: [{ key: "std.status", value: "done" }]
)
```

Core flow:

1. Receive mutation request
2. Check ownership — is this tool allowed to write this field?
3. Run all subscribed handlers
4. Resolve conflicts
5. Write atomically to JSON

This gives **transaction safety** for free: either all mutations in a request
are written, or none of them are.

### Loop Detection

Since handlers never write directly but always through Core, Core can detect and
abort circular handler calls.

---

## WASM Handler System

Each namespace has an associated `.wasm` handler containing its mapping and
aggregation logic.

### Registry

- Central registry with versioned files like `gantt@1.2.0.wasm`
- If a `.upl.json` contains an unknown namespace → handler is automatically
  fetched, stored, and executed
- WASM as sandbox: no trust problem, cross-platform, versionable

### Handler Interface (draft)

```
on_field_changed(field, old_value, new_value) -> []Mutation
get_owned_fields() -> []FieldName
get_subscribed_fields() -> []FieldName
aggregate(field) -> Strategy
```

Only the **mapper and aggregator** are WASM modules. Reading and initiating
writes are handled by the tool itself.

---

## Ownership & Conflict Resolution

Each handler declares:

```
owned_fields: [std.end, std.duration]
subscribed_fields: [std.start]
```

If two handlers claim ownership of the same `std` field → **registry error at
upload time**, not a runtime conflict. Conflicts are prevented at build time.

### Bidirectional Dependencies

Problem: `end = start + duration` — three variables, which one gets recalculated
when one changes? Open question: possible resolution via **last-writer-wins with
ownership**, or explicit priority definition per field.

---

## Tool Spec

Integrating a new tool into the ecosystem requires four functions:

1. **Read** — parse JSON and render a view (free implementation)
2. **Write** — send mutation requests to Core
3. **Map** — declare owned/subscribed fields, define transitive mappings (WASM)
4. **Aggregate** — define how parent values are computed from children (WASM)

Implementing only step 1 gives a valid read-only viewer. Implementing all four
gives a full tool.

---

## Integration with .tl and .ct

The ecosystem integrates tightly with the other formats:

- **CT attribute** `upl-id` references tasks by UUID
- **CT tag** `upl-kanban = #upl #kanban @upl-id=[UUID]` links timeline entries
  to tasks
- **TL entries** tagged this way enable automatic time tracking: the Kanban
  board shows _actual_ time worked directly from timeline logs — no separate
  time tracking tool needed

→ Logging and planning converge.

---

## License

**LGPL** — maximizes adoption while preventing anyone from forking Core itself
into a proprietary product. Proprietary tools that _use_ UniPlanner are
explicitly allowed and not considered bad faith.

---

## Open Questions

- Bidirectional dependencies (the `end/start/duration` problem) — conflict
  resolution not yet finalized
- Registry architecture: central server vs. decentralized (npm-style with
  mirrors)
- Concrete WASM interface spec not yet written
- Implementation language for Core: Kotlin/JVM decided for v1, Zig was under
  consideration
