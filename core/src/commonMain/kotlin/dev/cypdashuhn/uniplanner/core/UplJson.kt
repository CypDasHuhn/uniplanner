package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object UplJson {
    private const val TASKS = "tasks"

    fun read(
        text: String,
        source: String = "<json>",
    ): UplDocument {
        val json = Json.parseToJsonElement(text).jsonObject
        return readDocument(json, source)
    }

    fun write(document: UplDocument): String =
        buildJsonObject {
            put(CoreFields.id.value, JsonPrimitive(document.id))
            put(CoreFields.kind.value, JsonPrimitive(document.kind.name.lowercase()))
            document.fields.forEach { (field, value) -> put(field.value, value) }
            if (document.isContainer) {
                put(TASKS, buildJsonArray { document.tasks.values.forEach { add(taskJson(it)) } })
            }
        }.toString()

    private fun readDocument(
        json: JsonObject,
        source: String,
    ): UplDocument {
        val id = requireNotNull(json[CoreFields.id.value]?.jsonPrimitive?.contentOrNull) { "missing id in $source" }
        val rawKind = json[CoreFields.kind.value]?.jsonPrimitive?.contentOrNull
        val kind = kindOf(rawKind, json.containsKey(TASKS), source)
        val tasks = if (kind == DocumentKind.CONTAINER) readTasks(json[TASKS], source) else emptyMap()
        val fields =
            json.entries
                .filter { (key, _) -> key != CoreFields.id.value && key != CoreFields.kind.value && !isTasksKey(key, kind) }
                .associate { (key, value) -> FieldKey(key) to value }
        return UplDocument(id, kind, fields, tasks)
    }

    private fun readTasks(
        element: JsonElement?,
        source: String,
    ): Map<String, UplDocument> =
        when (element) {
            null, JsonNull -> emptyMap()
            is JsonArray -> readTaskArray(element, source)
            is JsonObject -> readTaskObject(element, source)
            else -> throw IllegalArgumentException("tasks must be an array or object in $source")
        }

    private fun readTaskArray(
        array: JsonArray,
        source: String,
    ): Map<String, UplDocument> {
        val tasks = linkedMapOf<String, UplDocument>()
        array.forEach { element ->
            val json = element.jsonObject
            val id = requireNotNull(json[CoreFields.id.value]?.jsonPrimitive?.contentOrNull) { "missing task id in $source" }
            require(id !in tasks) { "duplicate task id '$id' in $source" }
            tasks[id] = readTask(json, id, source)
        }
        return tasks
    }

    private fun readTaskObject(
        json: JsonObject,
        source: String,
    ): Map<String, UplDocument> {
        val tasks = linkedMapOf<String, UplDocument>()
        json.forEach { (key, element) ->
            val taskJson = element.jsonObject
            val declaredId = taskJson[CoreFields.id.value]?.jsonPrimitive?.contentOrNull
            require(declaredId == null || declaredId == key) { "task '$key' declares id '$declaredId' in $source" }
            tasks[key] = readTask(taskJson, key, source)
        }
        return tasks
    }

    private fun readTask(
        json: JsonObject,
        fallbackId: String,
        source: String,
    ): UplDocument {
        val id = json[CoreFields.id.value]?.jsonPrimitive?.contentOrNull ?: fallbackId
        val rawKind = json[CoreFields.kind.value]?.jsonPrimitive?.contentOrNull
        require(rawKind == null || rawKind == "task") { "task '$id' has kind '$rawKind' in $source" }
        val fields =
            json.entries
                .filter { (key, _) -> key != CoreFields.id.value && key != CoreFields.kind.value }
                .associate { (key, value) -> FieldKey(key) to value }
        return UplDocument(id, DocumentKind.TASK, fields)
    }

    private fun taskJson(document: UplDocument): JsonObject =
        buildJsonObject {
            put(CoreFields.id.value, JsonPrimitive(document.id))
            document.fields.forEach { (field, value) -> put(field.value, value) }
        }

    private fun isTasksKey(
        key: String,
        kind: DocumentKind,
    ): Boolean = kind == DocumentKind.CONTAINER && key == TASKS

    private fun kindOf(
        raw: String?,
        hasTasks: Boolean,
        source: String,
    ): DocumentKind =
        when {
            raw == "container" -> DocumentKind.CONTAINER
            raw == "workspace" -> DocumentKind.WORKSPACE
            raw == null && hasTasks -> DocumentKind.CONTAINER
            raw == null || raw == "task" -> DocumentKind.TASK
            else -> throw IllegalArgumentException("unknown document kind '$raw' in $source")
        }
}
