package dev.cypdashuhn.uniplanner.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SessionStoreTest {
    private val principal = AuthPrincipal("cyp")
    private val clock = MutableClock(Instant.parse("2026-10-22T20:00:00Z"))
    private val store = SessionStore(clock, SequenceTokenSource())

    @Test
    fun `creates and resolves a session`() {
        val issued = store.create(principal, Duration.ofHours(1))

        assertEquals(principal, store.resolve(issued.token)?.principal)
        assertEquals(1, store.size)
    }

    @Test
    fun `rejects unknown token`() {
        assertNull(store.resolve(null))
        assertNull(store.resolve(""))
        assertNull(store.resolve("unknown"))
    }

    @Test
    fun `expires sessions`() {
        val issued = store.create(principal, Duration.ofHours(1))
        clock.current = clock.current.plus(Duration.ofHours(1))

        assertNull(store.resolve(issued.token))
        assertEquals(0, store.size)
    }

    @Test
    fun `revokes sessions`() {
        val issued = store.create(principal, Duration.ofHours(1))

        assertEquals(true, store.revoke(issued.token))
        assertNull(store.resolve(issued.token))
        assertFalse(store.revoke(issued.token))
    }

    @Test
    fun `purges expired sessions`() {
        store.create(principal, Duration.ofMinutes(5))
        clock.current = clock.current.plus(Duration.ofMinutes(5))

        assertEquals(1, store.purgeExpired())
        assertEquals(0, store.size)
    }

    @Test
    fun `rejects non-positive ttl`() {
        assertFailsWith<IllegalArgumentException> {
            store.create(principal, Duration.ZERO)
        }
    }

    @Test
    fun `issued session hides raw value when printed`() {
        val issued = store.create(principal, Duration.ofHours(1))

        assertFalse(issued.toString().contains(issued.token))
    }
}

private class SequenceTokenSource : TokenSource {
    private var next = 0

    override fun generate(): String = "session-token-${next++}"
}

private class MutableClock(
    var current: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current
}
