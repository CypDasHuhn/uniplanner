package dev.cypdashuhn.uniplanner.cli

import dev.cypdashuhn.uniplanner.core.TaskFile
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "inspect" -> inspect(pathFrom(args))
        else -> printUsage()
    }
}

private fun inspect(path: Path) {
    val document = TaskFile(path).read()
    println("task ${document.id}")
    document.fields.keys.map { it.value }.sorted().forEach(::println)
}

private fun pathFrom(args: Array<String>): Path {
    val value = args.getOrNull(1) ?: fail("usage: uniplanner inspect <file.upl.json>")
    return Path.of(value)
}

private fun printUsage() {
    println("usage: uniplanner inspect <file.upl.json>")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
