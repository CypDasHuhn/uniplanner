package dev.cypdashuhn.uniplanner.handler

import kotlinx.serialization.json.JsonElement

interface DocumentView {
    val id: String

    fun value(field: FieldKey): JsonElement?
}
