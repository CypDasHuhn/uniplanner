package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonElement

enum class DocumentKind { TASK, WORKSPACE, CONTAINER }

data class UplDocument(
    override val id: String,
    val kind: DocumentKind,
    val fields: Map<FieldKey, JsonElement>,
    val tasks: Map<String, UplDocument> = emptyMap(),
) : DocumentView {
    init {
        require(fields.keys.none { it == CoreFields.id || it == CoreFields.kind || it == CoreFields.tasks }) {
            "core fields are structural"
        }
        require(tasks.all { (id, task) -> id == task.id }) { "task map key must match task id" }
    }

    override fun value(field: FieldKey): JsonElement? = fields[field]

    val isContainer: Boolean get() = kind == DocumentKind.CONTAINER
    val isWorkspace: Boolean get() = kind == DocumentKind.WORKSPACE

    fun task(id: String): UplDocument? = tasks[id]
}

object CoreFields {
    val id = FieldKey("id")
    val kind = FieldKey("kind")
    val tasks = FieldKey("tasks")
}
