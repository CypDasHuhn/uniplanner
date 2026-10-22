package dev.cypdashuhn.uniplanner.auth

data class AuthPrincipal(
    val id: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "principal id must not be blank" }
    }
}

sealed interface AuthResult {
    data class Accepted(
        val principal: AuthPrincipal,
    ) : AuthResult

    data object Rejected : AuthResult
}

fun interface TokenAuthenticator {
    fun authenticate(presentedToken: String?): AuthResult
}
