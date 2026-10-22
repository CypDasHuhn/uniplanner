package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.AuthPrincipal
import dev.cypdashuhn.uniplanner.auth.http.AuthRequest
import dev.cypdashuhn.uniplanner.auth.http.HttpAuth
import dev.cypdashuhn.uniplanner.auth.http.bearerToken
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Application.authRoutes(httpAuth: HttpAuth) {
    routing {
        get("/health") {
            call.respondText("ok")
        }

        post("/auth/login") {
            val token = call.loginToken()
            val login = httpAuth.login(token)
            if (login == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            call.response.header(HttpHeaders.SetCookie, login.setCookie)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/auth/logout") {
            val cookie = httpAuth.logout(call.request.headers[HttpHeaders.Cookie])
            call.response.header(HttpHeaders.SetCookie, cookie)
            call.respond(HttpStatusCode.NoContent)
        }

        get("/auth/me") {
            val principal = call.requirePrincipal(httpAuth) ?: return@get
            call.respondText(principalJson(principal), ContentType.Application.Json)
        }
    }
}

private suspend fun ApplicationCall.loginToken(): String? {
    val form = request.contentType().withoutParameters() == ContentType.Application.FormUrlEncoded
    return if (form) receiveParameters()["token"] else bearerToken(request.headers[HttpHeaders.Authorization])
}

internal suspend fun ApplicationCall.requirePrincipal(httpAuth: HttpAuth): AuthPrincipal? {
    val principal = httpAuth.authenticate(authRequest())
    if (principal == null) {
        response.header(HttpHeaders.WWWAuthenticate, "Bearer")
        respond(HttpStatusCode.Unauthorized)
    }
    return principal
}

private fun ApplicationCall.authRequest(): AuthRequest =
    AuthRequest(
        authorization = request.headers[HttpHeaders.Authorization],
        cookie = request.headers[HttpHeaders.Cookie],
    )

private fun principalJson(principal: AuthPrincipal): String =
    buildJsonObject {
        put("id", principal.id)
        put(
            "attributes",
            buildJsonObject {
                principal.attributes.forEach { (key, value) -> put(key, value) }
            },
        )
    }.toString()
