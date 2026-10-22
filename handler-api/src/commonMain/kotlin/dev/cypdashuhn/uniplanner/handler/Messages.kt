package dev.cypdashuhn.uniplanner.handler

import kotlinx.serialization.json.JsonElement

data class Mutation(
    val field: FieldKey,
    val value: JsonElement?,
    val target: String? = null,
)

data class FieldChange(
    val field: FieldKey,
    val oldValue: JsonElement?,
    val newValue: JsonElement?,
    val target: String? = null,
)

data class NewTask(
    val id: String,
    val fields: Map<FieldKey, JsonElement> = emptyMap(),
)

data class MutationRequest(
    val requester: String,
    val mutations: List<Mutation>,
    val createTasks: List<NewTask> = emptyList(),
    val removeTasks: List<String> = emptyList(),
)
