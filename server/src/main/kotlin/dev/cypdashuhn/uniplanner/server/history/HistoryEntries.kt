package dev.cypdashuhn.uniplanner.server.history

import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal object HistoryEntries {
    const val OP_MUTATION = "mutation"
    const val OP_REPLACE = "replace"
    const val STATUS_APPLIED = "applied"
    const val STATUS_REJECTED = "rejected"

    //region (Entries)
    fun mutation(
        id: String,
        timestamp: Instant,
        path: String,
        principal: String,
        before: UplDocument,
        request: MutationRequest,
        result: MutationResult,
    ): JsonObject {
        val applied = result as? MutationResult.Applied
        val rejected = result as? MutationResult.Rejected
        val document = applied?.document ?: before

        return buildJsonObject {
            put("v", 1)
            put("id", id)
            put("timestamp", timestamp.toString())
            put("op", OP_MUTATION)
            put("status", if (applied != null) STATUS_APPLIED else STATUS_REJECTED)
            put("path", path)
            put("document", documentSummary(document))
            put("principal", principal)
            put("requester", request.requester)
            put("request", requestJson(request))
            put("changes", changesJson(applied?.changes.orEmpty()))
            put("createdTaskIds", JsonArray(request.createTasks.map { JsonPrimitive(it.id) }))
            put("removedTasks", removedTasksJson(before, request.removeTasks))
            rejected?.let { put("reason", it.reason) }
        }
    }

    fun replacement(
        id: String,
        timestamp: Instant,
        path: String,
        principal: String,
        before: UplDocument?,
        after: UplDocument,
        request: MutationRequest,
    ): JsonObject =
        buildJsonObject {
            put("v", 1)
            put("id", id)
            put("timestamp", timestamp.toString())
            put("op", OP_REPLACE)
            put("status", STATUS_APPLIED)
            put("path", path)
            put("document", documentSummary(after))
            put("principal", principal)
            put("requester", request.requester)
            put("request", requestJson(request))
            put("changes", JsonArray(emptyList()))
            put("createdTaskIds", JsonArray(emptyList()))
            put("removedTasks", JsonArray(emptyList()))
            put(
                "replacement",
                buildJsonObject {
                    put("before", before?.let(::documentSummary) ?: JsonNull)
                    put("after", documentSummary(after))
                },
            )
        }
    //endregion

    //region (Summaries)
    private fun documentSummary(document: UplDocument): JsonObject =
        buildJsonObject {
            put("id", document.id)
            put("kind", document.kind.name.lowercase())
            put("taskCount", taskCount(document))
        }

    private fun taskCount(document: UplDocument): Int =
        when (document.kind) {
            DocumentKind.CONTAINER -> document.tasks.size
            DocumentKind.TASK -> 1
            DocumentKind.WORKSPACE -> 0
        }
    //endregion

    //region (Request)
    private fun requestJson(request: MutationRequest): JsonObject =
        buildJsonObject {
            put("requester", request.requester)
            put("mutations", JsonArray(request.mutations.map(::mutationJson)))
            put("createTasks", JsonArray(request.createTasks.map(::newTaskJson)))
            put("removeTasks", JsonArray(request.removeTasks.map(::JsonPrimitive)))
        }

    private fun mutationJson(mutation: Mutation): JsonObject =
        buildJsonObject {
            put("field", mutation.field.value)
            put("target", mutation.target?.let(::JsonPrimitive) ?: JsonNull)
            put("value", mutation.value?.let(::canonicalJson) ?: JsonNull)
        }

    private fun newTaskJson(task: NewTask): JsonObject =
        buildJsonObject {
            put("id", task.id)
            put(
                "fields",
                buildJsonObject {
                    task.fields.entries
                        .sortedBy { it.key.value }
                        .forEach { (field, value) -> put(field.value, canonicalJson(value)) }
                },
            )
        }
    //endregion

    //region (Changes and snapshots)
    private fun changesJson(changes: List<FieldChange>): JsonArray =
        buildJsonArray {
            changes.forEach { change ->
                add(
                    buildJsonObject {
                        put("field", change.field.value)
                        put("target", change.target?.let(::JsonPrimitive) ?: JsonNull)
                        put("oldValue", change.oldValue ?: JsonNull)
                        put("newValue", change.newValue ?: JsonNull)
                    },
                )
            }
        }

    private fun canonicalJson(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> {
                buildJsonObject {
                    element.entries
                        .sortedBy { it.key }
                        .forEach { (key, value) -> put(key, canonicalJson(value)) }
                }
            }

            is JsonArray -> {
                buildJsonArray {
                    element.forEach { add(canonicalJson(it)) }
                }
            }

            else -> {
                element
            }
        }

    private fun removedTasksJson(
        before: UplDocument,
        removedTasks: List<String>,
    ): JsonArray =
        buildJsonArray {
            removedTasks.forEach { taskId ->
                before.task(taskId)?.let { add(taskSnapshot(it)) }
            }
        }

    private fun taskSnapshot(task: UplDocument): JsonObject =
        buildJsonObject {
            put("id", task.id)
            task.fields.entries
                .sortedBy { it.key.value }
                .forEach { (field, value) -> put(field.value, value) }
        }
    //endregion
}
