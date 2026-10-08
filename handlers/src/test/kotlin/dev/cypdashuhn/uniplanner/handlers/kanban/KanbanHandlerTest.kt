package dev.cypdashuhn.uniplanner.handlers.kanban

import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.applied
import dev.cypdashuhn.uniplanner.handlers.apply
import dev.cypdashuhn.uniplanner.handlers.task
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KanbanHandlerTest {
    @Test
    fun `moving to done closes the task and clears order`() {
        val document = task(KanbanFields.column to JsonPrimitive("doing"), KanbanFields.order to JsonPrimitive("m"))

        val applied = apply(KanbanHandler(), document, Mutation(KanbanFields.column, JsonPrimitive("done"))).applied()

        assertEquals(JsonPrimitive(true), applied.fields[Std.statusClosed])
        assertTrue(KanbanFields.order !in applied.fields)
    }

    @Test
    fun `moving out of done reopens the task`() {
        val document = task(KanbanFields.column to JsonPrimitive("done"), Std.statusClosed to JsonPrimitive(true))

        val applied = apply(KanbanHandler(), document, Mutation(KanbanFields.column, JsonPrimitive("doing"))).applied()

        assertEquals(JsonPrimitive(false), applied.fields[Std.statusClosed])
    }

    @Test
    fun `moving between open columns leaves closed untouched`() {
        val document = task(KanbanFields.column to JsonPrimitive("todo"), Std.statusClosed to JsonPrimitive(false))

        val applied = apply(KanbanHandler(), document, Mutation(KanbanFields.column, JsonPrimitive("doing"))).applied()

        assertEquals(JsonPrimitive(false), applied.fields[Std.statusClosed])
    }

    @Test
    fun `closing from outside the board moves the card to done`() {
        val document = task(KanbanFields.column to JsonPrimitive("doing"))

        val applied = apply(KanbanHandler(), document, Mutation(Std.statusClosed, JsonPrimitive(true))).applied()

        assertEquals(JsonPrimitive("done"), applied.fields[KanbanFields.column])
    }

    @Test
    fun `reopening a done card moves it back to todo`() {
        val document = task(KanbanFields.column to JsonPrimitive("done"), Std.statusClosed to JsonPrimitive(true))

        val applied = apply(KanbanHandler(), document, Mutation(Std.statusClosed, JsonPrimitive(false))).applied()

        assertEquals(JsonPrimitive("todo"), applied.fields[KanbanFields.column])
    }

    @Test
    fun `owning the workspace column config`() {
        assertTrue(KanbanFields.columns in KanbanHandler().ownedFields())
    }
}
