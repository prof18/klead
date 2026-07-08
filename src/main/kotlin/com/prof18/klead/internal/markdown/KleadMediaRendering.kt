package com.prof18.klead.internal.markdown

import com.prof18.klead.internal.dom.attrTrimmedOrNull
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.resolveUrl
import com.prof18.klead.internal.media.TrustedEmbeds
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

// Leaf renderers for media and link destinations: images, video embeds, trusted raw iframes,
// inline math, and link/image URL resolution. None of these recurse back into the block or
// inline renderer, so they live outside the stateful Renderer.

internal fun renderImage(element: Element, baseUrl: String): String {
    val sources = listOfNotNull(
        largestSrcsetUrl(element.attr("srcset")),
        element.attr("src").trim().takeIf { it.isNotBlank() },
    ).distinct()
    for (src in sources) {
        if (isDangerousUrl(src) || element.isPlaceholderImage(src)) continue
        val url = resolveUrl(baseUrl, src)
        if (url.isNotBlank()) {
            return "![${escapeInline(element.attr("alt"))}](${escapeDestination(url)})"
        }
    }
    return ""
}

internal fun renderEmbeddedMedia(element: Element, baseUrl: String): String {
    val href = element.attr("data-klead-video-url").trim().ifBlank {
        TrustedEmbeds.markdownMediaFromUrl(element.attr("src").trim())?.watchUrl.orEmpty()
    }
    if (href.isNotBlank()) {
        return renderMarkdownMedia(href, baseUrl, preserveLeadingSpacer = element.hasKleadLeadingSpacer())
    }

    return renderTrustedRawIframe(element, baseUrl)
}

internal fun renderLinkedImage(element: Element, baseUrl: String): String? {
    val image = element.linkedImageOnlyChild() ?: return null

    val url = element.safeResolvedHref(baseUrl) ?: return null
    val imageMarkdown = renderImage(image, baseUrl)
    return if (imageMarkdown.isBlank()) {
        null
    } else {
        "[$imageMarkdown](${escapeDestination(url.withRootSlashForBareOrigin())})"
    }
}

internal fun Element.linkedImageOnlyChild(): Element? {
    if (normalName() != "a") return null
    if (hasNonImageText()) return null
    return children().singleOrNull { it.normalName() == "img" }
}

internal fun Element.safeResolvedHref(baseUrl: String): String? {
    val href = attr("href").trim()
    if (href.isBlank() || isDangerousUrl(href)) return null
    return resolveUrl(baseUrl, href).takeIf { it.isNotBlank() }
}

internal fun linkDestination(url: String, title: String, visibleText: String): String {
    val destination = escapeDestination(url)
    val cleanTitle = title.trim().takeIf { it.isNotBlank() } ?: return destination
    if (cleanTitle.normalizedLinkTitleText().equals(visibleText.normalizedLinkTitleText(), ignoreCase = true)) {
        return destination
    }
    return "$destination \"${escapeTitle(cleanTitle)}\""
}

internal fun renderMath(element: Element): String? {
    val latex = element.attrTrimmedOrNull("data-latex") ?: return null
    val display = element.hasClass("display") || element.attr("display") == "block"
    return if (display) "$$\n$latex\n$$" else "$$latex$"
}

private fun renderMarkdownMedia(href: String, baseUrl: String, preserveLeadingSpacer: Boolean): String {
    if (isDangerousUrl(href)) return ""
    val url = resolveUrl(baseUrl, href)
    if (url.isBlank()) return ""
    val media = "![](${escapeDestination(url)})"
    return if (preserveLeadingSpacer) "\n$media" else media
}

private fun Element.hasKleadLeadingSpacer(): Boolean = attr("data-klead-leading-spacer") == "true"

private fun renderTrustedRawIframe(element: Element, baseUrl: String): String {
    val src = element.attr("src").trim()
    if (src.isBlank() || isDangerousUrl(src)) return ""
    val url = resolveUrl(baseUrl, src)
    if (url.isBlank() || !TrustedEmbeds.isTrustedRawIframeSrc(url)) return ""

    val attributes = buildList {
        add("src" to url)
        rawIframeAttributes.forEach { name ->
            if (element.hasAttr(name)) {
                add(name to element.attr(name))
            }
        }
    }
    val renderedAttributes = attributes.joinToString(" ") { (name, value) ->
        "$name=\"${escapeHtmlAttribute(value)}\""
    }
    return "<iframe $renderedAttributes></iframe>"
}

private fun Element.hasNonImageText(): Boolean = childNodes().any { node ->
    when (node) {
        is TextNode -> node.text().isNotBlank()
        is Element -> node.normalName() != "img" && node.text().isNotBlank()
        else -> false
    }
}
