package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import kotlinx.serialization.json.JsonElement

private const val MAX_CHAIN = 64

sealed interface MutationResult {
    data class Applied(val document: UplDocument, val changes: List<FieldChange>) : MutationResult

    data class Rejected(val reason: String) : MutationResult
}

class Gatekeeper(private val registry: HandlerRegistry) {
    fun apply(document: UplDocument, request: MutationRequest): MutationResult {
        val fields = document.fields.toMutableMap()
        val changes = mutableListOf<FieldChange>()
        val pending = ArrayDeque(request.mutations.map { PendingMutation(request.requester, it, emptyList()) })

        while (pending.isNotEmpty()) {
            val pendingMutation = pending.removeFirst()
            val mutation = pendingMutation.mutation
            val rejection = validate(mutation, pendingMutation.writer)
            if (rejection != null) return MutationResult.Rejected(rejection)

            val oldValue = fields[mutation.field]
            val newValue = mutation.value
            if (newValue == null) {
                if (oldValue == null) continue
                fields.remove(mutation.field)
            } else {
                if (oldValue == newValue) continue
                fields[mutation.field] = newValue
            }
            val change = FieldChange(mutation.field, oldValue, newValue)
            changes += change

            val view = FieldView(document.id, fields)
            for (handler in registry.subscribersOf(mutation.field)) {
                val invocation = Invocation(handler.namespace, mutation.field)
                if (invocation in pendingMutation.chain) {
                    return MutationResult.Rejected("circular handler invocation: ${describe(pendingMutation.chain + invocation)}")
                }
                if (pendingMutation.chain.size >= MAX_CHAIN) {
                    return MutationResult.Rejected("handler chain exceeded $MAX_CHAIN invocations")
                }
                val chain = pendingMutation.chain + invocation
                handler.onFieldChanged(change, view).forEach { reaction ->
                    pending.addLast(PendingMutation(handler.namespace, reaction, chain))
                }
            }
        }

        return MutationResult.Applied(UplDocument(document.id, document.kind, fields), changes)
    }

    private fun validate(mutation: Mutation, writer: String): String? {
        if (mutation.field == CoreFields.id || mutation.field == CoreFields.kind) {
            return "${mutation.field.value} is a core field"
        }
        if (registry.isGroupKey(mutation.field)) {
            return "${mutation.field.value} is a group key"
        }
        val owner = registry.ownerOf(mutation.field)
        if (owner != null && owner != writer) {
            return "$writer cannot write ${mutation.field.value}: owned by $owner"
        }
        return null
    }

    private fun describe(chain: List<Invocation>): String =
        chain.joinToString(" -> ") { "${it.namespace}(${it.field.value})" }

    private data class Invocation(val namespace: String, val field: FieldKey)

    private data class PendingMutation(val writer: String, val mutation: Mutation, val chain: List<Invocation>)
}

private class FieldView(override val id: String, private val fields: Map<FieldKey, JsonElement>) : DocumentView {
    override fun value(field: FieldKey): JsonElement? = fields[field]
}
