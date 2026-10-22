package dev.cypdashuhn.uniplanner.handlers

import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
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

    @Test
    fun `creating a container task derives its end`() {
        val container = UplDocument("week_01", DocumentKind.CONTAINER, emptyMap())
        val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))
        val newTask =
            NewTask(
                "t1",
                mapOf(
                    Std.timeStart to JsonPrimitive("2026-04-01T08:00:00Z"),
                    Std.timeDuration to JsonPrimitive("PT2H"),
                ),
            )

        val result = gatekeeper.apply(container, MutationRequest("gantt", emptyList(), createTasks = listOf(newTask)))

        val applied = result.applied()
        assertEquals(JsonPrimitive("2026-04-01T10:00:00Z"), applied.task("t1")?.fields?.get(Std.timeEnd))
    }
}
