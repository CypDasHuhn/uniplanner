package dev.cypdashuhn.uniplanner.handlers.dayplanner

import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.applied
import dev.cypdashuhn.uniplanner.handlers.apply
import dev.cypdashuhn.uniplanner.handlers.task
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayplannerHandlerTest {
    @Test
    fun `closing clears the day and order`() {
        val document =
            task(
                DayplannerFields.day to JsonPrimitive("2026-04-08"),
                DayplannerFields.order to JsonPrimitive("m"),
            )

        val applied = apply(DayplannerHandler(), document, Mutation(Std.statusClosed, JsonPrimitive(true))).applied()

        assertTrue(DayplannerFields.day !in applied.fields)
        assertTrue(DayplannerFields.order !in applied.fields)
    }

    @Test
    fun `reopening does not re-place the task`() {
        val document = task(DayplannerFields.day to JsonPrimitive("2026-04-08"))

        val applied = apply(DayplannerHandler(), document, Mutation(Std.statusClosed, JsonPrimitive(false))).applied()

        assertEquals(JsonPrimitive("2026-04-08"), applied.fields[DayplannerFields.day])
    }

    @Test
    fun `changing the day clears order`() {
        val document =
            task(
                DayplannerFields.day to JsonPrimitive("2026-04-08"),
                DayplannerFields.order to JsonPrimitive("m"),
            )

        val applied = apply(DayplannerHandler(), document, Mutation(DayplannerFields.day, JsonPrimitive("2026-04-09"))).applied()

        assertEquals(JsonPrimitive("2026-04-09"), applied.fields[DayplannerFields.day])
        assertTrue(DayplannerFields.order !in applied.fields)
    }
}
