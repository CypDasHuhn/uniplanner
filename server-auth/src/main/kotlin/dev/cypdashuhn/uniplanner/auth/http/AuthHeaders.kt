package dev.cypdashuhn.uniplanner.auth.http

fun bearerToken(authorization: String?): String? {
    val header = authorization?.trim().orEmpty()
    val separator = header.indexOf(' ')
    if (separator <= 0) return null

    val scheme = header.substring(0, separator)
    if (!scheme.equals("Bearer", ignoreCase = true)) return null

    return header.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
}

fun cookieValue(
    cookieHeader: String?,
    name: String,
): String? = cookieValues(cookieHeader, name).firstOrNull()

fun cookieValues(
    cookieHeader: String?,
    name: String,
): List<String> =
    cookieHeader
        .orEmpty()
        .split(';')
        .mapNotNull { part -> parseCookie(part, name) }

private fun parseCookie(
    part: String,
    name: String,
): String? {
    val cookie = part.trim()
    val separator = cookie.indexOf('=')
    if (separator <= 0) return null
    if (cookie.substring(0, separator).trim() != name) return null

    return cookie
        .substring(separator + 1)
        .trim()
        .removeSurrounding("\"")
        .takeIf { it.isNotEmpty() }
}
