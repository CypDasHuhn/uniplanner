package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonElement

data class TaskDocument(
    val id: String,
    val fields: Map<FieldKey, JsonElement>,
)
