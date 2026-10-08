package dev.cypdashuhn.uniplanner.handlers.gantt

import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.applied
import dev.cypdashuhn.uniplanner.handlers.apply
import dev.cypdashuhn.uniplanner.handlers.task
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GanttHandlerTest {
    @Test
    fun `duration change derives the end date`() {
        val document = task(Std.timeStart to JsonPrimitive("2026-04-01"), Std.timeDuration to JsonPrimitive("P3D"))

        val applied = apply(GanttHandler(), document, Mutation(Std.timeDuration, JsonPrimitive("P5D"))).applied()

        assertEquals(JsonPrimitive("2026-04-06"), applied.fields[Std.timeEnd])
    }

    @Test
    fun `start change re-derives the end date`() {
        val document = task(
            Std.timeStart to JsonPrimitive("2026-04-01"),
            Std.timeDuration to JsonPrimitive("P3D"),
            Std.timeEnd to JsonPrimitive("2026-04-04"),
        )

        val applied = apply(GanttHandler(), document, Mutation(Std.timeStart, JsonPrimitive("2026-04-10"))).applied()

        assertEquals(JsonPrimitive("2026-04-13"), applied.fields[Std.timeEnd])
    }

    @Test
    fun `end change derives the duration`() {
        val document = task(
            Std.timeStart to JsonPrimitive("2026-04-01"),
            Std.timeEnd to JsonPrimitive("2026-04-04"),
        )

        val applied = apply(GanttHandler(), document, Mutation(Std.timeEnd, JsonPrimitive("2026-04-10"))).applied()

        assertEquals(JsonPrimitive("P9D"), applied.fields[Std.timeDuration])
    }

    @Test
    fun `no reaction without both operands`() {
        val document = task(Std.timeStart to JsonPrimitive("2026-04-01"))

        val applied = apply(GanttHandler(), document, Mutation(Std.timeStart, JsonPrimitive("2026-04-02"))).applied()

        assertTrue(Std.timeEnd !in applied.fields)
        assertTrue(Std.timeDuration !in applied.fields)
    }

    @Test
    fun `no reaction when the derived end is unchanged`() {
        val document = task(
            Std.timeStart to JsonPrimitive("2026-04-01"),
            Std.timeDuration to JsonPrimitive("P3D"),
            Std.timeEnd to JsonPrimitive("2026-04-04"),
        )
        val equivalent = JsonPrimitive("PT72H")

        val applied = apply(GanttHandler(), document, Mutation(Std.timeDuration, equivalent)).applied()

        assertEquals(equivalent, applied.fields[Std.timeDuration])
        assertEquals(JsonPrimitive("2026-04-04"), applied.fields[Std.timeEnd])
    }

    @Test
    fun `keeps progress as a plain owned field`() {
        val document = task()

        val applied = apply(GanttHandler(), document, Mutation(GanttFields.progress, JsonPrimitive(40))).applied()

        assertEquals(JsonPrimitive(40), applied.fields[GanttFields.progress])
    }
}
