package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.http.HttpAuth
import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.core.UplFile
import dev.cypdashuhn.uniplanner.core.UplJson
import dev.cypdashuhn.uniplanner.core.UplRequestJson
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handler.Std
import dev.cypdashuhn.uniplanner.handlers.BuiltinHandlers
import dev.cypdashuhn.uniplanner.server.history.HistoryStore
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

private val historyLog = LoggerFactory.getLogger("dev.cypdashuhn.uniplanner.server.PlanningRoutes")

fun Application.documentRoutes(
    httpAuth: HttpAuth,
    vault: Vault,
    history: HistoryStore,
) {
    val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))
    val writes = Mutex()

    routing {
        route("/api") {
            get("/document") {
                call.getDocument(httpAuth, vault)
            }

            get("/tags") {
                call.getTags(httpAuth, vault)
            }

            put("/document") {
                call.replaceDocument(httpAuth, vault, gatekeeper, writes, history)
            }

            post("/preview") {
                call.mutate(httpAuth, vault, gatekeeper, writes, history, write = false)
            }

            post("/mutation") {
                call.mutate(httpAuth, vault, gatekeeper, writes, history, write = true)
            }
        }
    }
}

private suspend fun ApplicationCall.getDocument(httpAuth: HttpAuth, vault: Vault) {
    requirePrincipal(httpAuth) ?: return
    val path = vaultPath(vault) ?: return

    val document =
        try {
            withContext(Dispatchers.IO) { UplJson.write(UplFile(path).read()) }
        } catch (error: NoSuchFileException) {
            respond(HttpStatusCode.NotFound)
            return
        } catch (error: IllegalArgumentException) {
            respondFailure(error)
            return
        } catch (error: IOException) {
            respondFailure(error)
            return
        }

    respondText(document, ContentType.Application.Json)
}

private suspend fun ApplicationCall.getTags(httpAuth: HttpAuth, vault: Vault) {
    requirePrincipal(httpAuth) ?: return
    val directory = vaultDirectory(vault) ?: return

    val tags =
        try {
            withContext(Dispatchers.IO) { directoryTags(directory) }
        } catch (error: IllegalArgumentException) {
            respondFailure(error)
            return
        } catch (error: IOException) {
            respondFailure(error)
            return
        }

    val body = buildJsonObject { put("tags", buildJsonArray { tags.forEach { add(it) } }) }
    respondText(body.toString(), ContentType.Application.Json)
}

private fun directoryTags(directory: Path): Set<String> {
    val tags = sortedSetOf<String>()
    Files.list(directory).use { files ->
        files
            .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".upl.json") }
            .forEach { file -> collectTags(UplFile(file).read(), tags) }
    }
    return tags
}

private fun collectTags(document: UplDocument, tags: MutableSet<String>) {
    if (document.isContainer) {
        document.tasks.values.forEach { collectTags(it, tags) }
        return
    }
    if (document.kind != DocumentKind.TASK) return

    val values = document.fields[Std.tags] as? JsonArray ?: return
    for (value in values) {
        val tag = (value as? JsonPrimitive)?.contentOrNull?.trim()?.trimStart('#').orEmpty()
        if (tag.isNotEmpty()) tags.add(tag)
    }
}

private suspend fun ApplicationCall.replaceDocument(
    httpAuth: HttpAuth,
    vault: Vault,
    gatekeeper: Gatekeeper,
    writes: Mutex,
    history: HistoryStore,
) {
    val principal = requirePrincipal(httpAuth) ?: return
    val path = vaultPathForWrite(vault) ?: return
    val document =
        try {
            UplJson.read(receiveText())
        } catch (error: IllegalArgumentException) {
            respondFailure(error)
            return
        } catch (error: IOException) {
            respondFailure(error)
            return
        }
    if (!document.isContainer && !document.isWorkspace) {
        respondFailure(IllegalArgumentException("document must be a container or workspace"))
        return
    }

    val result =
        try {
            withContext(Dispatchers.IO) {
                writes.withLock {
                    val before = readBeforeReplacement(path)
                    val request = MutationRequest("import", emptyList())
                    val applied = gatekeeper.apply(document, request)
                    if (applied is MutationResult.Applied) {
                        UplFile(path).write(applied.document)
                        history.recordReplace(path, principal.id, before, applied.document, request)
                    }
                    applied
                }
            }
        } catch (error: IllegalArgumentException) {
            respondFailure(error)
            return
        } catch (error: IOException) {
            respondFailure(error)
            return
        }

    when (result) {
        is MutationResult.Applied -> respondText(appliedJson(result), ContentType.Application.Json)
        is MutationResult.Rejected ->
            respondText(errorJson(result.reason), ContentType.Application.Json, HttpStatusCode.BadRequest)
    }
}

