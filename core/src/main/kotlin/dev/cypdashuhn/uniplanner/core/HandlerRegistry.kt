package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler

class HandlerRegistry(handlers: List<Handler>) {
    private val handlersByNamespace = handlers.associateBy { it.namespace }
    private val ownersByField = mutableMapOf<FieldKey, String>()
    private val subscribersByField = mutableMapOf<FieldKey, MutableList<Handler>>()

    init {
        handlers.forEach(::register)
    }

    fun handler(namespace: String): Handler? = handlersByNamespace[namespace]

    fun ownerOf(field: FieldKey): String? = ownersByField[field]

    fun subscribersOf(field: FieldKey): List<Handler> = subscribersByField[field].orEmpty()

    private fun register(handler: Handler) {
        handler.ownedFields().forEach { field ->
            val existing = ownersByField.putIfAbsent(field, handler.namespace)
            require(existing == null) { "field ${field.value} owned by both $existing and ${handler.namespace}" }
        }
        handler.subscribedFields().forEach { field ->
            subscribersByField.getOrPut(field) { mutableListOf() }.add(handler)
        }
    }
}
