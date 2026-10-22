package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
import kotlinx.serialization.json.JsonElement

private const val MAX_CHAIN = 64

sealed interface MutationResult {
    data class Applied(
        val document: UplDocument,
        val changes: List<FieldChange>,
    ) : MutationResult

    data class Rejected(
        val reason: String,
    ) : MutationResult
}

class Gatekeeper(
    private val registry: HandlerRegistry,
) {
    fun apply(
        document: UplDocument,
        request: MutationRequest,
    ): MutationResult {
        val state = MutableState(document)
        val rejection = prepareTasks(document, request, state)
        if (rejection != null) return MutationResult.Rejected(rejection)

        val changes = mutableListOf<FieldChange>()
        val created =
            request.createTasks.flatMap { task ->
                task.fields.map { (field, value) ->
                    PendingMutation(request.requester, Mutation(field, value, task.id), task.id, emptyList())
                }
            }
        val requested = request.mutations.map { PendingMutation(request.requester, it, it.target, emptyList()) }
        val pending = ArrayDeque(created + requested)

        while (pending.isNotEmpty()) {
            val pendingMutation = pending.removeFirst()
            val mutation = pendingMutation.mutation
            val target = pendingMutation.target
            val rejection = validate(document, state, mutation, target, pendingMutation.writer)
            if (rejection != null) return MutationResult.Rejected(rejection)

            val fields = state.fieldsFor(target) ?: return MutationResult.Rejected("unknown task target: $target")
            val oldValue = fields[mutation.field]
            val newValue = mutation.value
            if (newValue == null) {
                if (oldValue == null) continue
                fields.remove(mutation.field)
            } else {
                if (oldValue == newValue) continue
                fields[mutation.field] = newValue
            }
            val change = FieldChange(mutation.field, oldValue, newValue, target)
            changes += change

            val view = state.viewFor(target)
            for (handler in registry.subscribersOf(mutation.field)) {
                val invocation = Invocation(handler.namespace, mutation.field, target)
                if (invocation in pendingMutation.chain) {
                    return MutationResult.Rejected("circular handler invocation: ${describe(pendingMutation.chain + invocation)}")
                }
                if (pendingMutation.chain.size >= MAX_CHAIN) {
                    return MutationResult.Rejected("handler chain exceeded $MAX_CHAIN invocations")
                }
                val chain = pendingMutation.chain + invocation
                handler.onFieldChanged(change, view).forEach { reaction ->
                    pending.addLast(PendingMutation(handler.namespace, reaction, reaction.target ?: target, chain))
                }
            }
        }

        return MutationResult.Applied(state.snapshot(), changes)
    }

    private fun prepareTasks(
        document: UplDocument,
        request: MutationRequest,
        state: MutableState,
    ): String? {
        val changesTasks = request.createTasks.isNotEmpty() || request.removeTasks.isNotEmpty()
        if (changesTasks && !document.isContainer) {
            return "task creation and removal require a container document"
        }
        val createdIds = request.createTasks.map { it.id }
        if (createdIds.size != createdIds.toSet().size) {
            return "duplicate created task id"
        }
        val conflicts = createdIds.filter { it in state.taskIds || it in request.removeTasks }
        if (conflicts.isNotEmpty()) {
            return "task already exists or is also removed: ${conflicts.joinToString()}"
        }
        val missing = request.removeTasks.filter { it !in state.taskIds }
        if (missing.isNotEmpty()) {
            return "unknown task target: ${missing.joinToString()}"
        }
        request.removeTasks.forEach(state::removeTask)
        request.createTasks.forEach(state::addTask)
        return null
    }

    private fun validate(
        document: UplDocument,
        state: MutableState,
        mutation: Mutation,
        target: String?,
        writer: String,
    ): String? {
        if (mutation.field == CoreFields.id || mutation.field == CoreFields.kind || mutation.field == CoreFields.tasks) {
            return "${mutation.field.value} is a core field"
        }
        if (target != null && !document.isContainer) {
            return "target '$target' is not allowed for a ${document.kind.name.lowercase()} document"
        }
        if (target != null && !state.hasTask(target)) {
            return "unknown task target: $target"
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
        chain.joinToString(" -> ") {
            val target = it.target?.let { task -> "$task:" }.orEmpty()
            "${it.namespace}($target${it.field.value})"
        }

    private data class Invocation(
        val namespace: String,
        val field: FieldKey,
        val target: String?,
    )

    private data class PendingMutation(
        val writer: String,
        val mutation: Mutation,
        val target: String?,
        val chain: List<Invocation>,
    )
}

private class MutableState(
    private val source: UplDocument,
) {
    private val fields = source.fields.toMutableMap()
    private val tasks = source.tasks.mapValuesTo(linkedMapOf()) { (_, task) -> task.fields.toMutableMap() }

    val taskIds: Set<String> get() = tasks.keys

    fun hasTask(target: String): Boolean = target in tasks

    fun addTask(task: NewTask) {
        tasks[task.id] = mutableMapOf()
    }

    fun removeTask(id: String) {
        tasks.remove(id)
    }

    fun fieldsFor(target: String?): MutableMap<FieldKey, JsonElement>? =
        if (target == null) fields else tasks[target]

    fun viewFor(target: String?): DocumentView =
        if (target == null) {
            FieldView(source.id, fields)
        } else {
            FieldView(target, tasks.getValue(target))
        }

    fun snapshot(): UplDocument =
        if (!source.isContainer) {
            UplDocument(source.id, source.kind, fields.toMap())
        } else {
            UplDocument(
                source.id,
                DocumentKind.CONTAINER,
                fields.toMap(),
                tasks.mapValuesTo(linkedMapOf()) { (id, taskFields) -> UplDocument(id, DocumentKind.TASK, taskFields.toMap()) },
            )
        }
}

private class FieldView(
    override val id: String,
    private val fields: Map<FieldKey, JsonElement>,
) : DocumentView {
    override fun value(field: FieldKey): JsonElement? = fields[field]
}
