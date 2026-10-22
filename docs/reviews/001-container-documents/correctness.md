# Correctness review — container documents

## Round 1

Verdict: findings. The bundle commit/rollback path itself is sound: `MutableState` copies container fields and every task's field map, `prepareTasks` validates create/remove conflicts before any work, and a rejected mutation discards the whole copy. Targeted reactions also stay on the trigger task unless a handler explicitly sets `Mutation.target`, and the new UTC-instant gantt cases behave as tested. The issues below are in the document codec contract and in the mixed date/instant fallback.

### Finding 1 — `tasks` is a structural key but is still writable as an ordinary field

Location:
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/Gatekeeper.kt:117`
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:34`
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:45`
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:49`
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:127`

Problem: `validate` reserves only `id` and `kind`, so any requester can write a field named `tasks`. `UplJson.write` then puts that field and, for a container, immediately overwrites it with the task array; `UplJson.read` excludes a container's `tasks` key from fields and, for a kind-less document, treats the presence of `tasks` as the container discriminant. The write path therefore loses a container-level `tasks` field, and a task document carrying a `tasks` field is retyped as a container (or made unreadable when the value is not an array/object) on the next read.

Reproduction paths:
1. `Gatekeeper(HandlerRegistry(emptyList())).apply(UplDocument("t1", DocumentKind.TASK, emptyMap()), MutationRequest("x", listOf(Mutation(FieldKey("tasks"), JsonPrimitive("oops")))))` returns `Applied`. `UplFile.write` produces `{"id":"t1","tasks":"oops"}`; `UplFile.read` calls `kindOf(null, hasTasks = true)` and then `readTasks(JsonPrimitive("oops"))`, which throws `IllegalArgumentException`.
2. For a container, the same mutation returns `Applied` and a matching entry in `changes`, but `UplJson.write` overwrites `fields["tasks"]` with the task array, so `JsApi.applyMutation` reports `ok: true` while the returned `document` silently omits the applied change.

Suggested fix: make `tasks` a reserved structural key (reject it in `Gatekeeper.validate` alongside `id`/`kind`, and filter it from flat create-task fields), and ensure `UplJson.write` cannot overwrite a user field with the task array. Also consider writing an explicit `"kind":"task"` for task documents, or otherwise removing the ambiguity for legacy kind-less files that contain a `tasks` key.

### Finding 2 — mixed date-only and instant gantt values silently skip derivation

Location:
- `handlers/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/handlers/common/TimeValues.kt:54`
- `handlers/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/handlers/gantt/GanttHandler.kt:43`

Problem: `durationBetween` deliberately returns `null` when one `Moment` is a `DateMoment` and the other is an `InstantMoment`. `GanttHandler.derivedDuration` treats that null as "no reaction", so the end mutation is accepted and committed while `std.time.duration` keeps its old value.

Reproduction: take a task with `std.time.start = "2026-04-01"`, `std.time.end = "2026-04-02T00:00:00Z"`, and `std.time.duration = "P1D"`. Apply a gantt mutation setting `std.time.end = "2026-04-03T00:00:00Z"`. The result has the new end but still has duration `P1D` even though the span is two days. The reverse pair (instant start, date end) behaves the same. This matters because both representations are accepted on read, so a document migrated from date-only to instants can be edited without its derived duration ever being corrected.

Suggested fix: normalize the two representations inside `durationBetween`/`endOf` (for example, interpret a bare date as UTC start-of-day when paired with an instant) so a duration is always derived, or add a validation rejection for mixed representations instead of applying the mutation with a stale derived field.

### Finding 3 — container task ids are taken from the nested task, not from the map key

Location:
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:35`
- `core/src/commonMain/kotlin/dev/cypdashuhn/uniplanner/core/UplJson.kt:110`

Problem: `UplDocument.tasks` is a `Map<String, UplDocument>`, but `UplJson.write` serializes only `document.tasks.values`, and `taskJson` writes `document.id`. The map key is discarded. `readTaskObject` enforces `declaredId == key`, so the invariant exists on read but not on write. A directly constructed container with a key/id mismatch therefore changes on a write/read roundtrip: the task is rebound to its embedded id and the original key lookup (`document.task("t1")`) no longer resolves.

Reproduction: `UplDocument("c", DocumentKind.CONTAINER, emptyMap(), mapOf("t1" to UplDocument("other", DocumentKind.TASK, emptyMap())))` written with `UplFile.write` and read back has tasks keyed by `"other"`, not `"t1"`; the roundtripped `UplDocument` is not equal to the original.

Suggested fix: serialize the map key as the task id (and use it in `taskJson`), or enforce `require(task.id == key)` in `UplJson.write` (and ideally in the model) so an inconsistent container cannot be written.

## Round 2

Verdict: concur with all three Round 1 findings as addressed; no new findings.

- Finding 1: concur. `tasks` is now reserved in `CoreFields`, rejected by `Gatekeeper.validate`, and blocked by `UplDocument.init`; `UplJson.write` writes an explicit `kind` for task documents, so a task carrying a `tasks` field can no longer be written and re-read as a container. The container write path can no longer overwrite a `tasks` field because the model rejects such fields. Checked the internal construction paths (`UplJson.read`/`readTask`, `Gatekeeper.MutableState.snapshot`) and they satisfy the new invariants.
- Finding 2: concur. `durationBetween` and the new `sameMoment` normalize date-only values to UTC start-of-day when paired with an instant, in both directions; the new gantt tests cover date-start/instant-end and instant-start/date-end, and both derive the expected duration. The remaining `DateMoment.plus` null for sub-day durations is the pre-existing date-only whole-day restriction documented in `docs/README.md`, not a regression from this change.
- Finding 3: concur. `UplDocument.init` now enforces key == task id, and `UplJson.write` cannot receive a container whose map key disagrees with the embedded task id; the Round 1 mismatch reproduction is rejected at construction instead of silently changing on roundtrip.

Regression check: the only behavior change from the new invariant is that legacy non-container documents with a literal `tasks` field now fail model construction instead of being read as ordinary fields. That is the intended consequence of reserving `tasks` as structural; no other regression found in container roundtrips, task creation/removal atomicity, reaction scoping, or the JS request contract.
