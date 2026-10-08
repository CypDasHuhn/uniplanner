package dev.cypdashuhn.uniplanner.handler

interface Handler {
    val namespace: String

    fun ownedFields(): Set<FieldKey>

    fun subscribedFields(): Set<FieldKey>

    fun onFieldChanged(change: FieldChange): List<Mutation>
}
