package dev.defuddle.dom

import org.jsoup.nodes.Element
import java.net.URI

fun Element.absUrlOrEmpty(attr: String): String {
    val rawValue = attr(attr)
    if (isDangerousUrl(rawValue)) return ""
    return absUrl(attr).takeUnless(::isDangerousUrl).orEmpty()
}

fun resolveUrl(baseUrl: String, value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank() || isDangerousUrl(trimmed)) return ""
    return runCatching {
        URI(baseUrl).resolve(trimmed).toString()
    }.getOrDefault("")
}

fun isDangerousUrl(value: String): Boolean {
    val normalized = value.trimStart().lowercase()
    return normalized.startsWith("javascript:") ||
        normalized.startsWith("vbscript:") ||
        normalized.startsWith("data:text/html")
}
