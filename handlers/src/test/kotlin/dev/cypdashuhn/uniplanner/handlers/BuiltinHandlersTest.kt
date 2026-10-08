package dev.cypdashuhn.uniplanner.handlers

import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.dayplanner.DayplannerFields
import dev.cypdashuhn.uniplanner.handlers.kanban.KanbanFields
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinHandlersTest {
    @Test
    fun `builtin handlers register without conflicts`() {
        HandlerRegistry(BuiltinHandlers.all())
    }

    @Test
    fun `closing a task propagates to kanban and dayplanner`() {
        val document =
            task(
                KanbanFields.column to JsonPrimitive("doing"),
                KanbanFields.order to JsonPrimitive("m"),
                DayplannerFields.day to JsonPrimitive("2026-04-08"),
                DayplannerFields.order to JsonPrimitive("m"),
            )
        val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(Std.statusClosed, JsonPrimitive(true)))))

        val applied = result.applied()
        assertEquals(JsonPrimitive("done"), applied.fields[KanbanFields.column])
        assertTrue(DayplannerFields.day !in applied.fields)
        assertTrue(DayplannerFields.order !in applied.fields)
    }
}
