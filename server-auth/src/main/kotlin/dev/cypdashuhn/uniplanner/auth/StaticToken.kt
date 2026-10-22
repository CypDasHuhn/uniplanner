package dev.cypdashuhn.uniplanner.auth

class StaticToken private constructor(
    val hash: TokenHash,
    val principal: AuthPrincipal,
) {
    companion object {
        fun fromRaw(
            rawToken: String,
            principal: AuthPrincipal,
        ): StaticToken = StaticToken(TokenHash.of(rawToken), principal)

        fun issue(
            principal: AuthPrincipal,
            tokenSource: TokenSource = SecureTokenSource,
        ): IssuedToken {
            val rawToken = tokenSource.generate()
            return IssuedToken(rawToken, StaticToken(TokenHash.of(rawToken), principal))
        }
    }
}

class IssuedToken(
    val rawToken: String,
    val token: StaticToken,
) {
    override fun toString(): String = "IssuedToken(principal=${token.principal.id}, token=<redacted>)"
}

class StaticTokenAuthenticator(
    tokens: Collection<StaticToken>,
) : TokenAuthenticator {
    private val tokens = tokens.toList()

    init {
        require(this.tokens.isNotEmpty()) { "at least one static token is required" }
        require(this.tokens.distinctBy { it.hash.value }.size == this.tokens.size) { "duplicate static token" }
    }

    override fun authenticate(presentedToken: String?): AuthResult {
        if (presentedToken.isNullOrBlank()) return AuthResult.Rejected

        val presentedHash = TokenHash.of(presentedToken)
        var matched: StaticToken? = null
        for (token in tokens) {
            if (token.hash.matches(presentedHash)) matched = token
        }

        return matched?.let { AuthResult.Accepted(it.principal) } ?: AuthResult.Rejected
    }
}
