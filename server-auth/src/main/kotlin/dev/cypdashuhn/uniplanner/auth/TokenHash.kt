package dev.cypdashuhn.uniplanner.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class TokenHash private constructor(
    val value: String,
) {
    fun matches(other: TokenHash): Boolean =
        MessageDigest.isEqual(
            value.toByteArray(StandardCharsets.UTF_8),
            other.value.toByteArray(StandardCharsets.UTF_8),
        )

    override fun equals(other: Any?): Boolean = other is TokenHash && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        fun of(rawToken: String): TokenHash {
            require(rawToken.isNotBlank()) { "token must not be blank" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.toByteArray(StandardCharsets.UTF_8))
            return TokenHash(Base64.getEncoder().encodeToString(digest))
        }
    }
}
