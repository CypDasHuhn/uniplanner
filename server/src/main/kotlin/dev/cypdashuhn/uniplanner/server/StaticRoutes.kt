package dev.cypdashuhn.uniplanner.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.nio.file.Path

fun Application.staticRoutes(root: Path) {
    routing {
        for (prefix in listOf("api", "auth")) {
            route("/$prefix") { handle { call.respond(HttpStatusCode.NotFound) } }
            route("/$prefix/{...}") { handle { call.respond(HttpStatusCode.NotFound) } }
        }
        singlePageApplication {
            filesPath = root.toString()
            defaultPage = "index.html"
            applicationRoute = "/"
        }
    }
}
