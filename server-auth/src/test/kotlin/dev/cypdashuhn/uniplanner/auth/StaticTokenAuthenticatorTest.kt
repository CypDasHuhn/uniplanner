package dev.cypdashuhn.uniplanner.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class StaticTokenAuthenticatorTest {
    private val principal = AuthPrincipal("cyp", mapOf("vault" to "personal"))
    private val rawToken = "test-token-0123456789abcdef0123456789abcdef"
    private val authenticator = StaticTokenAuthenticator(listOf(StaticToken.fromRaw(rawToken, principal)))

    @Test
    fun `accepts matching token`() {
        assertEquals(AuthResult.Accepted(principal), authenticator.authenticate(rawToken))
    }

    @Test
    fun `rejects missing token`() {
        assertEquals(AuthResult.Rejected, authenticator.authenticate(null))
        assertEquals(AuthResult.Rejected, authenticator.authenticate(" "))
    }

    @Test
    fun `rejects wrong token`() {
        assertEquals(AuthResult.Rejected, authenticator.authenticate("$rawToken-wrong"))
    }

    @Test
    fun `maps tokens to separate principals`() {
        val otherPrincipal = AuthPrincipal("guest", mapOf("vault" to "shared"))
        val otherToken = "other-token-fedcba9876543210fedcba9876543210"
        val multi = StaticTokenAuthenticator(
            listOf(
                StaticToken.fromRaw(rawToken, principal),
                StaticToken.fromRaw(otherToken, otherPrincipal),
            ),
        )

        assertEquals(AuthResult.Accepted(principal), multi.authenticate(rawToken))
        assertEquals(AuthResult.Accepted(otherPrincipal), multi.authenticate(otherToken))
    }

    @Test
    fun `issued token authenticates`() {
        val issued = StaticToken.issue(principal, TokenSource { "issued-token-0123456789abcdef0123456789abcdef" })
        val issuedAuthenticator = StaticTokenAuthenticator(listOf(issued.token))

        assertEquals(AuthResult.Accepted(principal), issuedAuthenticator.authenticate(issued.rawToken))
    }

    @Test
    fun `issued token hides raw value when printed`() {
        val issued = StaticToken.issue(principal, TokenSource { "issued-token-0123456789abcdef0123456789abcdef" })

        assertFalse(issued.toString().contains(issued.rawToken))
    }

    @Test
    fun `rejects duplicate token hashes`() {
        assertFailsWith<IllegalArgumentException> {
            StaticTokenAuthenticator(
                listOf(
                    StaticToken.fromRaw(rawToken, principal),
                    StaticToken.fromRaw(rawToken, AuthPrincipal("other")),
                ),
            )
        }
    }
}
