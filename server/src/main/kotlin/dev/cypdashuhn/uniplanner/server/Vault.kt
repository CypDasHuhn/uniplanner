package dev.cypdashuhn.uniplanner.server

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

class Vault(root: Path) {
    val root: Path = root.toRealPath()

    fun resolve(relative: String?): Path {
        val candidate = requestedPath(relative)
        val real = candidate.toRealPath()
        require(real.startsWith(root)) { "path escapes vault" }
        if (!Files.isRegularFile(real)) throw NoSuchFileException(real.toString())

        return real
    }

    fun resolveDirectory(relative: String?): Path {
        val candidate = candidatePath(requiredPath(relative))
        val real = candidate.toRealPath()
        require(real.startsWith(root)) { "path escapes vault" }
        if (!Files.isDirectory(real)) throw NoSuchFileException(real.toString())

        return real
    }

    fun resolveForWrite(relative: String?): Path {
        val candidate = requestedPath(relative)
        val parent = candidate.parent ?: throw IllegalArgumentException("path has no parent")
        val realParent = parent.toRealPath()
        require(realParent.startsWith(root)) { "path escapes vault" }
        val target = realParent.resolve(candidate.fileName.toString())

        if (Files.exists(target)) {
            val real = target.toRealPath()
            require(real.startsWith(root)) { "path escapes vault" }
            if (!Files.isRegularFile(real)) throw NoSuchFileException(real.toString())
            return real
        }

        return target
    }

    private fun requestedPath(relative: String?): Path {
        val requested = requiredPath(relative)
        require(requested.endsWith(".upl.json")) { "path must end with .upl.json" }
        return candidatePath(requested)
    }

    private fun requiredPath(relative: String?): String {
        val requested = relative?.trim().orEmpty()
        require(requested.isNotEmpty()) { "path is required" }
        require(!Path.of(requested).isAbsolute) { "path must be relative" }
        return requested
    }

    private fun candidatePath(requested: String): Path {
        val candidate = root.resolve(requested).normalize()
        require(candidate.startsWith(root)) { "path escapes vault" }
        return candidate
    }
}
