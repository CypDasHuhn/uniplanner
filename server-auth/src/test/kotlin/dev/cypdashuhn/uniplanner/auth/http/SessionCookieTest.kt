package dev.cypdashuhn.uniplanner.auth.http

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionCookieTest {
    @Test
    fun `creates hardened cookie`() {
        val cookie = SessionCookie()

        assertEquals(
            "upl_session=token; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=60",
            cookie.create("token", Duration.ofMinutes(1)),
        )
    }

    @Test
    fun `clears cookie`() {
        assertEquals(
            "upl_session=; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=0",
            SessionCookie().clear(),
        )
    }

    @Test
    fun `supports non-secure local cookies`() {
        val cookie = SessionCookie(secure = false)

        assertTrue(cookie.create("token", Duration.ofMinutes(1)).contains("HttpOnly"))
    }

    @Test
    fun `requires secure for same-site none`() {
        assertFailsWith<IllegalArgumentException> {
            SessionCookie(secure = false, sameSite = SameSite.None)
        }
    }

    @Test
    fun `rejects invalid attributes`() {
        assertFailsWith<IllegalArgumentException> { SessionCookie(name = "bad name") }
        assertFailsWith<IllegalArgumentException> { SessionCookie(path = "relative") }
    }
}
