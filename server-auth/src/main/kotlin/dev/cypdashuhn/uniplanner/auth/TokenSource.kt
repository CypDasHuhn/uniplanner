package dev.cypdashuhn.uniplanner.auth

import java.security.SecureRandom
import java.util.Base64

fun interface TokenSource {
    fun generate(): String
}

object SecureTokenSource : TokenSource {
    private const val TOKEN_BYTES = 32

    private val random = SecureRandom()

    override fun generate(): String =
        ByteArray(TOKEN_BYTES)
            .also(random::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
