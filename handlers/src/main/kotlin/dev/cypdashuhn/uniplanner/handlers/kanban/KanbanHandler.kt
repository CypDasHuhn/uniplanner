package dev.cypdashuhn.uniplanner.handlers.kanban

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.common.booleanOrNull
import dev.cypdashuhn.uniplanner.handlers.common.textOrNull
import kotlinx.serialization.json.JsonPrimitive

object KanbanFields {
    val column = FieldKey("kanban.column")
    val order = FieldKey("kanban.order")
    val columns = FieldKey("kanban.columns")
}

private const val DONE = "done"
private const val TODO = "todo"

class KanbanHandler : Handler {
    override val namespace = "kanban"

    override fun ownedFields(): Set<FieldKey> = setOf(KanbanFields.column, KanbanFields.order, KanbanFields.columns)

    override fun subscribedFields(): Set<FieldKey> = setOf(KanbanFields.column, Std.statusClosed)

    override fun onFieldChanged(change: FieldChange, document: DocumentView): List<Mutation> = when (change.field) {
        KanbanFields.column -> columnChanged(change, document)
        Std.statusClosed -> closedChanged(change, document)
        else -> emptyList()
    }

    private fun columnChanged(change: FieldChange, document: DocumentView): List<Mutation> {
        val mutations = mutableListOf(Mutation(KanbanFields.order, null))
        val column = change.newValue.textOrNull()
        val wasDone = change.oldValue.textOrNull() == DONE

        when {
            column == DONE && document.booleanOrNull(Std.statusClosed) != true ->
                mutations += Mutation(Std.statusClosed, JsonPrimitive(true))

            wasDone && column != DONE && document.booleanOrNull(Std.statusClosed) != false ->
                mutations += Mutation(Std.statusClosed, JsonPrimitive(false))
        }
        return mutations
    }

    private fun closedChanged(change: FieldChange, document: DocumentView): List<Mutation> {
        val closed = change.newValue.booleanOrNull()
        val column = document.textOrNull(KanbanFields.column)

        return when {
            closed == true && column != DONE -> listOf(Mutation(KanbanFields.column, JsonPrimitive(DONE)))
            closed == false && column == DONE -> listOf(Mutation(KanbanFields.column, JsonPrimitive(TODO)))
            else -> emptyList()
        }
    }
}
