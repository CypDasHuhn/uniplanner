package dev.cypdashuhn.uniplanner.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {
    @Test
    fun `health is public`() {
        withServer {
            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }
    }

    @Test
    fun `me accepts bearer token`() {
        withServer {
            val response = client.get("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("test", body["id"]?.jsonPrimitive?.content)
            assertEquals(
                "personal",
                body["attributes"]?.jsonObject?.get("vault")?.jsonPrimitive?.content,
            )
        }
    }

    @Test
    fun `me rejects anonymous request`() {
        withServer {
            val response = client.get("/auth/me")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("Bearer", response.headers[HttpHeaders.WWWAuthenticate])
        }
    }

    @Test
    fun `login issues session cookie`() {
        withServer {
            val login = login()
            val setCookie = assertNotNull(login.headers[HttpHeaders.SetCookie])

            assertEquals(HttpStatusCode.NoContent, login.status)
            assertTrue(setCookie.contains("HttpOnly"))
            assertTrue(setCookie.contains("SameSite=Lax"))

            val me = client.get("/auth/me") {
                header(HttpHeaders.Cookie, setCookie.substringBefore(';'))
            }

            assertEquals(HttpStatusCode.OK, me.status)
        }
    }

    @Test
    fun `login accepts bearer header`() {
        withServer {
            val response = client.post("/auth/login") {
                header(HttpHeaders.Authorization, "Bearer $TEST_TOKEN")
            }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertNotNull(response.headers[HttpHeaders.SetCookie])
        }
    }

    @Test
    fun `login rejects invalid token`() {
        withServer {
            val response = client.post("/auth/login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("token=wrong")
            }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `logout revokes session`() {
        withServer {
            val setCookie = assertNotNull(login().headers[HttpHeaders.SetCookie])
            val sessionCookie = setCookie.substringBefore(';')

            val logout = client.post("/auth/logout") {
                header(HttpHeaders.Cookie, sessionCookie)
            }

            assertEquals(HttpStatusCode.NoContent, logout.status)
            assertTrue(assertNotNull(logout.headers[HttpHeaders.SetCookie]).contains("Max-Age=0"))

            val me = client.get("/auth/me") {
                header(HttpHeaders.Cookie, sessionCookie)
            }

            assertEquals(HttpStatusCode.Unauthorized, me.status)
        }
    }

    private suspend fun ApplicationTestBuilder.login() =
        client.post("/auth/login") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("token=$TEST_TOKEN")
        }
}
