package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class UplFile(
    private val path: Path,
) {
    fun read(): UplDocument {
        val json = Json.parseToJsonElement(Files.readString(path)).jsonObject
        val id = requireNotNull(json[CoreFields.id.value]?.jsonPrimitive?.contentOrNull) { "missing id in $path" }
        val kind = kindOf(json[CoreFields.kind.value]?.jsonPrimitive?.contentOrNull)
        val fields =
            json.entries
                .filter { (key, _) -> key != CoreFields.id.value && key != CoreFields.kind.value }
                .associate { (key, value) -> FieldKey(key) to value }
        return UplDocument(id, kind, fields)
    }

    fun write(document: UplDocument) {
        val json =
            buildJsonObject {
                put(CoreFields.id.value, JsonPrimitive(document.id))
                if (document.kind == DocumentKind.WORKSPACE) {
                    put(CoreFields.kind.value, JsonPrimitive(document.kind.name.lowercase()))
                }
                document.fields.forEach { (field, value) -> put(field.value, value) }
            }
        val directory = requireNotNull(path.toAbsolutePath().parent) { "no parent directory for $path" }
        val temp = Files.createTempFile(directory, path.fileName.toString(), ".tmp")
        Files.writeString(temp, json.toString())
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun kindOf(raw: String?): DocumentKind =
        when (raw) {
            null, "task" -> DocumentKind.TASK
            "workspace" -> DocumentKind.WORKSPACE
            else -> throw IllegalArgumentException("unknown document kind '$raw' in $path")
        }
}
