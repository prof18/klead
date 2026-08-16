package com.prof18.klead.internal.dom

import org.jsoup.nodes.Element
import java.net.URI

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
    return runCatching {
        URI(baseUrl).resolve(trimmed).toString()
    }.getOrDefault("")
}

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
