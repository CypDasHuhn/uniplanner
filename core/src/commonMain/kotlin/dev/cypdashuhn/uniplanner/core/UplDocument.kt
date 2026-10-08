package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonElement

enum class DocumentKind { TASK, WORKSPACE }

data class UplDocument(
    override val id: String,
    val kind: DocumentKind,
    val fields: Map<FieldKey, JsonElement>,
) : DocumentView {
    override fun value(field: FieldKey): JsonElement? = fields[field]
}

object CoreFields {
    val id = FieldKey("id")
    val kind = FieldKey("kind")
}
