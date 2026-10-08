package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `defaults missing kind to task`() {
        val path = Files.createTempDirectory("uniplanner").resolve("task.upl.json")
        Files.writeString(path, """{"id":"abc123","std.name":"Fix login bug"}""")

        val document = UplFile(path).read()

        assertEquals(DocumentKind.TASK, document.kind)
        assertEquals(mapOf(FieldKey("std.name") to JsonPrimitive("Fix login bug")), document.fields)
    }

    private fun writeThrough(document: UplDocument): Path {
        val path = Files.createTempDirectory("uniplanner").resolve("doc.upl.json")
        UplFile(path).write(document)
        return path
    }
}
