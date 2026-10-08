package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest

sealed interface MutationResult {
    data class Applied(val document: TaskDocument, val changes: List<FieldChange>) : MutationResult

    data class Rejected(val reason: String) : MutationResult
}

class Gatekeeper(private val registry: HandlerRegistry) {
    fun apply(document: TaskDocument, request: MutationRequest): MutationResult {
        val fields = document.fields.toMutableMap()
        val changes = mutableListOf<FieldChange>()
        val pending = ArrayDeque(request.mutations.map { PendingMutation(request.requester, it, emptyList()) })

        while (pending.isNotEmpty()) {
            val pendingMutation = pending.removeFirst()
            val mutation = pendingMutation.mutation
            val owner = registry.ownerOf(mutation.field)

            if (owner != null && owner != pendingMutation.writer) {
                return MutationResult.Rejected(
                    "${pendingMutation.writer} cannot write ${mutation.field.value}: owned by $owner",
                )
            }

            val newValue = mutation.value
            val oldValue = fields[mutation.field]
            if (newValue == null) {
                fields.remove(mutation.field)
            } else {
                fields[mutation.field] = newValue
            }
            val change = FieldChange(mutation.field, oldValue, newValue)
            changes += change

            registry.subscribersOf(mutation.field).forEach { handler ->
                if (handler.namespace in pendingMutation.chain) {
                    val cycle = (pendingMutation.chain + handler.namespace).joinToString(" -> ")
                    return MutationResult.Rejected("circular handler invocation: $cycle")
                }
                val chain = pendingMutation.chain + handler.namespace
                handler.onFieldChanged(change).forEach { reaction ->
                    pending.addLast(PendingMutation(handler.namespace, reaction, chain))
                }
            }
        }

        return MutationResult.Applied(TaskDocument(document.id, fields), changes)
    }

    private data class PendingMutation(
        val writer: String,
        val mutation: Mutation,
        val chain: List<String>,
    )
}
