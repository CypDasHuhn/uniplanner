package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object UplRequestJson {
    private const val ID = "id"
    private const val KIND = "kind"
    private const val FIELDS = "fields"

    fun read(
        text: String,
        source: String = "<request>",
    ): MutationRequest {
        val json = Json.parseToJsonElement(text).jsonObject
        return MutationRequest(
            requester = json.requireString("requester", source),
            mutations = json["mutations"]?.jsonArray.orEmpty().map { readMutation(it.jsonObject) },
            createTasks = json["createTasks"]?.jsonArray.orEmpty().map { readTask(it.jsonObject, source) },
            removeTasks = json["removeTasks"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
        )
    }

    private fun readMutation(json: JsonObject): Mutation =
        Mutation(
            field = FieldKey(json.requireString("field", "mutation")),
            value = json["value"]?.takeUnless { it is JsonNull },
            target = json["target"]?.jsonPrimitive?.contentOrNull,
        )

    private fun readTask(
        json: JsonObject,
        source: String,
    ): NewTask {
        val id = json.requireString(ID, source)
        val fields = taskFields(json, source)
        return NewTask(id, fields.associate { (key, value) -> FieldKey(key) to value })
    }

    private fun taskFields(
        json: JsonObject,
        source: String,
    ): Iterable<Map.Entry<String, JsonElement>> {
        val nested = json[FIELDS]
        return when (nested) {
            null -> json.entries.filter { (key, _) -> key != ID && key != KIND }
            is JsonObject -> nested.entries
            else -> throw IllegalArgumentException("task fields must be an object in $source")
        }
    }

    private fun JsonObject.requireString(
        key: String,
        source: String,
    ): String = requireNotNull(this[key]?.jsonPrimitive?.contentOrNull) { "missing $key in $source" }
}
