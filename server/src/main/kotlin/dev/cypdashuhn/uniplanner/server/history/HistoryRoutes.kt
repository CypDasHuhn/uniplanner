package dev.cypdashuhn.uniplanner.server.history

import dev.cypdashuhn.uniplanner.auth.http.HttpAuth
import dev.cypdashuhn.uniplanner.server.requirePrincipal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val STATUSES = setOf(HistoryEntries.STATUS_APPLIED, HistoryEntries.STATUS_REJECTED)
private val OPERATIONS = setOf(HistoryEntries.OP_MUTATION, HistoryEntries.OP_REPLACE)

//region (Routes)
fun Application.historyRoutes(
    httpAuth: HttpAuth,
    history: HistoryStore,
) {
    routing {
        route("/api") {
            get("/history/days") {
                call.getHistoryDays(httpAuth, history)
            }

            get("/history") {
                call.getHistory(httpAuth, history)
            }
        }
    }
}
//endregion

//region (Handlers)
private suspend fun ApplicationCall.getHistoryDays(
    httpAuth: HttpAuth,
    history: HistoryStore,
) {
    requirePrincipal(httpAuth) ?: return

    val days =
        try {
            withContext(Dispatchers.IO) { history.days() }
        } catch (error: IOException) {
            respondHistoryFailure(error)
            return
        }

    val body =
        buildJsonObject {
            put(
                "days",
                buildJsonArray {
                    days.forEach { day ->
                        add(
                            buildJsonObject {
                                put("date", day.date.toString())
                                put("count", day.count)
                            },
                        )
                    }
                },
            )
        }
    respondText(body.toString(), ContentType.Application.Json)
}

private suspend fun ApplicationCall.getHistory(
    httpAuth: HttpAuth,
    history: HistoryStore,
) {
    requirePrincipal(httpAuth) ?: return

    val query =
        try {
            historyQuery(history)
        } catch (error: IllegalArgumentException) {
            respondHistoryFailure(error)
            return
        }

    val entries =
        try {
            withContext(Dispatchers.IO) { history.entries(query) }
        } catch (error: IllegalArgumentException) {
            respondHistoryFailure(error)
            return
        } catch (error: IOException) {
            respondHistoryFailure(error)
            return
        }

    respondText(entriesJson(entries, query.date, history.config.timeZone.id), ContentType.Application.Json)
}
//endregion

//region (Queries)
private fun ApplicationCall.historyQuery(history: HistoryStore): HistoryQuery {
    val rawDate = queryParameter("date")
    val date =
        when (rawDate) {
            null -> history.today()
            else -> parseDate(rawDate)
        }
    val status = queryParameter("status")
    if (status != null) require(status in STATUSES) { "unknown history status '$status'" }
    val op = queryParameter("op")
    if (op != null) require(op in OPERATIONS) { "unknown history operation '$op'" }

    return HistoryQuery(
        date = date,
        path = historyPath(queryParameter("path")),
        status = status,
        op = op,
        requester = queryParameter("requester"),
        limit = parseLimit(queryParameter("limit")),
        before = queryParameter("before"),
    )
}

private fun ApplicationCall.queryParameter(name: String): String? = request.queryParameters[name]?.trim()?.takeIf { it.isNotEmpty() }

private fun parseDate(raw: String): LocalDate =
    try {
        LocalDate.parse(raw)
    } catch (error: DateTimeParseException) {
        throw IllegalArgumentException("invalid history date '$raw'", error)
    }

private fun historyPath(raw: String?): String? {
    if (raw == null) return null
    val path = Path.of(raw)
    require(!path.isAbsolute) { "history path must be relative" }
    require(path.none { it.toString() == ".." }) { "history path must not contain .." }
    return path.normalize().toString().replace('\\', '/')
}

private fun parseLimit(raw: String?): Int {
    if (raw == null) return HistoryQuery.DEFAULT_LIMIT

    val limit = raw.toIntOrNull() ?: throw IllegalArgumentException("history limit must be a number")
    require(limit in 1..HistoryQuery.MAX_LIMIT) {
        "history limit must be between 1 and ${HistoryQuery.MAX_LIMIT}"
    }
    return limit
}
//endregion

//region (Responses)
private fun entriesJson(
    entries: List<JsonObject>,
    date: LocalDate,
    timeZone: String,
): String =
    buildJsonObject {
        put("date", date.toString())
        put("timeZone", timeZone)
        put("entries", JsonArray(entries))
    }.toString()

private suspend fun ApplicationCall.respondHistoryFailure(error: Throwable) {
    val status =
        when (error) {
            is IllegalArgumentException -> HttpStatusCode.BadRequest
            else -> HttpStatusCode.InternalServerError
        }
    val body =
        buildJsonObject {
            put("error", error.message ?: "request failed")
        }
    respondText(body.toString(), ContentType.Application.Json, status)
}
//endregion
