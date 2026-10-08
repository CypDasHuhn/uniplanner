package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskFileTest {
    @Test
    fun `round trips a document through disk`() {
        val directory = Files.createTempDirectory("uniplanner")
        val path = directory.resolve("task.upl.json")
        val document = TaskDocument(
            "abc123",
            mapOf(
                FieldKey("std.name") to JsonPrimitive("Fix login bug"),
                FieldKey("kanban.column") to JsonPrimitive("doing"),
            ),
        )

        val file = TaskFile(path)
        file.write(document)

        assertEquals(document, file.read())
    }
}
