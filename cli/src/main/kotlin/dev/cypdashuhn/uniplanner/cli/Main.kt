package dev.cypdashuhn.uniplanner.cli

import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.UplFile
import dev.cypdashuhn.uniplanner.handlers.BuiltinHandlers
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "inspect" -> inspect(pathFrom(args))
        "handlers" -> handlers()
        else -> printUsage()
    }
}

private fun inspect(path: Path) {
    val document = UplFile(path).read()
    println("${document.kind.name.lowercase()} ${document.id}")
    document.fields.keys.map { it.value }.sorted().forEach(::println)
}

private fun handlers() {
    HandlerRegistry(BuiltinHandlers.all())
    BuiltinHandlers.all().forEach { handler ->
        println(handler.namespace)
        handler.ownedFields().map { it.value }.sorted().forEach { println("  owns $it") }
        handler.subscribedFields().map { it.value }.sorted().forEach { println("  subscribes $it") }
    }
}

private fun pathFrom(args: Array<String>): Path {
    val value = args.getOrNull(1) ?: fail("usage: uniplanner inspect <file.upl.json>")
    return Path.of(value)
}

private fun printUsage() {
    println("usage:")
    println("  uniplanner inspect <file.upl.json>")
    println("  uniplanner handlers")
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
