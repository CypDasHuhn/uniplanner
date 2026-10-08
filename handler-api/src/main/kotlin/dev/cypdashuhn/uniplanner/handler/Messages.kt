package dev.cypdashuhn.uniplanner.handler

import kotlinx.serialization.json.JsonElement

data class Mutation(
    val field: FieldKey,
    val value: JsonElement?,
)

data class FieldChange(
    val field: FieldKey,
    val oldValue: JsonElement?,
    val newValue: JsonElement?,
)

data class MutationRequest(
    val requester: String,
    val mutations: List<Mutation>,
)
