package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UplRequestJsonTest {
    @Test
    fun `reads targetless field mutation`() {
        val request =
            UplRequestJson.read(
                """{"requester":"gantt","mutations":[{"field":"std.name","value":"Deep work"}]}""",
            )

        assertEquals("gantt", request.requester)
        assertEquals(FieldKey("std.name"), request.mutations.single().field)
        assertEquals(JsonPrimitive("Deep work"), request.mutations.single().value)
        assertNull(request.mutations.single().target)
    }

    @Test
    fun `reads mutation with target and explicit null value as removal`() {
        val request =
            UplRequestJson.read(
                """{"requester":"gantt","mutations":[{"field":"std.time.end","target":"t1","value":null}]}""",
            )

        assertEquals("t1", request.mutations.single().target)
        assertNull(request.mutations.single().value)
    }

    @Test
    fun `reads nested task fields`() {
        val request =
            UplRequestJson.read(
                """
                {
                  "requester": "gantt",
                  "createTasks": [
                    {"id": "t1", "fields": {"std.name": "Deep work", "std.time.duration": "PT2H"}}
                  ]
                }
                """,
            )

        val task = request.createTasks.single()
        assertEquals("t1", task.id)
        assertEquals(JsonPrimitive("Deep work"), task.fields[FieldKey("std.name")])
        assertEquals(JsonPrimitive("PT2H"), task.fields[FieldKey("std.time.duration")])
    }

    @Test
    fun `reads flat task fields`() {
        val request =
            UplRequestJson.read(
                """{"requester":"gantt","createTasks":[{"id":"t1","kind":"task","std.name":"Deep work"}]}""",
            )

        val task = request.createTasks.single()
        assertEquals(mapOf(FieldKey("std.name") to JsonPrimitive("Deep work")), task.fields)
    }

    @Test
    fun `reads task removals`() {
        val request = UplRequestJson.read("""{"requester":"gantt","removeTasks":["t1","t2"]}""")

        assertEquals(listOf("t1", "t2"), request.removeTasks)
    }

    @Test
    fun `rejects malformed task fields`() {
        assertFailsWith<IllegalArgumentException> {
            UplRequestJson.read("""{"requester":"gantt","createTasks":[{"id":"t1","fields":[]}]}""")
        }
    }
}
