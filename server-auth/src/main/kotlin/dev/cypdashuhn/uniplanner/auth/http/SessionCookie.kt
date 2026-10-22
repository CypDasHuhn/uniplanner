package dev.cypdashuhn.uniplanner.auth.http

import java.time.Duration

enum class SameSite(
    val value: String,
) {
    Lax("Lax"),
    Strict("Strict"),
    None("None"),
}

data class SessionCookie(
    val name: String = "upl_session",
    val path: String = "/",
    val secure: Boolean = true,
    val sameSite: SameSite = SameSite.Lax,
) {
    init {
        require(name.isNotEmpty() && name.none { it == '=' || it == ';' || it == ',' || it.isWhitespace() }) {
            "invalid cookie name"
        }
        require(path.startsWith("/") && path.none { it == ';' || it.isWhitespace() }) { "invalid cookie path" }
        require(secure || sameSite != SameSite.None) { "SameSite=None requires Secure" }
    }

    fun create(
        token: String,
        ttl: Duration,
    ): String {
        require(token.isNotEmpty() && token.none { it == ';' || it.isWhitespace() }) { "invalid cookie token" }
        return render("$name=$token", ttl.toSeconds().coerceAtLeast(1))
    }

    fun clear(): String = render("$name=", 0)

    private fun render(
        value: String,
        maxAge: Long,
    ): String = buildString {
        append(value)
        append("; Path=").append(path)
        append("; HttpOnly")
        if (secure) append("; Secure")
        append("; SameSite=").append(sameSite.value)
        append("; Max-Age=").append(maxAge)
    }
}
