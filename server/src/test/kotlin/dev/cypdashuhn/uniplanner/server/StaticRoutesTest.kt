package dev.cypdashuhn.uniplanner.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticRoutesTest {
    @Test
    fun `serves the app at root`() {
        withStatic { root ->
            withServer(staticRoot = root) {
                val response = client.get("/")

                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("planner-root"))
            }
        }
    }

    @Test
    fun `serves assets from the static root`() {
        withStatic { root ->
            withServer(staticRoot = root) {
                val response = client.get("/assets/app.js")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("console.log('planner')", response.bodyAsText())
            }
        }
    }

    @Test
    fun `falls back to the app for client routes`() {
        withStatic { root ->
            withServer(staticRoot = root) {
                val response = client.get("/week/2026-W43")

                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.bodyAsText().contains("planner-root"))
            }
        }
    }

    @Test
    fun `does not serve the app for unknown api routes`() {
        withStatic { root ->
            withServer(staticRoot = root) {
                val response = client.get("/api/missing")

                assertEquals(HttpStatusCode.NotFound, response.status)
            }
        }
    }

    @Test
    fun `keeps server routes ahead of the app`() {
        val vault = Files.createTempDirectory("upl-static-vault")
        Files.writeString(vault.resolve("task.upl.json"), """{"id":"t1","kind":"task","std.name":"Task"}""")
        withStatic { root ->
            withServer(vault, root) {
                val health = client.get("/health")
                assertEquals(HttpStatusCode.OK, health.status)
                assertEquals("ok", health.bodyAsText())

                val document = client.get("/api/document") {
                    parameter("path", "task.upl.json")
                    header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
                }
                assertEquals(HttpStatusCode.OK, document.status)
                assertEquals(
                    "t1",
                    Json.parseToJsonElement(document.bodyAsText()).jsonObject["id"]?.jsonPrimitive?.content,
                )
            }
        }
    }

    private fun withStatic(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("upl-static")
        Files.createDirectories(root.resolve("assets"))
        Files.writeString(root.resolve("index.html"), "<html>planner-root</html>")
        Files.writeString(root.resolve("assets/app.js"), "console.log('planner')")
        block(root)
    }
}
