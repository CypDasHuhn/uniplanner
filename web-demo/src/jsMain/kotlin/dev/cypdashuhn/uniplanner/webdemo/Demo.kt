package dev.cypdashuhn.uniplanner.webdemo

import dev.cypdashuhn.uniplanner.core.CoreFields
import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import dev.cypdashuhn.uniplanner.handlers.BuiltinHandlers
import dev.cypdashuhn.uniplanner.handlers.dayplanner.DayplannerFields
import dev.cypdashuhn.uniplanner.handlers.kanban.KanbanFields
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.JsExport
import kotlin.js.Promise

private val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))
private var document = initialDocument()

@JsExport
fun currentDocument(): String = document.toJson()

@JsExport
fun resetDocument(): String {
    document = initialDocument()
    return document.toJson()
}

@JsExport
fun requestMutation(requestJson: String): Promise<String> =
    Promise { resolve, reject ->
        try {
            when (val result = gatekeeper.apply(document, parseRequest(requestJson))) {
                is MutationResult.Applied -> {
                    document = result.document
                    resolve(document.toJson())
                }

                is MutationResult.Rejected -> {
                    reject(IllegalStateException(result.reason))
                }
            }
        } catch (error: Throwable) {
            reject(error)
        }
    }

fun main() {
    val inBrowser = js("typeof window !== 'undefined'") as Boolean
    if (!inBrowser) return

    val api = js("({})")
    api.currentDocument = { currentDocument() }
    api.resetDocument = { resetDocument() }
    api.requestMutation = { requestJson: String -> requestMutation(requestJson) }
    window.asDynamic().uniplannerDemo = api
}

private fun initialDocument(): UplDocument =
    UplDocument(
        "t1",
        DocumentKind.TASK,
        mapOf(
            KanbanFields.column to JsonPrimitive("doing"),
            KanbanFields.order to JsonPrimitive("m"),
            DayplannerFields.day to JsonPrimitive("2026-04-09"),
            DayplannerFields.order to JsonPrimitive("m"),
        ),
    )

private fun parseRequest(requestJson: String): MutationRequest {
    val json = Json.parseToJsonElement(requestJson).jsonObject
    val requester = json.getValue("requester").jsonPrimitive.content
    val mutations =
        json.getValue("mutations").jsonArray.map { element ->
            val mutation = element.jsonObject
            Mutation(FieldKey(mutation.getValue("field").jsonPrimitive.content), mutation["value"])
        }
    return MutationRequest(requester, mutations)
}

private fun UplDocument.toJson(): String =
    buildJsonObject {
        put(CoreFields.id.value, JsonPrimitive(id))
        fields.forEach { (field, value) -> put(field.value, value) }
    }.toString()
