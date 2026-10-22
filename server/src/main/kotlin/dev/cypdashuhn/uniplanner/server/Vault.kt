package dev.cypdashuhn.uniplanner.server

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class Vault(root: Path) {
    private val root = root.toRealPath()

    fun resolve(relative: String?): Path {
        val requested = relative?.trim().orEmpty()
        require(requested.isNotEmpty()) { "path is required" }
        require(!Path.of(requested).isAbsolute) { "path must be relative" }
        require(requested.endsWith(".upl.json")) { "path must end with .upl.json" }

        val candidate = root.resolve(requested).normalize()
        require(candidate.startsWith(root)) { "path escapes vault" }

        val real = candidate.toRealPath()
        require(real.startsWith(root)) { "path escapes vault" }
        if (!Files.isRegularFile(real)) throw NoSuchFileException(real.toString())

        return real
    }
}
