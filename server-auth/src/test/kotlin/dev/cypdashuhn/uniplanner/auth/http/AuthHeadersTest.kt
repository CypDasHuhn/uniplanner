package dev.cypdashuhn.uniplanner.auth.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthHeadersTest {
    @Test
    fun `reads bearer token`() {
        assertEquals("token", bearerToken("Bearer token"))
        assertEquals("token", bearerToken("bearer token"))
        assertEquals("token", bearerToken("  Bearer   token  "))
    }

    @Test
    fun `rejects other schemes and missing values`() {
        assertNull(bearerToken(null))
        assertNull(bearerToken(""))
        assertNull(bearerToken("Basic token"))
        assertNull(bearerToken("Bearer"))
        assertNull(bearerToken("Bearer   "))
    }

    @Test
    fun `reads cookie value`() {
        assertEquals("abc", cookieValue("a=1; upl_session=abc; c=3", "upl_session"))
        assertEquals("abc", cookieValue("upl_session=\"abc\"", "upl_session"))
        assertEquals("abc=def", cookieValue("upl_session=abc=def", "upl_session"))
        assertNull(cookieValue("a=1; c=3", "upl_session"))
        assertNull(cookieValue(null, "upl_session"))
    }

    @Test
    fun `reads duplicate cookies in order`() {
        assertEquals(
            listOf("first", "second"),
            cookieValues("upl_session=first; other=1; upl_session=second", "upl_session"),
        )
    }

    @Test
    fun `ignores malformed cookie parts`() {
        assertEquals(
            listOf("abc"),
            cookieValues("broken; =empty; upl_session=abc; also", "upl_session"),
        )
    }
}
