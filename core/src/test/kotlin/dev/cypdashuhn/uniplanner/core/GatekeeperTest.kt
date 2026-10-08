package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GatekeeperTest {
    private val name = FieldKey("std.name")
    private val status = FieldKey("std.status")
    private val column = FieldKey("kanban.column")

    @Test
    fun `applies a mutation to an unowned field`() {
        val document = TaskDocument("abc123", mapOf(name to JsonPrimitive("Fix login bug")))
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(
            document,
            MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))),
        )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("in-progress"), applied.document.fields[status])
        assertEquals(1, applied.changes.size)
    }

    @Test
    fun `rejects a mutation to a foreign owned field`() {
        val owner = FakeHandler("gantt", owned = setOf(FieldKey("gantt.start")), subscribed = emptySet())
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(owner)))
        val document = TaskDocument("abc123", emptyMap())

        val result = gatekeeper.apply(
            document,
            MutationRequest("kanban", listOf(Mutation(FieldKey("gantt.start"), JsonPrimitive("2026-04-01")))),
        )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `runs subscribed handlers transitively`() {
        val reactions = mapOf(status to listOf(Mutation(column, JsonPrimitive("doing"))))
        val kanban = FakeHandler("kanban", owned = setOf(column), subscribed = setOf(status), reactions = reactions)
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(kanban)))
        val document = TaskDocument("abc123", emptyMap())

        val result = gatekeeper.apply(
            document,
            MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))),
        )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("doing"), applied.document.fields[column])
        assertEquals(listOf(status, column), applied.changes.map { it.field })
    }

    @Test
    fun `rejects a circular handler chain`() {
        val start = FieldKey("gantt.start")
        val reactor = FakeHandler(
            "gantt",
            owned = setOf(start),
            subscribed = setOf(start),
            reactions = mapOf(start to listOf(Mutation(start, JsonPrimitive("2026-04-02")))),
        )
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(reactor)))
        val document = TaskDocument("abc123", emptyMap())

        val result = gatekeeper.apply(
            document,
            MutationRequest("gantt", listOf(Mutation(start, JsonPrimitive("2026-04-01")))),
        )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `null value removes the field`() {
        val document = TaskDocument("abc123", mapOf(status to JsonPrimitive("todo")))
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, null))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertTrue(status !in applied.document.fields)
    }
}

private class FakeHandler(
    override val namespace: String,
    private val owned: Set<FieldKey>,
    private val subscribed: Set<FieldKey>,
    private val reactions: Map<FieldKey, List<Mutation>> = emptyMap(),
) : Handler {
    override fun ownedFields(): Set<FieldKey> = owned

    override fun subscribedFields(): Set<FieldKey> = subscribed

    override fun onFieldChanged(change: FieldChange): List<Mutation> = reactions[change.field].orEmpty()
}
