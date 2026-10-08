package dev.cypdashuhn.uniplanner.handlers.common

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

fun JsonElement?.textOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

fun JsonElement?.booleanOrNull(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

fun DocumentView.textOrNull(field: FieldKey): String? = value(field).textOrNull()

fun DocumentView.booleanOrNull(field: FieldKey): Boolean? = value(field).booleanOrNull()
