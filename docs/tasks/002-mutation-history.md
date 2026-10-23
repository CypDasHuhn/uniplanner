---
name: Mutation history log
status: done
depends-on: []
reviewers: [correctness]
---

# Mutation history log

## Goal

Persist an append-only audit history of every write that reaches the vault, so operators can answer who changed which document, when, and with what effect, without making history part of the document model.

## Scope

- Append-only JSONL, one file per day: `${UPL_VAULT}/.upl-history/YYYY-MM-DD.jsonl` by default.
- Server config from the environment:
  - `UPL_HISTORY_ENABLED` — default `true`.
  - `UPL_HISTORY_DIR` — default `.upl-history`, resolved relative to the vault; must stay inside the vault.
  - `UPL_HISTORY_TIME_ZONE` — default `UTC`, an IANA zone id.
  - `UPL_HISTORY_RETENTION_DAYS` — default `90`; `0` keeps everything.
- Entry schema v1 (one JSON object per line):
  - `id`, UTC `timestamp` (ISO instant), `op` (`mutation` | `replace`), `status` (`applied` | `rejected`).
  - Vault-relative `path`.
  - `document`: summary only (document id, kind, task count), never the full document.
  - `principal`: the authenticated principal id from the request. `requester`: `MutationRequest.requester`.
  - `request`: the canonical request (stable field order, normalized nulls).
  - `changes`: every applied `FieldChange` (field, target, old value, new value), including handler reactions.
  - `createdTaskIds`, `removedTasks` (full task snapshots taken from the pre-write document), `replacement` (`before`/`after` summaries only), `reason` on rejection.
- Record applied **and** rejected `POST /api/mutation`.
- Record `PUT /api/document` replacements and creations as `op: replace` (successful writes only).
- Do not record previews, malformed request bodies, authentication failures, or reads.
- Write history inside the existing write mutex, **after** the atomic document write. A history failure must not fail the document write; log it at server level.
- Retention/pruning runs on startup and on day rotation: only files matching the history daily-file pattern and older than the retention window; never current, future, or unrelated files.
- Read API, auth required:
  - `GET /api/history/days` — available days with entry counts, newest first.
  - `GET /api/history?date=&path=&status=&op=&requester=&limit=&before=` — entry list, newest first.
  - `date` defaults to today in `UPL_HISTORY_TIME_ZONE`; `limit` defaults to 100, max 500; `before` is an opaque cursor (last entry id of the previous page).
- Tolerate a partial trailing line on read and repair the newline before the next append.
- Tests for storage, rotation, retention, config, append failure, ordering, concurrency, read filters, cursor, and auth.

## Acceptance criteria

- An applied mutation appends exactly one line under the day file for its UTC instant, containing all applied changes and created/removed task details.
- A rejected mutation appends one line with `status: rejected` and the rejection reason; the document is untouched.
- A successful document replace appends one `replace` line with before/after summaries and no full document body.
- Previews, malformed requests, auth failures, and reads append nothing.
- Two concurrent mutations serialize through the write mutex and produce lines in commit order.
- A simulated history append failure still returns success for the document write and logs the history error.
- Retention removes only expired daily files and leaves the current, future, and unrelated files intact.
- A partial trailing line does not break reads, and the next append starts on a fresh line.
- Read endpoints enforce auth, default to today, honor filters and limits, paginate via `before`, and return newest first.
- `./gradlew build` is green.

## Out of scope

- Client UI.
- Cross-session revert or reapply.
- Full replacement snapshots (before/after full documents).
- Logging previews.
- Multi-process file locking.
- Size-based rotation.

## Notes

- This is an audit log only; the `.upl.json` document remains the source of truth.
- No precomputed inverse is stored in v1; a future revert ticket would derive it from `changes` and task snapshots.
- Audit failure is observable only through server logs in v1.
