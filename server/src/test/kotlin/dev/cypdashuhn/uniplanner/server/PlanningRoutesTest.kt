package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.core.UplJson
import dev.cypdashuhn.uniplanner.handler.FieldKey
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanningRoutesTest {
    private val name = FieldKey("std.name")

    @Test
    fun `requires authentication`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.get("/api/document") {
                    parameter("path", "task.upl.json")
                }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }
    }

    @Test
    fun `reads a document`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.get("/api/document") {
                    parameter("path", "task.upl.json")
                    authorize()
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals("t1", body["id"]?.jsonPrimitive?.content)
                assertEquals("Task", body["std.name"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `reads all tags from a directory`() {
        withVault { vault ->
            val planner = Files.createDirectory(vault.resolve("planner"))
            Files.writeString(
                planner.resolve("week.upl.json"),
                """
                {
                  "id": "week",
                  "kind": "container",
                  "tasks": [
                    { "id": "t1", "std.tags": ["#work", "home"] },
                    { "id": "t2", "std.tags": ["work"] }
                  ]
                }
                """.trimIndent(),
            )
            Files.writeString(planner.resolve("task.upl.json"), """{"id":"t3","kind":"task","std.tags":["errand"]}""")
            Files.writeString(planner.resolve("workspace.upl.json"), """{"id":"workspace","kind":"workspace"}""")

            withServer(vault) {
                val response = client.get("/api/tags") {
                    parameter("path", "planner")
                    authorize()
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val tags = Json.parseToJsonElement(response.bodyAsText()).jsonObject["tags"]?.jsonArray?.map { it.jsonPrimitive.content }
                assertEquals(listOf("errand", "home", "work"), tags)
            }
        }
    }

    @Test
    fun `replaces a container document`() {
        withVault { vault ->
            Files.writeString(vault.resolve("week.upl.json"), """{"id":"week","kind":"container","tasks":[]}""")
            withServer(vault) {
                val response = client.put("/api/document") {
                    parameter("path", "week.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "week",
                          "kind": "container",
                          "std.name": "Imported",
                          "tasks": [
                            { "id": "t1", "std.name": "Imported task" }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals("week", body["document"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
                assertEquals(
                    "Imported task",
                    UplJson.read(Files.readString(vault.resolve("week.upl.json")))
                        .tasks
                        .getValue("t1")
                        .fields[name]
                        ?.jsonPrimitive
                        ?.content,
                )
            }
        }
    }

    @Test
    fun `creates a missing container document`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.put("/api/document") {
                    parameter("path", "new-week.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "new-week",
                          "kind": "container",
                          "std.name": "New week",
                          "tasks": []
                        }
                        """.trimIndent(),
                    )
                }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    "New week",
                    UplJson.read(Files.readString(vault.resolve("new-week.upl.json")))
                        .fields[name]
                        ?.jsonPrimitive
                        ?.content,
                )
            }
        }
    }

    @Test
    fun `replaces a workspace document`() {
        withVault { vault ->
            Files.writeString(vault.resolve("workspace.upl.json"), """{"id":"workspace","kind":"workspace"}""")
            withServer(vault) {
                val response = client.put("/api/document") {
                    parameter("path", "workspace.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "id": "workspace",
                          "kind": "workspace",
                          "std.name": "Planner",
                          "std.tag.colors": {
                            "work": { "text": "#111111", "background": "#222222" }
                          }
                        }
                        """.trimIndent(),
                    )
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val document = UplJson.read(Files.readString(vault.resolve("workspace.upl.json")))
                val colors = document.fields[FieldKey("std.tag.colors")]?.jsonObject
                assertEquals("workspace", document.id)
                assertEquals("Planner", document.fields[name]?.jsonPrimitive?.content)
                assertEquals("#111111", colors?.get("work")?.jsonObject?.get("text")?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `creates a missing workspace document`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.put("/api/document") {
                    parameter("path", "workspace.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"workspace","kind":"workspace","std.name":"Planner"}""")
                }

                val document = UplJson.read(Files.readString(vault.resolve("workspace.upl.json")))
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Planner", document.fields[name]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `writes workspace config through a mutation`() {
        withVault { vault ->
            Files.writeString(vault.resolve("workspace.upl.json"), """{"id":"workspace","kind":"workspace"}""")
            withServer(vault) {
                val response = client.post("/api/mutation") {
                    parameter("path", "workspace.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "requester": "weekplanner",
                          "mutations": [
                            {
                              "field": "std.tag.colors",
                              "value": { "work": { "text": "#111111", "background": "#222222" } }
                            }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                val document = UplJson.read(Files.readString(vault.resolve("workspace.upl.json")))
                val colors = document.fields[FieldKey("std.tag.colors")]?.jsonObject
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("#222222", colors?.get("work")?.jsonObject?.get("background")?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun `rejects replacing with a task document`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.put("/api/document") {
                    parameter("path", "task.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"task","kind":"task","std.name":"Task"}""")
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("Task", storedName(vault))
            }
        }
    }

    @Test
    fun `applies and stores a mutation`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.post("/api/mutation") {
                    parameter("path", "task.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(mutation("Renamed"))
                }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("Renamed", storedName(vault))
            }
        }
    }

    @Test
    fun `previews without writing`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.post("/api/preview") {
                    parameter("path", "task.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(mutation("Renamed"))
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val document = body["document"]?.jsonObject
                assertEquals("Renamed", document?.get("std.name")?.jsonPrimitive?.content)
                assertEquals("Task", storedName(vault))
            }
        }
    }

    @Test
    fun `rejects an invalid mutation`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.post("/api/mutation") {
                    parameter("path", "task.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "requester": "test",
                          "mutations": [
                            { "field": "std.name", "value": "Renamed", "target": "t1" }
                          ]
                        }
                        """.trimIndent(),
                    )
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("Task", storedName(vault))
            }
        }
    }

    @Test
    fun `rejects invalid paths and missing files`() {
        withVault { vault ->
            withServer(vault) {
                val traversal = client.get("/api/document") {
                    parameter("path", "../outside.upl.json")
                    authorize()
                }
                assertEquals(HttpStatusCode.BadRequest, traversal.status)

                val missing = client.get("/api/document") {
                    parameter("path", "missing.upl.json")
                    authorize()
                }
                assertEquals(HttpStatusCode.NotFound, missing.status)

                val writeTraversal = client.put("/api/document") {
                    parameter("path", "../outside.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"outside","kind":"container","tasks":[]}""")
                }
                assertEquals(HttpStatusCode.BadRequest, writeTraversal.status)
            }
        }
    }

    @Test
    fun `rejects malformed mutation JSON`() {
        withVault { vault ->
            withServer(vault) {
                val response = client.post("/api/mutation") {
                    parameter("path", "task.upl.json")
                    authorize()
                    contentType(ContentType.Application.Json)
                    setBody("{")
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authorize() {
        header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
    }

    private fun mutation(name: String): String =
        """
        {
          "requester": "test",
          "mutations": [
            { "field": "std.name", "value": "$name" }
          ]
        }
        """.trimIndent()

    private fun storedName(vault: Path): String? =
        UplJson.read(Files.readString(vault.resolve("task.upl.json"))).fields[name]?.jsonPrimitive?.content

    private fun withVault(block: (Path) -> Unit) {
        val vault = Files.createTempDirectory("upl-planning-test")
        Files.writeString(vault.resolve("task.upl.json"), """{"id":"t1","kind":"task","std.name":"Task"}""")
        block(vault)
    }
}
