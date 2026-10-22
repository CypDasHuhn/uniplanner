package dev.cypdashuhn.uniplanner.auth.http

import dev.cypdashuhn.uniplanner.auth.AuthPrincipal
import dev.cypdashuhn.uniplanner.auth.AuthResult
import dev.cypdashuhn.uniplanner.auth.IssuedSession
import dev.cypdashuhn.uniplanner.auth.SessionStore
import dev.cypdashuhn.uniplanner.auth.TokenAuthenticator
import java.time.Duration

data class AuthRequest(
    val authorization: String? = null,
    val cookie: String? = null,
)

data class LoginResponse(
    val principal: AuthPrincipal,
    val session: IssuedSession,
    val setCookie: String,
) {
    override fun toString(): String = "LoginResponse(principal=${principal.id}, setCookie=<redacted>)"
}

class HttpAuth(
    private val authenticator: TokenAuthenticator,
    private val sessions: SessionStore,
    private val cookie: SessionCookie = SessionCookie(),
    private val sessionTtl: Duration = Duration.ofHours(12),
) {
    init {
        require(!sessionTtl.isZero && !sessionTtl.isNegative) { "session ttl must be positive" }
    }

    fun authenticate(request: AuthRequest): AuthPrincipal? {
        sessionPrincipal(request.cookie)?.let { return it }

        val token = bearerToken(request.authorization) ?: return null
        return principalFor(token)
    }

    fun login(presentedToken: String?): LoginResponse? {
        val principal = principalFor(presentedToken) ?: return null
        val session = sessions.create(principal, sessionTtl)
        return LoginResponse(principal, session, cookie.create(session.token, sessionTtl))
    }

    fun logout(cookieHeader: String?): String {
        sessions.revoke(sessionToken(cookieHeader))
        return cookie.clear()
    }

    fun sessionToken(cookieHeader: String?): String? =
        cookieValue(cookieHeader, cookie.name)

    private fun sessionPrincipal(cookieHeader: String?): AuthPrincipal? =
        cookieValues(cookieHeader, cookie.name)
            .firstNotNullOfOrNull { sessions.resolve(it)?.principal }

    private fun principalFor(token: String?): AuthPrincipal? =
        when (val result = authenticator.authenticate(token)) {
            is AuthResult.Accepted -> result.principal
            AuthResult.Rejected -> null
        }
}
