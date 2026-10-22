package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.AuthPrincipal
import dev.cypdashuhn.uniplanner.auth.http.SessionCookie
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal const val TEST_TOKEN = "test-token-0123456789abcdef0123456789abcdef"

internal fun withServer(
    vault: Path = Files.createTempDirectory("upl-server-test"),
    staticRoot: Path? = null,
    block: suspend ApplicationTestBuilder.() -> Unit,
) {
    testApplication {
        application {
            module(
                ServerConfig(
                    host = "127.0.0.1",
                    port = 0,
                    token = TEST_TOKEN,
                    principal = AuthPrincipal("test", mapOf("vault" to "personal")),
                    cookie = SessionCookie(secure = false),
                    sessionTtl = Duration.ofHours(12),
                    vault = vault,
                    staticRoot = staticRoot,
                ),
            )
        }
        block()
    }
}
