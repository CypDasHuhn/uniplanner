package dev.cypdashuhn.uniplanner.handlers.dayplanner

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.common.booleanOrNull

object DayplannerFields {
    val day = FieldKey("dayplanner.day")
    val order = FieldKey("dayplanner.order")
}

class DayplannerHandler : Handler {
    override val namespace = "dayplanner"

    override fun ownedFields(): Set<FieldKey> = setOf(DayplannerFields.day, DayplannerFields.order)

    override fun subscribedFields(): Set<FieldKey> = setOf(Std.statusClosed, DayplannerFields.day)

    override fun onFieldChanged(change: FieldChange, document: DocumentView): List<Mutation> = when (change.field) {
        DayplannerFields.day -> clearOrder()
        Std.statusClosed -> if (change.newValue.booleanOrNull() == true) clearPlacement() else emptyList()
        else -> emptyList()
    }

    private fun clearOrder(): List<Mutation> = listOf(Mutation(DayplannerFields.order, null))

    private fun clearPlacement(): List<Mutation> =
        listOf(Mutation(DayplannerFields.day, null), Mutation(DayplannerFields.order, null))
}