private suspend fun ApplicationCall.mutate(
    httpAuth: HttpAuth,
    vault: Vault,
    gatekeeper: Gatekeeper,
    writes: Mutex,
    history: HistoryStore,
    write: Boolean,
) {
    val principal = requirePrincipal(httpAuth) ?: return
    val path = vaultPath(vault) ?: return
    val request = mutationRequest() ?: return

    val result =
        try {
            withContext(Dispatchers.IO) {
                if (write) {
                    writes.withLock { applyAndWrite(path, request, gatekeeper, history, principal.id) }
                } else {
                    gatekeeper.apply(UplFile(path).read(), request)
                }
            }
        } catch (error: NoSuchFileException) {
            respond(HttpStatusCode.NotFound)
            return
        } catch (error: IllegalArgumentException) {
            respondFailure(error)
            return
        } catch (error: IOException) {
            respondFailure(error)
            return
        }

    when (result) {
        is MutationResult.Applied -> respondText(appliedJson(result), ContentType.Application.Json)
        is MutationResult.Rejected ->
            respondText(errorJson(result.reason), ContentType.Application.Json, HttpStatusCode.BadRequest)
    }
}

private fun readBeforeReplacement(path: Path): UplDocument? {
    if (!Files.isRegularFile(path)) return null

    return try {
        UplFile(path).read()
    } catch (error: IllegalArgumentException) {
        historyLog.warn("could not read document before replacement: $path", error)
        null
    } catch (error: IOException) {
        historyLog.warn("could not read document before replacement: $path", error)
        null
    }
}

private fun applyAndWrite(
    path: Path,
    request: MutationRequest,
    gatekeeper: Gatekeeper,
    history: HistoryStore,
    principal: String,
): MutationResult {
    val before = UplFile(path).read()
    val result = gatekeeper.apply(before, request)
    if (result is MutationResult.Applied) UplFile(path).write(result.document)
    history.recordMutation(path, principal, before, request, result)
    return result
}

private suspend fun ApplicationCall.vaultDirectory(vault: Vault): Path? =
    try {
        vault.resolveDirectory(request.queryParameters["path"])
    } catch (error: NoSuchFileException) {
        respond(HttpStatusCode.NotFound)
        null
    } catch (error: IllegalArgumentException) {
        respondFailure(error)
        null
    } catch (error: IOException) {
        respondFailure(error)
        null
    }

private suspend fun ApplicationCall.vaultPath(vault: Vault): Path? =
    try {
        vault.resolve(request.queryParameters["path"])
    } catch (error: NoSuchFileException) {
        respond(HttpStatusCode.NotFound)
        null
    } catch (error: IllegalArgumentException) {
        respondFailure(error)
        null
    } catch (error: IOException) {
        respondFailure(error)
        null
    }

private suspend fun ApplicationCall.vaultPathForWrite(vault: Vault): Path? =
    try {
        vault.resolveForWrite(request.queryParameters["path"])
    } catch (error: NoSuchFileException) {
        respond(HttpStatusCode.NotFound)
        null
    } catch (error: IllegalArgumentException) {
        respondFailure(error)
        null
    } catch (error: IOException) {
        respondFailure(error)
        null
    }

private suspend fun ApplicationCall.mutationRequest(): MutationRequest? =
    try {
        UplRequestJson.read(receiveText())
    } catch (error: IllegalArgumentException) {
        respondFailure(error)
        null
    } catch (error: IOException) {
        respondFailure(error)
        null
    }

private fun appliedJson(result: MutationResult.Applied): String =
    buildJsonObject {
        put("document", Json.parseToJsonElement(UplJson.write(result.document)))
        put(
            "changes",
            buildJsonArray {
                result.changes.forEach { add(changeJson(it)) }
            },
        )
    }.toString()

private fun changeJson(change: FieldChange): JsonObject =
    buildJsonObject {
        put("field", change.field.value)
        put("target", change.target?.let { JsonPrimitive(it) } ?: JsonNull)
        put("oldValue", change.oldValue ?: JsonNull)
        put("newValue", change.newValue ?: JsonNull)
    }

private suspend fun ApplicationCall.respondFailure(error: Throwable) {
    val status =
        when (error) {
            is IllegalArgumentException -> HttpStatusCode.BadRequest
            is IOException -> HttpStatusCode.InternalServerError
            else -> HttpStatusCode.InternalServerError
        }
    respondText(errorJson(error.message ?: "request failed"), ContentType.Application.Json, status)
}

private fun errorJson(message: String): String =
    buildJsonObject {
        put("error", message)
    }.toString()
