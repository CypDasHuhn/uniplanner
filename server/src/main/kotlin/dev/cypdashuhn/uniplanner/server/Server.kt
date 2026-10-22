package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.SessionStore
import dev.cypdashuhn.uniplanner.auth.StaticToken
import dev.cypdashuhn.uniplanner.auth.StaticTokenAuthenticator
import dev.cypdashuhn.uniplanner.auth.http.HttpAuth
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = ServerConfig.fromEnvironment()
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: ServerConfig) {
    val httpAuth = HttpAuth(
        authenticator = StaticTokenAuthenticator(listOf(StaticToken.fromRaw(config.token, config.principal))),
        sessions = SessionStore(),
        cookie = config.cookie,
        sessionTtl = config.sessionTtl,
    )
    authRoutes(httpAuth)
    documentRoutes(httpAuth, Vault(config.vault))
}
