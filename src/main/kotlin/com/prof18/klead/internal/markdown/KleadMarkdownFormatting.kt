package com.prof18.klead.internal.markdown

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal fun renderSvg(element: Element): String {
    if (!element.hasRenderableSvgContent()) return ""
    val clone = element.clone()
    clone.applySvgStyleFallbacks()
    clone.select("[class]").removeAttr("class")
    return clone.outerHtml()
        .trim()
        .replace(tagGapWhitespacePattern, "><")
        .replace(svgTextLabelGroupSpacingPattern, "</text> $1")
        .replace(svgTextLabelPathSpacingPattern, "</text> $1")
        .replace(svgSelfClosingTagPattern) { match ->
            "<${match.groupValues[1]}${match.groupValues[2]}></${match.groupValues[1]}>"
        }
}

internal fun Element.applySvgStyleFallbacks() {
    select("*").forEach { element ->
        element.applySvgAttributeValueFallbacks()
        element.applySvgClassFallbacks()
    }
    select("line.gridline").forEach { line ->
        line.prependMissingSvgAttributes(
            "stroke-opacity" to "0.2",
            "stroke" to "currentColor",
        )
    }
    select("path.path-area").forEach { path ->
        path.prependMissingSvgAttributes("fill" to "none")
    }
    select("path.path-line").forEach { path ->
        path.prependMissingSvgAttributes(
            "stroke" to "currentColor",
            "fill" to "none",
        )
    }
}

internal fun Element.applySvgAttributeValueFallbacks() {
    for (attributeName in svgColorAttributes) {
        val fallback = svgAttributeValueFallbacks[attr(attributeName)] ?: continue
        attr(attributeName, fallback)
    }
}

internal fun Element.applySvgClassFallbacks() {
    val classes = classNames()
    svgClassAttributeFallbacks.forEach { (className, fallback) ->
        if (className in classes) {
            prependMissingSvgAttributes(fallback)
        }
    }

    val styleFallbacks = buildList {
        if ("text-[14px]" in classes) add("font-size:14px")
        if ("font-semibold" in classes) add("font-weight:600")
    }
    if (styleFallbacks.isNotEmpty()) {
        prependMissingSvgAttributes("style" to styleFallbacks.joinToString(";"))
    }
}

internal fun Element.prependMissingSvgAttributes(vararg defaults: Pair<String, String>) {
    val missing = defaults.filterNot { (name, _) -> hasAttr(name) }
    if (missing.isEmpty()) return

    val existing = attributes().asList().map { attribute -> attribute.key to attribute.value }
    clearAttributes()
    missing.forEach { (name, value) -> attr(name, value) }
    existing.forEach { (name, value) -> attr(name, value) }
}

internal fun Element.hasRenderableSvgContent(): Boolean =
    !isSmallIconSvg() && (select(svgRenderableElementSelector).isNotEmpty() || text().isNotBlank())

internal fun Element.isSmallIconSvg(): Boolean {
    if (text().isNotBlank()) return false
    val viewBox = attr("viewBox")
        .trim()
        .split(svgNumberDelimiter)
        .mapNotNull { it.toDoubleOrNull() }
    if (viewBox.size == 4 && viewBox[2] <= SVG_ICON_MAX_SIZE && viewBox[3] <= SVG_ICON_MAX_SIZE) return true

    val width = attr("width").svgLengthValue()
    val height = attr("height").svgLengthValue()
    return width != null && height != null && width <= SVG_ICON_MAX_SIZE && height <= SVG_ICON_MAX_SIZE
}

internal fun String.svgLengthValue(): Double? {
    val trimmed = trim().lowercase()
    val numeric = trimmed.removeSuffix("px").removeSuffix("em").toDoubleOrNull() ?: return null
    return if (trimmed.endsWith("em")) numeric * CSS_EM_SIZE else numeric
}

private const val SVG_ICON_MAX_SIZE = 32.0
private const val CSS_EM_SIZE = 16.0

