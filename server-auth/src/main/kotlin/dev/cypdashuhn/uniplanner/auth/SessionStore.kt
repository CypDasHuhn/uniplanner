package dev.cypdashuhn.uniplanner.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val principal: AuthPrincipal,
    val expiresAt: Instant,
)

class IssuedSession(
    val token: String,
    val session: Session,
) {
    override fun toString(): String = "IssuedSession(principal=${session.principal.id}, token=<redacted>)"
}

class SessionStore(
    private val clock: Clock = Clock.systemUTC(),
    private val tokenSource: TokenSource = SecureTokenSource,
) {
    private val sessions = ConcurrentHashMap<TokenHash, Session>()

    val size: Int get() = sessions.size

    fun create(
        principal: AuthPrincipal,
        ttl: Duration,
    ): IssuedSession {
        require(!ttl.isZero && !ttl.isNegative) { "session ttl must be positive" }
        purgeExpired()

        val expiresAt = clock.instant().plus(ttl)
        while (true) {
            val rawToken = tokenSource.generate()
            val hash = TokenHash.of(rawToken)
            val session = Session(principal, expiresAt)
            if (sessions.putIfAbsent(hash, session) == null) {
                return IssuedSession(rawToken, session)
            }
        }
    }

    fun resolve(rawToken: String?): Session? {
        if (rawToken.isNullOrBlank()) return null

        val hash = TokenHash.of(rawToken)
        val session = sessions[hash] ?: return null
        if (!session.expiresAt.isAfter(clock.instant())) {
            sessions.remove(hash, session)
            return null
        }

        return session
    }

    fun revoke(rawToken: String?): Boolean {
        if (rawToken.isNullOrBlank()) return false
        return sessions.remove(TokenHash.of(rawToken)) != null
    }

    fun purgeExpired(): Int {
        val now = clock.instant()
        var removed = 0
        for ((hash, session) in sessions) {
            if (!session.expiresAt.isAfter(now) && sessions.remove(hash, session)) removed++
        }
        return removed
    }
}
