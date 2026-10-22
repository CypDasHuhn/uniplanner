package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.NewTask
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GatekeeperTest {
    private val status = FieldKey("std.status")
    private val closed = FieldKey("std.status.closed")
    private val column = FieldKey("kanban.column")
    private val other = FieldKey("kanban.other")

    @Test
    fun `applies a mutation to an unowned field`() {
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))),
            )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("in-progress"), applied.document.fields[status])
        assertEquals(1, applied.changes.size)
    }

    @Test
    fun `rejects a mutation to a foreign owned field`() {
        val owner = FakeHandler("gantt", owned = setOf(FieldKey("gantt.start")))
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(owner)))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
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
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))),
            )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("doing"), applied.document.fields[column])
        assertEquals(listOf(status, column), applied.changes.map { it.field })
    }

    @Test
    fun `allows a multi-step derivation by one handler`() {
        val reactions =
            mapOf(
                status to listOf(Mutation(column, JsonPrimitive("doing"))),
                column to listOf(Mutation(other, JsonPrimitive("m"))),
            )
        val reactor = FakeHandler("kanban", owned = setOf(column, other), subscribed = setOf(status, column), reactions = reactions)
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(reactor)))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))),
            )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(listOf(status, column, other), applied.changes.map { it.field })
    }

    @Test
    fun `rejects a circular handler chain`() {
        val start = FieldKey("gantt.start")
        val reactor =
            FakeHandler(
                "gantt",
                owned = setOf(start),
                subscribed = setOf(start),
                reactions = mapOf(start to listOf(Mutation(start, JsonPrimitive("2026-04-02")))),
            )
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(reactor)))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("gantt", listOf(Mutation(start, JsonPrimitive("2026-04-01")))),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `skips no-op writes and their notifications`() {
        val recorder = FakeHandler("kanban", subscribed = setOf(status))
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(recorder)))
        val document = UplDocument("abc123", DocumentKind.TASK, mapOf(status to JsonPrimitive("todo")))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("todo")))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertTrue(applied.changes.isEmpty())
        assertTrue(recorder.invocations.isEmpty())
    }

    @Test
    fun `skips deleting an absent field`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, null))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertTrue(applied.changes.isEmpty())
    }

    @Test
    fun `rejects mutations to group keys`() {
        val owner = FakeHandler("gantt", owned = setOf(FieldKey("std.time.start")))
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(owner)))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("gantt", listOf(Mutation(FieldKey("std.time"), JsonPrimitive("nonsense")))),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `rejects mutations to core fields`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("kanban", listOf(Mutation(CoreFields.id, JsonPrimitive("other")))),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `rejects mutations to tasks`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result =
            gatekeeper.apply(
                document,
                MutationRequest("kanban", listOf(Mutation(CoreFields.tasks, JsonPrimitive("oops")))),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `null value removes the field`() {
        val document = UplDocument("abc123", DocumentKind.TASK, mapOf(status to JsonPrimitive("todo")))
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, null))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertTrue(status !in applied.document.fields)
    }

    @Test
    fun `applies a targeted mutation inside a container`() {
        val document = container()
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("todo"), "t1"))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("todo"), applied.document.task("t1")?.fields?.get(status))
        assertTrue(status !in applied.document.task("t2")!!.fields)
        assertEquals(listOf("t1"), applied.changes.map { it.target })
    }

    @Test
    fun `runs handler reactions against the targeted task`() {
        val reactor =
            FakeHandler(
                "kanban",
                owned = setOf(column),
                subscribed = setOf(status),
                reactions = mapOf(status to listOf(Mutation(column, JsonPrimitive("doing")))),
            )
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(reactor)))

        val result = gatekeeper.apply(container(), MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("todo"), "t1"))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("doing"), applied.document.task("t1")?.fields?.get(column))
        assertTrue(column !in applied.document.task("t2")!!.fields)
    }

    @Test
    fun `keeps container fields separate from task fields`() {
        val document =
            UplDocument(
                "week_01",
                DocumentKind.CONTAINER,
                mapOf(FieldKey("std.name") to JsonPrimitive("Week")),
                mapOf("t1" to UplDocument("t1", DocumentKind.TASK, emptyMap())),
            )
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(FieldKey("std.name"), JsonPrimitive("Renamed")))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("Renamed"), applied.document.fields[FieldKey("std.name")])
        assertTrue(FieldKey("std.name") !in applied.document.task("t1")!!.fields)
    }

    @Test
    fun `rejects a target on a single document`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("todo"), "t1"))))

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `rejects an unknown target atomically`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result =
            gatekeeper.apply(
                container(),
                MutationRequest(
                    "kanban",
                    listOf(Mutation(status, JsonPrimitive("todo"), "t1"), Mutation(status, JsonPrimitive("todo"), "missing")),
                ),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `creates a task in a container`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result =
            gatekeeper.apply(
                container(),
                MutationRequest(
                    "kanban",
                    emptyList(),
                    createTasks = listOf(NewTask("t3", mapOf(status to JsonPrimitive("todo")))),
                ),
            )

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals(JsonPrimitive("todo"), applied.document.task("t3")?.fields?.get(status))
        assertEquals(listOf("t3"), applied.changes.map { it.target })
    }

    @Test
    fun `removes a task from a container`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(container(), MutationRequest("kanban", emptyList(), removeTasks = listOf("t1")))

        val applied = assertIs<MutationResult.Applied>(result)
        assertTrue("t1" !in applied.document.tasks)
        assertTrue("t2" in applied.document.tasks)
    }

    @Test
    fun `rejects task creation outside a container`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))
        val document = UplDocument("abc123", DocumentKind.TASK, emptyMap())

        val result = gatekeeper.apply(document, MutationRequest("kanban", emptyList(), createTasks = listOf(NewTask("t1"))))

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `rejects creating a duplicate task id`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result = gatekeeper.apply(container(), MutationRequest("kanban", emptyList(), createTasks = listOf(NewTask("t1"))))

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `rejects a batch that creates a task and then fails`() {
        val gatekeeper = Gatekeeper(HandlerRegistry(emptyList()))

        val result =
            gatekeeper.apply(
                container(),
                MutationRequest(
                    "kanban",
                    listOf(Mutation(status, JsonPrimitive("todo"), "missing")),
                    createTasks = listOf(NewTask("t3")),
                ),
            )

        assertIs<MutationResult.Rejected>(result)
    }

    @Test
    fun `exposes current fields to handlers`() {
        val view =
            FakeHandler(
                "kanban",
                subscribed = setOf(status),
                reactions =
                    mapOf(status to listOf(Mutation(column, JsonPrimitive("doing")))),
            )
        val gatekeeper = Gatekeeper(HandlerRegistry(listOf(view)))
        val document = UplDocument("abc123", DocumentKind.TASK, mapOf(column to JsonPrimitive("todo")))

        val result = gatekeeper.apply(document, MutationRequest("kanban", listOf(Mutation(status, JsonPrimitive("in-progress")))))

        val applied = assertIs<MutationResult.Applied>(result)
        assertEquals("todo", view.seenColumn)
        assertEquals(JsonPrimitive("doing"), applied.document.fields[column])
    }
}

private fun container(): UplDocument =
    UplDocument(
        "week_01",
        DocumentKind.CONTAINER,
        emptyMap(),
        mapOf(
            "t1" to UplDocument("t1", DocumentKind.TASK, emptyMap()),
            "t2" to UplDocument("t2", DocumentKind.TASK, emptyMap()),
        ),
    )

private class FakeHandler(
    override val namespace: String,
    private val owned: Set<FieldKey> = emptySet(),
    private val subscribed: Set<FieldKey> = emptySet(),
    private val reactions: Map<FieldKey, List<Mutation>> = emptyMap(),
) : Handler {
    val invocations = mutableListOf<FieldKey>()
    var seenColumn: String? = null

    override fun ownedFields(): Set<FieldKey> = owned

    override fun subscribedFields(): Set<FieldKey> = subscribed

    override fun onFieldChanged(
        change: FieldChange,
        document: DocumentView,
    ): List<Mutation> {
        invocations += change.field
        seenColumn = (document.value(FieldKey("kanban.column")) as? JsonPrimitive)?.content
        return reactions[change.field].orEmpty()
    }
}
