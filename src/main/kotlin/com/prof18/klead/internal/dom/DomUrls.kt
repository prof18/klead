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

internal fun isDangerousUrl(value: String): Boolean {
    val normalized = value.trimStart().lowercase()
    return normalized.startsWith("javascript:") ||
        normalized.startsWith("vbscript:") ||
        normalized.startsWith("data:text/html")
}