internal fun largestSrcsetUrl(srcset: String): String? = srcset.split(srcsetDelimiter)
    .mapNotNull { candidate ->
        val parts = candidate.trim().split(srcsetWhitespacePattern)
        val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
        url to width
    }
    .maxByOrNull { it.second }
    ?.first

internal fun Element.isPlaceholderImage(src: String): Boolean {
    val normalized = src.trimStart().lowercase()
    if (
        normalized.startsWith("data:image/png") &&
        attr("data-lqip").equals("true", ignoreCase = true) &&
        attr("alt").isNotBlank()
    ) {
        return false
    }
    return normalized.startsWith("data:image/")
}

internal fun imageDedupKey(element: Element): String {
    val alt = element.attr("alt").trim()
    val source = largestSrcsetUrl(element.attr("srcset")) ?: element.attr("src")
    val family = source.normalizedImageFamily()
    return if (alt.isBlank()) "src:$family" else "alt:$alt|src:$family"
}

internal fun String.normalizedImageFamily(): String = substringBefore('?')
    .substringBefore('#')
    .replace(imageDimensionSuffixPattern, "")
    .replace(imageFileExtensionPattern, "")

internal fun codeSpan(text: String): String {
    val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
    val ticks = "`".repeat(maxTicks + 1)
    return if ("`" in text) "$ticks $text $ticks" else "$ticks$text$ticks"
}

internal fun renderCodeSpanContent(element: Element): String = element.childNodes().joinToString("") { node ->
    when (node) {
        is TextNode -> node.wholeText
        is Element -> renderCodeSpanElement(node)
        else -> ""
    }
}

internal fun renderCodeSpanElement(element: Element): String {
    val content = renderCodeSpanContent(element)
    return when (element.normalName()) {
        "strong", "b" -> "**$content**"
        "em", "i" -> "*$content*"
        "del", "s" -> "~~$content~~"
        "br" -> " "
        else -> content
    }
}

internal fun codeFence(text: String): String {
    val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
    return "`".repeat((maxTicks + 1).coerceAtLeast(3))
}

internal fun languageFrom(element: Element?): String? {
    if (element == null) return null
    val languageClass = element.classNames().firstOrNull { it.startsWith("language-") }
    if (languageClass != null) return languageClass.removePrefix("language-")
    return if (element.hasClass("hl")) {
        element.classNames().firstOrNull { className ->
            className !in highlightCodeClassNoise && codeLanguageClass.matches(className)
        }
    } else {
        null
    }
}

internal fun escapeInline(text: String): String = text
    .replace("\uFEFF", "")
    .normalizePlaceholderDots()
    .normalizeSpacedEllipses()
    .replace('\u00A0', ' ')
    .replace('\u202F', ' ')
    .replace("\\", "\\\\")
    .replace("`", "\\`")
    .replace("*", "\\*")
    .replace("_", "\\_")
    .replace("[", "\\[")
    .replace("]", "\\]")

internal fun String.normalizePlaceholderDots(): String = placeholderDotsPattern.replace(this) { match ->
    "${match.groupValues[1]}.."
}

internal fun String.normalizeSpacedEllipses(): String = spacedEllipsisPattern.replace(this, "...")

internal fun escapeDestination(url: String): String {
    val escaped = url.replace("(", "\\(").replace(")", "\\)")
    return if (escaped.any { it.isWhitespace() }) "<$escaped>" else escaped
}

internal fun escapeTitle(title: String): String = title
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

internal fun String.normalizedLinkTitleText(): String = replace(linkTitleWhitespacePattern, " ").trim()

internal fun String.withRootSlashForBareOrigin(): String = if (bareOriginUrl.matches(this)) "$this/" else this

internal fun escapeHtmlAttribute(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")

internal fun String.escapeTableCell(): String = replace("|", "\\|").replace("\n", " ").trim()
