package dev.cypdashuhn.uniplanner.server

import dev.cypdashuhn.uniplanner.auth.http.HttpAuth
import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplFile
import dev.cypdashuhn.uniplanner.core.UplJson
import dev.cypdashuhn.uniplanner.core.UplRequestJson
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handlers.BuiltinHandlers
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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

fun Application.documentRoutes(httpAuth: HttpAuth, vault: Vault) {
    val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))
    val writes = Mutex()

    routing {
        route("/api") {
            get("/document") {
                call.getDocument(httpAuth, vault)
            }

            post("/preview") {
                call.mutate(httpAuth, vault, gatekeeper, writes, write = false)
            }

            post("/mutation") {
                call.mutate(httpAuth, vault, gatekeeper, writes, write = true)
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

private suspend fun ApplicationCall.mutate(
    httpAuth: HttpAuth,
    vault: Vault,
    gatekeeper: Gatekeeper,
    writes: Mutex,
    write: Boolean,
) {
    requirePrincipal(httpAuth) ?: return
    val path = vaultPath(vault) ?: return
    val request = mutationRequest() ?: return

    val result =
        try {
            withContext(Dispatchers.IO) {
                if (write) {
                    writes.withLock { applyAndWrite(path, request, gatekeeper) }
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

private fun applyAndWrite(
    path: Path,
    request: MutationRequest,
    gatekeeper: Gatekeeper,
): MutationResult {
    val document = UplFile(path).read()
    val result = gatekeeper.apply(document, request)
    if (result is MutationResult.Applied) UplFile(path).write(result.document)
    return result
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
