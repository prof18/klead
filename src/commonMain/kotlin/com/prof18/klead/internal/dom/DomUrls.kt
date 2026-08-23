package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.nodes.Element

internal fun Element.absUrlOrEmpty(attr: String): String {
    val rawValue = attr(attr)
    if (isDangerousUrl(rawValue)) return ""
    return absUrl(attr).takeUnless(::isDangerousUrl).orEmpty()
}

internal fun String.toAbsoluteSiteUrl(domain: String): String = when {
    startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true) -> this
    startsWith("/") -> "https://$domain$this"
    else -> "https://$domain/$this"
}

internal fun resolveUrl(baseUrl: String, value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank() || isDangerousUrl(trimmed)) return ""
    return resolveKleadUri(baseUrl, trimmed).orEmpty()
}

internal data class KleadUri(
    val scheme: String?,
    val host: String?,
    val path: String?,
    val rawPath: String?,
    val rawQuery: String?,
    val asciiString: String,
)

internal fun parseKleadUri(value: String): KleadUri? {
    val uri = parseUriReference(value) ?: return null
    return KleadUri(
        scheme = uri.scheme,
        host = uri.authority?.extractHostOrNull(),
        path = uri.rawPath?.decodePercentEncoded(),
        rawPath = uri.rawPath,
        rawQuery = uri.rawQuery,
        asciiString = value.encodeNonAscii(),
    )
}

internal fun resolveKleadUri(baseUrl: String, value: String): String? {
    val base = parseUriReference(baseUrl)?.takeUnless { it.opaque } ?: return null
    val reference = parseUriReference(value) ?: return null

    return when {
        reference.scheme != null -> value

        reference.authority != null -> buildUri(
            scheme = base.scheme,
            authority = reference.authority,
            path = removeDotSegments(reference.rawPath.orEmpty()),
            query = reference.rawQuery,
            fragment = reference.rawFragment,
        )

        value.startsWith("#") -> buildUri(
            base.scheme,
            base.authority,
            base.rawPath.orEmpty(),
            base.rawQuery,
            reference.rawFragment,
        )

        else -> {
            val path = reference.rawPath.orEmpty()
            val resolvedPath = if (path.startsWith('/')) {
                removeDotSegments(path)
            } else {
                removeDotSegments(
                    base.rawPath.orEmpty().substringBeforeLast('/', missingDelimiterValue = "") + "/" + path,
                )
            }
            buildUri(base.scheme, base.authority, resolvedPath, reference.rawQuery, reference.rawFragment)
        }
    }
}

private data class UriReference(
    val scheme: String?,
    val authority: String?,
    val rawPath: String?,
    val rawQuery: String?,
    val rawFragment: String?,
    val opaque: Boolean,
)

private fun parseUriReference(value: String): UriReference? {
    if (value.any { it.isForbiddenUriCharacter() } || value.hasMalformedPercentEscape()) return null

    val fragmentIndex = value.indexOf('#')
    val beforeFragment = value.substring(0, fragmentIndex.takeIf { it >= 0 } ?: value.length)
    val rawFragment = fragmentIndex.takeIf { it >= 0 }?.let { value.substring(it + 1) }
    val queryIndex = beforeFragment.indexOf('?')
    val beforeQuery = beforeFragment.substring(0, queryIndex.takeIf { it >= 0 } ?: beforeFragment.length)
    val rawQuery = queryIndex.takeIf { it >= 0 }?.let { beforeFragment.substring(it + 1) }

    val firstDelimiter = beforeQuery.indexOfAny(charArrayOf('/', ':'))
    val colonIndex = beforeQuery.indexOf(':')
    val scheme = if (colonIndex >= 0 && colonIndex == firstDelimiter) {
        beforeQuery.substring(0, colonIndex).takeIf { URI_SCHEME.matches(it) } ?: return null
    } else {
        if (colonIndex >= 0 && !beforeQuery.startsWith('/')) return null
        null
    }
    val afterScheme = if (scheme == null) beforeQuery else beforeQuery.substring(colonIndex + 1)
    val opaque = scheme != null && !afterScheme.startsWith('/')
    if (opaque) {
        return UriReference(scheme, null, null, null, rawFragment, opaque = true)
    }

    val authority = if (afterScheme.startsWith("//")) {
        afterScheme.substring(2).substringBefore('/')
    } else {
        null
    }
    val rawPath = if (authority == null) {
        afterScheme
    } else {
        afterScheme.substring(2 + authority.length)
    }
    return UriReference(scheme, authority, rawPath, rawQuery, rawFragment, opaque = false)
}

