package dev.cypdashuhn.uniplanner.server

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultTest {
    private val root = Files.createTempDirectory("upl-vault-test")
    private val vault = Vault(root)

    @Test
    fun `resolves a document inside the vault`() {
        val file = root.resolve("week.upl.json")
        Files.writeString(file, "{}")

        assertEquals(file.toRealPath(), vault.resolve("week.upl.json"))
    }

    @Test
    fun `rejects paths outside the vault`() {
        assertFailsWith<IllegalArgumentException> { vault.resolve("../outside.upl.json") }
        assertFailsWith<IllegalArgumentException> { vault.resolve(root.resolve("outside.upl.json").toString()) }
    }

    @Test
    fun `rejects non-document paths`() {
        assertFailsWith<IllegalArgumentException> { vault.resolve("notes.txt") }
        assertFailsWith<IllegalArgumentException> { vault.resolve(null) }
    }

    @Test
    fun `rejects missing files`() {
        assertFailsWith<NoSuchFileException> { vault.resolve("missing.upl.json") }
    }

    @Test
    fun `resolves directories`() {
        val directory = Files.createDirectory(root.resolve("planner"))

        assertEquals(directory.toRealPath(), vault.resolveDirectory("planner"))
        assertEquals(root.toRealPath(), vault.resolveDirectory("."))
    }

    @Test
    fun `rejects missing files as directories`() {
        Files.writeString(root.resolve("week.upl.json"), "{}")

        assertFailsWith<NoSuchFileException> { vault.resolveDirectory("missing") }
        assertFailsWith<NoSuchFileException> { vault.resolveDirectory("week.upl.json") }
        assertFailsWith<IllegalArgumentException> { vault.resolveDirectory("../outside") }
        assertFailsWith<IllegalArgumentException> { vault.resolveDirectory(null) }
    }

    @Test
    fun `resolves missing files for writing`() {
        val file = root.resolve("new-week.upl.json")

        assertEquals(file, vault.resolveForWrite("new-week.upl.json"))
    }

    @Test
    fun `rejects missing directories for writing`() {
        assertFailsWith<NoSuchFileException> { vault.resolveForWrite("missing/new-week.upl.json") }
    }

    @Test
    fun `rejects invalid write paths`() {
        assertFailsWith<IllegalArgumentException> { vault.resolveForWrite("../outside.upl.json") }
        assertFailsWith<IllegalArgumentException> { vault.resolveForWrite(root.resolve("outside.upl.json").toString()) }
        assertFailsWith<IllegalArgumentException> { vault.resolveForWrite("notes.txt") }
        assertFailsWith<IllegalArgumentException> { vault.resolveForWrite(null) }
    }

    @Test
    fun `rejects directories for writing`() {
        val directory = root.resolve("folder.upl.json")
        Files.createDirectory(directory)

        assertFailsWith<NoSuchFileException> { vault.resolveForWrite("folder.upl.json") }
    }

    @Test
    fun `rejects directories`() {
        val directory = root.resolve("folder.upl.json")
        Files.createDirectory(directory)

        assertFailsWith<NoSuchFileException> { vault.resolve("folder.upl.json") }
    }
}
