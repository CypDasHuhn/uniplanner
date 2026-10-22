package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.AuthPrincipal
import dev.cypdashuhn.uniplanner.auth.http.SessionCookie
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class ServerConfig(
    val host: String,
    val port: Int,
    val token: String,
    val principal: AuthPrincipal,
    val cookie: SessionCookie,
    val sessionTtl: Duration,
    val vault: Path,
) {
    override fun toString(): String =
        "ServerConfig(host=$host, port=$port, principal=${principal.id}, cookie=${cookie.name}, " +
            "sessionTtl=$sessionTtl, vault=$vault)"

    companion object {
        private const val DEFAULT_HOST = "0.0.0.0"
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_PRINCIPAL = "owner"
        private const val DEFAULT_SESSION_MINUTES = 720L

        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            val token = env.required("UPL_TOKEN")
            val port = env.intOr("UPL_PORT", DEFAULT_PORT)
            require(port in 1..65535) { "UPL_PORT must be between 1 and 65535" }

            val sessionMinutes = env.longOr("UPL_SESSION_MINUTES", DEFAULT_SESSION_MINUTES)
            require(sessionMinutes > 0) { "UPL_SESSION_MINUTES must be positive" }

            val vault = Path.of(env.required("UPL_VAULT")).toAbsolutePath().normalize()
            require(Files.isDirectory(vault)) { "UPL_VAULT must be a directory" }

            return ServerConfig(
                host = env.valueOr("UPL_HOST", DEFAULT_HOST),
                port = port,
                token = token,
                principal = AuthPrincipal(env.valueOr("UPL_PRINCIPAL_ID", DEFAULT_PRINCIPAL)),
                cookie = SessionCookie(
                    name = env.valueOr("UPL_COOKIE_NAME", "upl_session"),
                    secure = env.flagOr("UPL_COOKIE_SECURE", true),
                ),
                sessionTtl = Duration.ofMinutes(sessionMinutes),
                vault = vault,
            )
        }
    }
}

private fun Map<String, String>.required(name: String): String =
    valueOrNull(name) ?: error("$name is required")

private fun Map<String, String>.valueOr(
    name: String,
    default: String,
): String = valueOrNull(name) ?: default

private fun Map<String, String>.intOr(
    name: String,
    default: Int,
): Int =
    valueOrNull(name)
        ?.let { requireNotNull(it.toIntOrNull()) { "$name must be a number" } }
        ?: default

private fun Map<String, String>.longOr(
    name: String,
    default: Long,
): Long =
    valueOrNull(name)
        ?.let { requireNotNull(it.toLongOrNull()) { "$name must be a number" } }
        ?: default

private fun Map<String, String>.flagOr(
    name: String,
    default: Boolean,
): Boolean {
    val raw = valueOrNull(name) ?: return default
    require(raw == "true" || raw == "false") { "$name must be true or false" }
    return raw.toBoolean()
}

private fun Map<String, String>.valueOrNull(name: String): String? =
    get(name)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