private fun String.extractHostOrNull(): String? {
    val hostAndPort = substringAfterLast('@')
    if (hostAndPort.any { it.code > ASCII_MAX }) return null
    return if (hostAndPort.startsWith('[')) {
        val closingBracket = hostAndPort.indexOf(']')
        closingBracket.takeIf { it > 0 }?.let { hostAndPort.substring(0, it + 1) }
    } else {
        val host = hostAndPort.substringBefore(':')
        val port = hostAndPort.substringAfter(':', missingDelimiterValue = "")
        val validPort = ':' !in hostAndPort || (port.isNotBlank() && port.all { it.isDigit() })
        host.takeIf { validPort && ASCII_HOST.matches(it) }
    }
}

private fun String.decodePercentEncoded(): String {
    if ('%' !in this) return this
    val decoded = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] != '%') {
            decoded.append(this[index++])
            continue
        }
        val bytes = mutableListOf<Byte>()
        while (index + 2 < length && this[index] == '%') {
            bytes += substring(index + 1, index + 3).toInt(16).toByte()
            index += 3
        }
        decoded.append(bytes.toByteArray().decodeToString())
    }
    return decoded.toString()
}

private fun String.encodeNonAscii(): String = buildString(length) {
    for (char in this@encodeNonAscii) {
        if (char.code <= ASCII_MAX) {
            append(char)
        } else {
            char.toString().encodeToByteArray().forEach { byte ->
                append('%')
                append(HEX_DIGITS[(byte.toInt() ushr 4) and 0xF])
                append(HEX_DIGITS[byte.toInt() and 0xF])
            }
        }
    }
}

private fun removeDotSegments(path: String): String {
    val output = mutableListOf<String>()
    val absolute = path.startsWith('/')
    val needsTrailingSlash = path.endsWith('/') || path.endsWith("/.") || path.endsWith("/..")
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (output.isNotEmpty()) output.removeAt(output.lastIndex)
            else -> output += segment
        }
    }
    return buildString {
        if (absolute) append('/')
        append(output.joinToString("/"))
        if (needsTrailingSlash && (isEmpty() || last() != '/')) append('/')
    }
}

private fun buildUri(scheme: String?, authority: String?, path: String, query: String?, fragment: String?): String =
    buildString {
        scheme?.let { append(it).append(':') }
        authority?.let { append("//").append(it) }
        append(path)
        query?.let { append('?').append(it) }
        fragment?.let { append('#').append(it) }
    }

private fun Char.isForbiddenUriCharacter(): Boolean =
    isWhitespace() || code <= 0x1F || code == 0x7F || this in URI_FORBIDDEN_CHARACTERS

private fun String.hasMalformedPercentEscape(): Boolean {
    var index = indexOf('%')
    while (index >= 0) {
        if (index + 2 >= length || this[index + 1].digitToIntOrNull(16) == null ||
            this[index + 2].digitToIntOrNull(16) == null
        ) {
            return true
        }
        index = indexOf('%', startIndex = index + 3)
    }
    return false
}

private val URI_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]*")
private val ASCII_HOST = Regex("[A-Za-z0-9.-]+")
private val URI_FORBIDDEN_CHARACTERS = setOf('<', '>', '\\', '^', '`', '{', '|', '}', '"')
private const val ASCII_MAX = 0x7F
private const val HEX_DIGITS = "0123456789ABCDEF"

// `data:` and `blob:` smuggle a whole document into an attribute, past the script and
// event-handler stripping done elsewhere. Inline images are the one benign use of `data:`, so
// those are allowed and every other media type is rejected. Relative URLs (no scheme) are always
// allowed.
internal fun isDangerousUrl(value: String): Boolean {
    val prefix = value.urlSchemePrefix()
    return prefix.startsWith("javascript:") ||
        prefix.startsWith("vbscript:") ||
        prefix.startsWith("blob:") ||
        (prefix.startsWith("data:") && !prefix.startsWith("data:image/"))
}

// Browsers strip whitespace and control characters before resolving a scheme, so `java\tscript:`
// still runs as `javascript:`. Drop them here too, and lowercase, so a padded scheme cannot slip
// past the prefix checks. Only the leading [URL_SCHEME_PREFIX_LENGTH] characters are needed —
// `data:image/` is the longest prefix any caller tests — so long URLs cost a bounded copy.
private fun String.urlSchemePrefix(): String {
    val prefix = StringBuilder(URL_SCHEME_PREFIX_LENGTH)
    for (char in this) {
        if (char.isUrlSchemeNoise()) continue
        prefix.append(char.lowercaseChar())
        if (prefix.length == URL_SCHEME_PREFIX_LENGTH) break
    }
    return prefix.toString()
}

private fun Char.isUrlSchemeNoise(): Boolean = code <= 0x1F || code == 0x7F || code == 0xFEFF || isWhitespace()

private const val URL_SCHEME_PREFIX_LENGTH = 16
