package dev.cypdashuhn.uniplanner.server

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerConfigTest {
    private val vault = System.getProperty("java.io.tmpdir")

    @Test
    fun `reads environment`() {
        val config = ServerConfig.fromEnvironment(
            env(
                mapOf(
                    "UPL_TOKEN" to " secret ",
                    "UPL_HOST" to "127.0.0.1",
                    "UPL_PORT" to "9090",
                    "UPL_PRINCIPAL_ID" to "alice",
                    "UPL_COOKIE_NAME" to "session",
                    "UPL_COOKIE_SECURE" to "false",
                    "UPL_SESSION_MINUTES" to "30",
                ),
            ),
        )

        assertEquals("secret", config.token)
        assertEquals("127.0.0.1", config.host)
        assertEquals(9090, config.port)
        assertEquals("alice", config.principal.id)
        assertEquals("session", config.cookie.name)
        assertFalse(config.cookie.secure)
        assertEquals(Duration.ofMinutes(30), config.sessionTtl)
        assertEquals(Path.of(vault).toAbsolutePath().normalize(), config.vault)
    }

    @Test
    fun `uses defaults`() {
        val config = ServerConfig.fromEnvironment(env(emptyMap()))

        assertEquals("0.0.0.0", config.host)
        assertEquals(8080, config.port)
        assertEquals("owner", config.principal.id)
        assertEquals("upl_session", config.cookie.name)
        assertTrue(config.cookie.secure)
        assertEquals(Duration.ofHours(12), config.sessionTtl)
        assertNull(config.staticRoot)
        assertTrue(config.history.enabled)
        assertEquals(Path.of(vault).resolve(".upl-history").toAbsolutePath().normalize(), config.history.directory)
        assertEquals(ZoneId.of("UTC"), config.history.timeZone)
        assertEquals(90, config.history.retentionDays)
    }

    @Test
    fun `reads history configuration`() {
        val config =
            ServerConfig.fromEnvironment(
                env(
                    mapOf(
                        "UPL_HISTORY_ENABLED" to "false",
                        "UPL_HISTORY_DIR" to "audit/history",
                        "UPL_HISTORY_TIME_ZONE" to "Europe/Berlin",
                        "UPL_HISTORY_RETENTION_DAYS" to "0",
                    ),
                ),
            )

        assertFalse(config.history.enabled)
        assertEquals(Path.of(vault).resolve("audit/history").toAbsolutePath().normalize(), config.history.directory)
        assertEquals(ZoneId.of("Europe/Berlin"), config.history.timeZone)
        assertEquals(0, config.history.retentionDays)
    }

    @Test
    fun `rejects invalid history configuration`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_ENABLED" to "yes")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_RETENTION_DAYS" to "-1")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_RETENTION_DAYS" to "later")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_TIME_ZONE" to "Mars/Olympus")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_DIR" to "../outside")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(
                env(mapOf("UPL_HISTORY_DIR" to Path.of(vault).resolveSibling("outside-history").toString())),
            )
        }
    }

    @Test
    fun `accepts an absolute history directory inside the vault`() {
        val directory = Path.of(vault).resolve("custom-history")

        val config = ServerConfig.fromEnvironment(env(mapOf("UPL_HISTORY_DIR" to directory.toString())))

        assertEquals(directory.toAbsolutePath().normalize(), config.history.directory)
    }

    @Test
    fun `reads static root`() {
        val root = Files.createTempDirectory("upl-static")
        Files.writeString(root.resolve("index.html"), "<html></html>")

        val config = ServerConfig.fromEnvironment(env(mapOf("UPL_STATIC" to root.toString())))

        assertEquals(root.toAbsolutePath().normalize(), config.staticRoot)
    }

    @Test
    fun `rejects invalid static roots`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_STATIC" to "/missing")))
        }

        val empty = Files.createTempDirectory("upl-static-empty")
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_STATIC" to empty.toString())))
        }

        val file = Files.createTempFile("upl-static-file", ".html")
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_STATIC" to file.toString())))
        }
    }

    @Test
    fun `requires token`() {
        assertFailsWith<IllegalStateException> {
            ServerConfig.fromEnvironment(emptyMap())
        }
    }

    @Test
    fun `rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_PORT" to "0")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_PORT" to "http")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_SESSION_MINUTES" to "0")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_SESSION_MINUTES" to "later")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(env(mapOf("UPL_COOKIE_SECURE" to "yes")))
        }
        assertFailsWith<IllegalArgumentException> {
            ServerConfig.fromEnvironment(mapOf("UPL_TOKEN" to "secret", "UPL_VAULT" to "/missing"))
        }
    }

    @Test
    fun `hides token when printed`() {
        val config = ServerConfig.fromEnvironment(env(mapOf("UPL_TOKEN" to "secret")))

        assertFalse(config.toString().contains("secret"))
    }

    private fun env(entries: Map<String, String>): Map<String, String> =
        mapOf("UPL_TOKEN" to "secret", "UPL_VAULT" to vault) + entries
}
