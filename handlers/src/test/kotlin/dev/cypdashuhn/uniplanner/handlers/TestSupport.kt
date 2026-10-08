package dev.cypdashuhn.uniplanner.handlers

import dev.cypdashuhn.uniplanner.core.DocumentKind
import dev.cypdashuhn.uniplanner.core.Gatekeeper
import dev.cypdashuhn.uniplanner.core.HandlerRegistry
import dev.cypdashuhn.uniplanner.core.MutationResult
import dev.cypdashuhn.uniplanner.core.UplDocument
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import dev.cypdashuhn.uniplanner.handler.MutationRequest
import kotlinx.serialization.json.JsonElement

internal fun task(vararg fields: Pair<FieldKey, JsonElement>): UplDocument =
    UplDocument("t1", DocumentKind.TASK, fields.toMap())

internal fun apply(handler: Handler, document: UplDocument, vararg mutations: Mutation): MutationResult =
    Gatekeeper(HandlerRegistry(listOf(handler))).apply(document, MutationRequest(handler.namespace, mutations.toList()))

internal fun MutationResult.applied(): UplDocument = when (this) {
    is MutationResult.Applied -> document
    is MutationResult.Rejected -> error("expected Applied but was Rejected: $reason")
}
