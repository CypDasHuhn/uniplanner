package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UplFileTest {
    @Test
    fun `round trips a task document through disk`() {
        val document =
            UplDocument(
                "abc123",
                DocumentKind.TASK,
                mapOf(
                    FieldKey("std.name") to JsonPrimitive("Fix login bug"),
                    FieldKey("kanban.column") to JsonPrimitive("doing"),
                ),
            )

        val path = writeThrough(document)

        assertEquals(document, UplFile(path).read())
    }

    @Test
    fun `round trips a workspace document with kind`() {
        val document =
            UplDocument(
                "wsp_01",
                DocumentKind.WORKSPACE,
                mapOf(
                    FieldKey("std.name") to JsonPrimitive("My Board"),
                    FieldKey("kanban.columns") to JsonArray(listOf(JsonPrimitive("todo"), JsonPrimitive("doing"), JsonPrimitive("done"))),
                ),
            )

        val path = writeThrough(document)

        assertEquals(document, UplFile(path).read())
    }

    @Test
    fun `round trips a container with task array`() {
        val document =
            UplDocument(
                "week_01",
                DocumentKind.CONTAINER,
                mapOf(FieldKey("std.name") to JsonPrimitive("Week 1")),
                mapOf(
                    "t1" to UplDocument("t1", DocumentKind.TASK, mapOf(FieldKey("std.name") to JsonPrimitive("First"))),
                    "t2" to UplDocument("t2", DocumentKind.TASK, mapOf(FieldKey("std.name") to JsonPrimitive("Second"))),
                ),
            )

        val path = writeThrough(document)

        assertEquals(document, UplFile(path).read())
    }

    @Test
    fun `reads a container with task object`() {
        val path = Files.createTempDirectory("uniplanner").resolve("container.upl.json")
        Files.writeString(
            path,
            """{"id":"week_01","kind":"container","tasks":{"t1":{"std.name":"First"},"t2":{"std.name":"Second"}}}""",
        )

        val document = UplFile(path).read()

        assertEquals(DocumentKind.CONTAINER, document.kind)
        assertEquals(setOf("t1", "t2"), document.tasks.keys)
        assertEquals(JsonPrimitive("First"), document.tasks.getValue("t1").fields[FieldKey("std.name")])
    }

    @Test
    fun `detects a container from the tasks key`() {
        val path = Files.createTempDirectory("uniplanner").resolve("container.upl.json")
        Files.writeString(path, """{"id":"week_01","tasks":[{"id":"t1","std.name":"First"}]}""")

        val document = UplFile(path).read()

        assertEquals(DocumentKind.CONTAINER, document.kind)
        assertEquals(setOf("t1"), document.tasks.keys)
    }

    @Test
    fun `defaults missing kind to task`() {
        val path = Files.createTempDirectory("uniplanner").resolve("task.upl.json")
        Files.writeString(path, """{"id":"abc123","std.name":"Fix login bug"}""")

        val document = UplFile(path).read()

        assertEquals(DocumentKind.TASK, document.kind)
        assertEquals(mapOf(FieldKey("std.name") to JsonPrimitive("Fix login bug")), document.fields)
    }

    @Test
    fun `writes an explicit task kind`() {
        val path = writeThrough(UplDocument("t1", DocumentKind.TASK, emptyMap()))

        assertTrue("\"kind\":\"task\"" in Files.readString(path))
    }

    @Test
    fun `rejects a mismatched task map key`() {
        assertFailsWith<IllegalArgumentException> {
            UplDocument(
                "c",
                DocumentKind.CONTAINER,
                emptyMap(),
                mapOf("t1" to UplDocument("other", DocumentKind.TASK, emptyMap())),
            )
        }
    }

    @Test
    fun `rejects core fields in the field map`() {
        listOf(CoreFields.id, CoreFields.kind, CoreFields.tasks).forEach { field ->
            assertFailsWith<IllegalArgumentException> {
                UplDocument("t1", DocumentKind.TASK, mapOf(field to JsonPrimitive("oops")))
            }
        }
    }

    private fun writeThrough(document: UplDocument): Path {
        val path = Files.createTempDirectory("uniplanner").resolve("doc.upl.json")
        UplFile(path).write(document)
        return path
    }
}
