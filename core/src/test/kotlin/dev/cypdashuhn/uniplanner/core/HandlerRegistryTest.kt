package dev.cypdashuhn.uniplanner.core

import dev.cypdashuhn.uniplanner.handler.DocumentView
import dev.cypdashuhn.uniplanner.handler.FieldChange
import dev.cypdashuhn.uniplanner.handler.FieldKey
import dev.cypdashuhn.uniplanner.handler.Handler
import dev.cypdashuhn.uniplanner.handler.Mutation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandlerRegistryTest {
    @Test
    fun `rejects duplicate namespaces`() {
        assertFailsWith<IllegalArgumentException> {
            HandlerRegistry(listOf(StubHandler("kanban"), StubHandler("kanban")))
        }
    }

    @Test
    fun `rejects identical ownership across handlers`() {
        val field = FieldKey("std.status.closed")

        assertFailsWith<IllegalArgumentException> {
            HandlerRegistry(listOf(StubHandler("kanban", owned = setOf(field)), StubHandler("dayplanner", owned = setOf(field))))
        }
    }

    @Test
    fun `rejects overlapping ownership across handlers`() {
        assertFailsWith<IllegalArgumentException> {
            HandlerRegistry(
                listOf(
                    StubHandler("gantt", owned = setOf(FieldKey("std.time"))),
                    StubHandler("dayplanner", owned = setOf(FieldKey("std.time.estimate"))),
                ),
            )
        }
    }

    @Test
    fun `resolves ownership through group prefixes`() {
        val registry = HandlerRegistry(listOf(StubHandler("gantt", owned = setOf(FieldKey("std.time")))))

        assertEquals("gantt", registry.ownerOf(FieldKey("std.time.start")))
        assertEquals("gantt", registry.ownerOf(FieldKey("std.time")))
        assertEquals(null, registry.ownerOf(FieldKey("std.timer")))
    }

    @Test
    fun `resolves subscriptions through group prefixes`() {
        val registry = HandlerRegistry(listOf(StubHandler("gantt", subscribed = setOf(FieldKey("std.time")))))

        assertEquals(listOf("gantt"), registry.subscribersOf(FieldKey("std.time.start")).map { it.namespace })
        assertTrue(registry.subscribersOf(FieldKey("std.timer")).isEmpty())
    }

    @Test
    fun `subscribes a handler once when group and leaf both match`() {
        val registry = HandlerRegistry(
            listOf(StubHandler("gantt", subscribed = setOf(FieldKey("std.time"), FieldKey("std.time.start")))),
        )

        assertEquals(1, registry.subscribersOf(FieldKey("std.time.start")).size)
    }

    @Test
    fun `detects group keys from declared owned and subscribed descendants`() {
        val registry = HandlerRegistry(
            listOf(StubHandler("gantt", owned = setOf(FieldKey("std.time.start")), subscribed = setOf(FieldKey("std.status.closed")))),
        )

        assertTrue(registry.isGroupKey(FieldKey("std.time")))
        assertTrue(registry.isGroupKey(FieldKey("std.status")))
        assertTrue(registry.isGroupKey(FieldKey("std")))
        assertFalse(registry.isGroupKey(FieldKey("std.time.start")))
        assertFalse(registry.isGroupKey(FieldKey("std.timeframe")))
    }
}

private class StubHandler(
    override val namespace: String,
    private val owned: Set<FieldKey> = emptySet(),
    private val subscribed: Set<FieldKey> = emptySet(),
) : Handler {
    override fun ownedFields(): Set<FieldKey> = owned

    override fun subscribedFields(): Set<FieldKey> = subscribed

    override fun onFieldChanged(change: FieldChange, document: DocumentView): List<Mutation> = emptyList()
}
