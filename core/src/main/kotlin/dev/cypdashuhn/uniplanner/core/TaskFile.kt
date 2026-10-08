package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class TaskFile(private val path: Path) {
    fun read(): TaskDocument {
        val json = Json.parseToJsonElement(Files.readString(path)).jsonObject
        val id = requireNotNull(json["id"]?.jsonPrimitive?.content) { "missing id in $path" }
        val fields = json.entries
            .filter { (key, _) -> key != "id" }
            .associate { (key, value) -> FieldKey(key) to value }
        return TaskDocument(id, fields)
    }

    fun write(document: TaskDocument) {
        val json = buildJsonObject {
            put("id", JsonPrimitive(document.id))
            document.fields.forEach { (field, value) -> put(field.value, value) }
        }
        val directory = requireNotNull(path.toAbsolutePath().parent) { "no parent directory for $path" }
        val temp = Files.createTempFile(directory, path.fileName.toString(), ".tmp")
        Files.writeString(temp, json.toString())
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
