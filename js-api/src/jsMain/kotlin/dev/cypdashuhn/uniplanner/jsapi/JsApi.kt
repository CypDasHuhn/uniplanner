package dev.cypdashuhn.uniplanner.jsapi

import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplJson
import dev.cypdashuhn.uniplanner.core.UplRequestJson
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handlers.BuiltinHandlers
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.JsExport

private val gatekeeper = Gatekeeper(HandlerRegistry(BuiltinHandlers.all()))

@JsExport
fun applyMutation(
    documentJson: String,
    requestJson: String,
): String = execute(documentJson, requestJson)

@JsExport
fun previewMutation(
    documentJson: String,
    requestJson: String,
): String = execute(documentJson, requestJson)

fun main() {
    val inBrowser = js("typeof window !== 'undefined'") as Boolean
    if (!inBrowser) return

    val api = js("({})")
    api.applyMutation = { documentJson: String, requestJson: String -> applyMutation(documentJson, requestJson) }
    api.previewMutation = { documentJson: String, requestJson: String -> previewMutation(documentJson, requestJson) }
    api.version = "0.1.0"
    window.asDynamic().uniplanner = api
}

private fun execute(
    documentJson: String,
    requestJson: String,
): String =
    try {
        val document = UplJson.read(documentJson, "<document>")
        val request = UplRequestJson.read(requestJson)
        when (val result = gatekeeper.apply(document, request)) {
            is MutationResult.Applied -> appliedJson(result)
            is MutationResult.Rejected -> rejectedJson(result.reason)
        }
    } catch (error: Throwable) {
        rejectedJson(error.message ?: error.toString())
    }

private fun appliedJson(result: MutationResult.Applied): String =
    buildJsonObject {
        put("ok", JsonPrimitive(true))
        put("document", Json.parseToJsonElement(UplJson.write(result.document)))
        put("changes", buildJsonArray { result.changes.forEach { add(changeJson(it)) } })
    }.toString()

private fun changeJson(change: FieldChange): JsonObject =
    buildJsonObject {
        put("field", JsonPrimitive(change.field.value))
        put("oldValue", change.oldValue ?: JsonNull)
        put("newValue", change.newValue ?: JsonNull)
        if (change.target != null) put("target", JsonPrimitive(change.target))
    }

private fun rejectedJson(reason: String): String =
    buildJsonObject {
        put("ok", JsonPrimitive(false))
        put("reason", JsonPrimitive(reason))
    }.toString()
