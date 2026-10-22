package dev.cypdashuhn.uniplanner.auth.http

import dev.cypdashuhn.uniplanner.auth.AuthPrincipal
import dev.cypdashuhn.uniplanner.auth.SessionStore
import dev.cypdashuhn.uniplanner.auth.StaticToken
import dev.cypdashuhn.uniplanner.auth.StaticTokenAuthenticator
import dev.cypdashuhn.uniplanner.auth.TokenSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpAuthTest {
    private val rawToken = "test-token-0123456789abcdef0123456789abcdef"
    private val principal = AuthPrincipal("cyp", mapOf("vault" to "personal"))
    private val clock = TestClock(Instant.parse("2026-10-22T20:00:00Z"))
    private val sessions = SessionStore(clock, TestTokenSource())
    private val auth = HttpAuth(
        authenticator = StaticTokenAuthenticator(listOf(StaticToken.fromRaw(rawToken, principal))),
        sessions = sessions,
        sessionTtl = Duration.ofHours(12),
    )

    @Test
    fun `authenticates bearer token`() {
        assertEquals(
            principal,
            auth.authenticate(AuthRequest(authorization = "Bearer $rawToken")),
        )
    }

    @Test
    fun `rejects invalid bearer token`() {
        assertNull(auth.authenticate(AuthRequest(authorization = "Bearer wrong")))
        assertNull(auth.authenticate(AuthRequest()))
    }

    @Test
    fun `logs in and authenticates with cookie`() {
        val login = assertNotNull(auth.login(rawToken))
        val sessionCookie = login.setCookie.substringBefore(';')

        assertEquals(principal, login.principal)
        assertEquals(login.session.token, auth.sessionToken(sessionCookie))
        assertEquals(principal, auth.authenticate(AuthRequest(cookie = sessionCookie)))
    }

    @Test
    fun `reads session cookie beside other cookies`() {
        val login = auth.login(rawToken)!!
        val cookieHeader = "theme=dark; ${login.setCookie.substringBefore(';')}; other=1"

        assertEquals(principal, auth.authenticate(AuthRequest(cookie = cookieHeader)))
    }

    @Test
    fun `falls back to bearer when session cookie is invalid`() {
        assertEquals(
            principal,
            auth.authenticate(
                AuthRequest(
                    authorization = "Bearer $rawToken",
                    cookie = "upl_session=missing",
                ),
            ),
        )
    }

    @Test
    fun `login rejects invalid token`() {
        assertNull(auth.login(null))
        assertNull(auth.login("wrong"))
    }

    @Test
    fun `login sets hardened cookie`() {
        val login = auth.login(rawToken)!!
        val cookie = login.setCookie

        assertTrue(cookie.contains("HttpOnly"))
        assertTrue(cookie.contains("Secure"))
        assertTrue(cookie.contains("SameSite=Lax"))
        assertTrue(cookie.contains("Max-Age=43200"))
    }

    @Test
    fun `login hides session token when printed`() {
        val login = auth.login(rawToken)!!

        assertFalse(login.toString().contains(login.session.token))
        assertFalse(login.setCookie.isEmpty())
    }

    @Test
    fun `logout revokes session and clears cookie`() {
        val login = auth.login(rawToken)!!
        val sessionCookie = login.setCookie.substringBefore(';')

        val cleared = auth.logout(sessionCookie)

        assertNull(auth.authenticate(AuthRequest(cookie = sessionCookie)))
        assertTrue(cleared.endsWith("Max-Age=0"))
    }

    @Test
    fun `expired session no longer authenticates`() {
        val login = auth.login(rawToken)!!
        clock.current = clock.current.plus(Duration.ofHours(12))

        assertNull(auth.authenticate(AuthRequest(cookie = login.setCookie.substringBefore(';'))))
    }
}

private class TestTokenSource : TokenSource {
    private var next = 0

    override fun generate(): String = "session-token-${next++}"
}

private class TestClock(
    var current: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current
}
