package dev.cypdashuhn.uniplanner.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class UplFile(
    private val path: Path,
) {
    fun read(): UplDocument = UplJson.read(Files.readString(path), path.toString())

    fun write(document: UplDocument) {
        val directory = requireNotNull(path.toAbsolutePath().parent) { "no parent directory for $path" }
        val temp = Files.createTempFile(directory, path.fileName.toString(), ".tmp")
        Files.writeString(temp, UplJson.write(document))
        Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
