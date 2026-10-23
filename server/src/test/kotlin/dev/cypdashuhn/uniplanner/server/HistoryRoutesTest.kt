package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.core.UplJson
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.server.history.HistoryConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRoutesTest {
    private val name = "std.name"

    @Test
    fun `history endpoints require authentication`() {
        withVault { vault ->
            withServer(vault) {
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/history").status)
                assertEquals(HttpStatusCode.Unauthorized, client.get("/api/history/days").status)
            }
        }
    }

    @Test
    fun `disabled history routes are unavailable`() {
        withVault { vault ->
            withServer(vault, history = HistoryConfig.defaults(vault).copy(enabled = false)) {
                assertEquals(HttpStatusCode.NotFound, client.get("/api/history") { authorize() }.status)
                assertEquals(HttpStatusCode.NotFound, client.get("/api/history/days") { authorize() }.status)
                assertFalse(Files.exists(vault.resolve(".upl-history")))
            }
        }
    }

    @Test
    fun `records an applied mutation with changes and document summary`() {
        withVault { vault ->
            withServer(vault) {
                val response = mutate("Renamed")

                assertEquals(HttpStatusCode.OK, response.status)
                val entries = entries()
                assertEquals(1, entries.size)
                val entry = entries.single()
                assertEquals("mutation", entry.string("op"))
                assertEquals("applied", entry.string("status"))
                assertEquals("task.upl.json", entry.string("path"))
                assertEquals("test", entry.string("principal"))
                assertEquals("test", entry.string("requester"))
                assertEquals("t1", entry.getValue("document").jsonObject.string("id"))
                assertEquals("1", entry.getValue("document").jsonObject.string("taskCount"))
                val change =
                    entry
                        .getValue("changes")
                        .jsonArray
                        .single()
                        .jsonObject
                assertEquals(name, change.string("field"))
                assertEquals("Task", change.string("oldValue"))
                assertEquals("Renamed", change.string("newValue"))
                assertFalse(entry.containsKey("reason"))
            }
        }
    }

    @Test
    fun `records a rejected mutation and leaves the document untouched`() {
        withVault { vault ->
            withServer(vault) {
                val response =
                    client.post("/api/mutation") {
                        parameter("path", "task.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "requester": "test",
                              "mutations": [
                                { "field": "std.name", "value": "Renamed", "target": "missing" }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                val entry = entries().single()
                assertEquals("rejected", entry.string("status"))
                assertEquals("target 'missing' is not allowed for a task document", entry.string("reason"))
                assertTrue(entry.getValue("changes").jsonArray.isEmpty())
                assertEquals("Task", storedName(vault))
            }
        }
    }

    @Test
    fun `records handler reactions in changes`() {
        withVault { vault ->
            Files.writeString(
                vault.resolve("task.upl.json"),
                """{"id":"t1","kind":"task","std.name":"Task","kanban.column":"todo"}""",
            )

            withServer(vault) {
                val response =
                    client.post("/api/mutation") {
                        parameter("path", "task.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "requester": "kanban",
                              "mutations": [
                                { "field": "kanban.column", "value": "done" }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val changes =
                    entries()
                        .single()
                        .getValue("changes")
                        .jsonArray
                        .map { it.jsonObject }
                assertEquals(listOf("kanban.column", "std.status.closed"), changes.map { it.string("field") })
                assertEquals("true", changes.last().string("newValue"))
            }
        }
    }

    @Test
    fun `records created task ids and removed task snapshots`() {
        withVault { vault ->
            Files.writeString(
                vault.resolve("week.upl.json"),
                """
                {
                  "id": "week",
                  "kind": "container",
                  "tasks": [
                    { "id": "t1", "std.name": "First" },
                    { "id": "t2", "std.name": "Second" }
                  ]
                }
                """.trimIndent(),
            )

            withServer(vault) {
                val response =
                    client.post("/api/mutation") {
                        parameter("path", "week.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody(
                            """
                            {
                              "requester": "test",
                              "mutations": [
                                { "field": "std.name", "value": "Changed", "target": "t1" }
                              ],
                              "createTasks": [
                                { "id": "t3", "fields": { "std.name": "New" } }
                              ],
                              "removeTasks": ["t2"]
                            }
                            """.trimIndent(),
                        )
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                val entry = entries().single()
                assertEquals(listOf("t3"), entry.getValue("createdTaskIds").jsonArray.map { it.jsonPrimitive.content })
                val removed =
                    entry
                        .getValue("removedTasks")
                        .jsonArray
                        .single()
                        .jsonObject
                assertEquals("t2", removed.string("id"))
                assertEquals("Second", removed.string("std.name"))
            }
        }
    }

    @Test
    fun `previews malformed requests auth failures and reads append nothing`() {
        withVault { vault ->
            withServer(vault) {
                val preview =
                    client.post("/api/preview") {
                        parameter("path", "task.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody(mutation("Preview"))
                    }
                assertEquals(HttpStatusCode.OK, preview.status)

                val malformed =
                    client.post("/api/mutation") {
                        parameter("path", "task.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody("{")
                    }
                assertEquals(HttpStatusCode.BadRequest, malformed.status)

                val unauthorized =
                    client.post("/api/mutation") {
                        parameter("path", "task.upl.json")
                        contentType(ContentType.Application.Json)
                        setBody(mutation("Anonymous"))
                    }
                assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

                client.get("/api/document") {
                    parameter("path", "task.upl.json")
                    authorize()
                }

                assertTrue(entries().isEmpty())
                assertTrue(days().isEmpty())
                assertTrue(Files.isDirectory(vault.resolve(".upl-history")))
            }
        }
    }

    @Test
    fun `replaces a corrupt existing document and records no before summary`() {
        withVault { vault ->
            Files.writeString(vault.resolve("week.upl.json"), "{")

            withServer(vault) {
                val response = replaceContainer("week.upl.json", "Repaired", "Task")

                assertEquals(HttpStatusCode.OK, response.status)
                val entry = entries().single()
                assertTrue(entry.getValue("replacement").jsonObject["before"] is JsonNull)
                assertEquals(
                    "week",
                    entry
                        .getValue("replacement")
                        .jsonObject
                        .getValue("after")
                        .jsonObject
                        .string("id"),
                )
            }
        }
    }

    @Test
    fun `rejected replacements append nothing`() {
        withVault { vault ->
            withServer(vault) {
                val response =
                    client.put("/api/document") {
                        parameter("path", "task.upl.json")
                        authorize()
                        contentType(ContentType.Application.Json)
                        setBody("""{"id":"task","kind":"task","std.name":"Task"}""")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(entries().isEmpty())
            }
        }
    }

    @Test
    fun `records document creation and replacement as replace entries`() {
        withVault { vault ->
            withServer(vault) {
                val created = replaceContainer("week.upl.json", "First week", "Initial task")
                assertEquals(HttpStatusCode.OK, created.status)

                val replaced = replaceContainer("week.upl.json", "Second week", "Changed task")
                assertEquals(HttpStatusCode.OK, replaced.status)

                val entries = entries()
                assertEquals(2, entries.size)

                val creation = entries.last()
                assertEquals("replace", creation.string("op"))
                assertEquals("applied", creation.string("status"))
                assertTrue(creation.getValue("replacement").jsonObject["before"] is JsonNull)
                assertEquals(
                    "week",
                    creation
                        .getValue("replacement")
                        .jsonObject
                        .getValue("after")
                        .jsonObject
                        .string("id"),
                )

                val replacement = entries.first()
                assertEquals(
                    "week",
                    replacement
                        .getValue("replacement")
                        .jsonObject
                        .getValue("before")
                        .jsonObject
                        .string("id"),
                )
                assertEquals(
                    "week",
                    replacement
                        .getValue("replacement")
                        .jsonObject
                        .getValue("after")
                        .jsonObject
                        .string("id"),
                )
                assertTrue(replacement.getValue("changes").jsonArray.isEmpty())
                assertFalse(replacement.containsKey("tasks"))
            }
        }
    }

    @Test
    fun `filters paginates and validates history queries`() {
        withVault { vault ->
            withServer(vault) {
                assertEquals(HttpStatusCode.OK, mutate("One").status)
                assertEquals(HttpStatusCode.OK, mutate("Two").status)
                assertEquals(HttpStatusCode.OK, mutate("Three").status)

                val firstPage = entries(limit = "2")
                assertEquals(listOf("Three", "Two"), firstPage.map { it.changeValue() })

                val secondPage = entries(limit = "2", before = firstPage.last().string("id"))
                assertEquals(listOf("One"), secondPage.map { it.changeValue() })

                assertEquals(3, entries(status = "applied").size)
                assertEquals(3, entries(op = "mutation").size)
                assertEquals(3, entries(requester = "test").size)
                assertTrue(entries(requester = "other").isEmpty())
                assertTrue(entries(path = "other.upl.json").isEmpty())
                assertTrue(entries(date = "1999-01-01").isEmpty())

                assertEquals(HttpStatusCode.BadRequest, historyResponse(status = "bogus").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(limit = "0").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(limit = "501").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(limit = "many").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(date = "yesterday").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(before = "missing").status)
                assertEquals(HttpStatusCode.BadRequest, historyResponse(path = "../outside.upl.json").status)
            }
        }
    }

    @Test
    fun `lists days with counts`() {
        withVault { vault ->
            withServer(vault) {
                assertEquals(HttpStatusCode.OK, mutate("One").status)
                assertEquals(HttpStatusCode.OK, mutate("Two").status)

                val days = days()
                assertEquals(1, days.size)
                val day = days.single().jsonObject
                assertEquals(2, day.string("count")?.toInt())
                assertEquals(day.string("date"), entries().first().string("timestamp")?.substringBefore('T'))
            }
        }
    }

    @Test
    fun `serializes concurrent mutations and appends in commit order`() {
        withVault { vault ->
            withServer(vault) {
                val names = (1..12).map { "Concurrent $it" }

                coroutineScope {
                    names
                        .map { value ->
                            async { mutate(value) }
                        }.awaitAll()
                }

                val entries = entries(limit = "100")
                assertEquals(names.size, entries.size)
                assertEquals(storedName(vault), entries.first().changeValue())
                assertEquals(names.size, entries.map { it.string("id") }.toSet().size)
            }
        }
    }

    @Test
    fun `a history append failure does not fail the document write`() {
        withVault { vault ->
            withServer(vault) {
                client.get("/health")
                Files.delete(vault.resolve(".upl-history"))
                Files.writeString(vault.resolve(".upl-history"), "not a directory")
                val response = mutate("Renamed")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Renamed", storedName(vault))
                assertTrue(entries().isEmpty())
            }
        }
    }

    private suspend fun ApplicationTestBuilder.mutate(value: String) =
        client.post("/api/mutation") {
            parameter("path", "task.upl.json")
            authorize()
            contentType(ContentType.Application.Json)
            setBody(mutation(value))
        }

    private suspend fun ApplicationTestBuilder.replaceContainer(
        path: String,
        containerName: String,
        taskName: String,
    ) = client.put("/api/document") {
        parameter("path", path)
        authorize()
        contentType(ContentType.Application.Json)
        setBody(
            """
            {
              "id": "week",
              "kind": "container",
              "std.name": "$containerName",
              "tasks": [
                { "id": "t1", "std.name": "$taskName" }
              ]
            }
            """.trimIndent(),
        )
    }

    private suspend fun ApplicationTestBuilder.entries(
        limit: String? = null,
        before: String? = null,
        status: String? = null,
        op: String? = null,
        requester: String? = null,
        path: String? = null,
        date: String? = null,
    ): List<JsonObject> {
        val response = historyResponse(limit, before, status, op, requester, path, date)
        assertEquals(HttpStatusCode.OK, response.status)
        return Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject
            .getValue("entries")
            .jsonArray
            .map { it.jsonObject }
    }

    private suspend fun ApplicationTestBuilder.historyResponse(
        limit: String? = null,
        before: String? = null,
        status: String? = null,
        op: String? = null,
        requester: String? = null,
        path: String? = null,
        date: String? = null,
    ) = client.get("/api/history") {
        authorize()
        limit?.let { parameter("limit", it) }
        before?.let { parameter("before", it) }
        status?.let { parameter("status", it) }
        op?.let { parameter("op", it) }
        requester?.let { parameter("requester", it) }
        path?.let { parameter("path", it) }
        date?.let { parameter("date", it) }
    }

    private suspend fun ApplicationTestBuilder.days(): List<JsonObject> {
        val response =
            client.get("/api/history/days") {
                authorize()
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json
            .parseToJsonElement(response.bodyAsText())
            .jsonObject
            .getValue("days")
            .jsonArray
            .map { it.jsonObject }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
    }

    private fun mutation(value: String): String =
        """
        {
          "requester": "test",
          "mutations": [
            { "field": "std.name", "value": "$value" }
          ]
        }
        """.trimIndent()

    private fun storedName(vault: Path): String? =
        UplJson
            .read(Files.readString(vault.resolve("task.upl.json")))
            .fields[FieldKey(name)]
            ?.jsonPrimitive
            ?.content

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun JsonObject.changeValue(): String? =
        getValue("changes")
            .jsonArray
            .single()
            .jsonObject
            .string("newValue")

    private fun withVault(block: (Path) -> Unit) {
        val vault = Files.createTempDirectory("upl-history-routes")
        Files.writeString(vault.resolve("task.upl.json"), """{"id":"t1","kind":"task","std.name":"Task"}""")
        block(vault)
    }
}
