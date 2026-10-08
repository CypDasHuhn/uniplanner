package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler

class HandlerRegistry(handlers: List<Handler>) {
    private val handlersByNamespace = handlers.associateBy { it.namespace }
    private val owned = handlers.flatMap { handler -> handler.ownedFields().map { Declaration(it, handler.namespace) } }
    private val subscribed = handlers.flatMap { handler -> handler.subscribedFields().map { Declaration(it, handler.namespace) } }
    private val declaredKeys = (owned + subscribed).map { it.key }.toSet()

    init {
        require(handlersByNamespace.size == handlers.size) {
            val duplicates = handlers.groupBy { it.namespace }.filterValues { it.size > 1 }.keys.joinToString()
            "duplicate handler namespace: $duplicates"
        }
        owned.forEach { first ->
            owned.filter { it.namespace != first.namespace }.forEach { second ->
                require(!first.key.covers(second.key) && !second.key.covers(first.key)) {
                    "ownership conflict: ${first.key.value} (${first.namespace}) and ${second.key.value} (${second.namespace})"
                }
            }
        }
    }

    fun handler(namespace: String): Handler? = handlersByNamespace[namespace]

    fun ownerOf(field: FieldKey): String? = owned.firstOrNull { it.key.covers(field) }?.namespace

    fun subscribersOf(field: FieldKey): List<Handler> = subscribed
        .filter { it.key.covers(field) }
        .map { handlersByNamespace.getValue(it.namespace) }
        .distinctBy { it.namespace }

    fun isGroupKey(field: FieldKey): Boolean = declaredKeys.any { it.value.startsWith("${field.value}.") }

    private data class Declaration(val key: FieldKey, val namespace: String)
}

private fun FieldKey.covers(other: FieldKey): Boolean =
    this == other || other.value.startsWith("${value}.")
