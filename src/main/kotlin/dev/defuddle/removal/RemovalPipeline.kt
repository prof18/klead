package dev.defuddle.removal

import dev.defuddle.DefuddleOptions
import dev.defuddle.dom.removeSafely
import dev.defuddle.dom.selectSafe
import org.jsoup.nodes.Element

data class RemovalRecord(
    val step: String,
    val selector: String?,
    val reason: String,
    val preview: String,
)

object RemovalPipeline {
    fun apply(
        content: Element,
        options: DefuddleOptions,
        debug: MutableList<RemovalRecord>,
        metadataImage: String? = null,
    ) {
        if (options.removeHiddenElements) {
            removeHiddenElements(content, debug)
        }
        if (options.removeExactSelectors) {
            removeExactSelectors(content, debug)
        }
        if (options.removePartialSelectors) {
            removePartialSelectors(content, debug)
        }
        if (options.removeLowScoring) {
            removeLowScoringBlocks(content, debug)
        }
        if (options.removeContentPatterns) {
            removeContentPatterns(content, debug)
        }
        deduplicateImages(content, debug)
        removeCoverImage(content, metadataImage, debug)
    }

    private fun removeHiddenElements(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("*").toList()) {
            val reason = hiddenReason(element) ?: continue
            if (isMathWrapper(element)) continue
            debug += RemovalRecord(
                step = "removeHiddenElements",
                selector = hiddenSelector(element),
                reason = reason,
                preview = element.text().take(100),
            )
            element.removeSafely()
        }
    }

    private fun hiddenReason(element: Element): String? {
        if (element.hasAttr("hidden")) return "hidden attribute"
        if (element.attr("aria-hidden").equals("true", ignoreCase = true)) return "aria-hidden"
        val style = element.attr("style").lowercase().replace(" ", "")
        if ("display:none" in style) return "display:none"
        if ("visibility:hidden" in style) return "visibility:hidden"
        if (Regex("""(?:^|;)opacity:0(?:\.0+)?(?:;|$)""").containsMatchIn(style)) return "opacity:0"
        val classes = element.classNames()
        if (classes.any { it == "hidden" || it == "invisible" || it.endsWith(":hidden") || it.endsWith(":invisible") }) {
            return "hidden class"
        }
        return null
    }

    private fun isMathWrapper(element: Element): Boolean {
        val className = element.className().lowercase()
        return element.tagName().equals("math", ignoreCase = true) ||
            element.selectFirst("math, annotation[encoding*=tex]") != null ||
            "math" in className ||
            "katex" in className ||
            "mathjax" in className
    }

    private fun hiddenSelector(element: Element): String =
        when {
            element.id().isNotBlank() -> "#${element.id()}"
            element.className().isNotBlank() -> "${element.tagName()}.${element.classNames().joinToString(".")}"
            else -> element.tagName()
        }

    private fun removeExactSelectors(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (selector in EXACT_SELECTORS) {
            for (element in content.selectSafe(selector).toList()) {
                if (isProtected(element)) continue
                recordAndRemove(element, debug, "removeExactSelectors", selector, "exact clutter selector")
            }
        }
    }

    private fun removePartialSelectors(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("*").toList()) {
            if (isProtected(element)) continue
            val haystack = partialHaystack(element)
            if (PARTIAL_PATTERNS.any { it in haystack }) {
                recordAndRemove(element, debug, "removePartialSelectors", null, "partial clutter attribute")
            }
        }
    }

    private fun removeLowScoringBlocks(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("section, aside, div, ul, ol").toList()) {
            if (isProtected(element) || isLikelyProse(element)) continue
            val text = element.text()
            val linkText = element.select("a").sumOf { it.text().length }
            val linkDensity = if (text.isBlank()) 0.0 else linkText.toDouble() / text.length
            val linkCount = element.select("a").size
            if (linkCount >= 3 && linkDensity > 0.55) {
                recordAndRemove(element, debug, "removeLowScoring", null, "link-heavy low scoring block")
            }
        }
    }

    private fun removeContentPatterns(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.children().toList().asReversed()) {
            val text = element.text().trim()
            if (text.isBlank()) continue
            if (SUBSCRIBE_PATTERN.containsMatchIn(text) && text.length < 180) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing subscribe call to action")
                continue
            }
            break
        }
    }

    private fun deduplicateImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        val seen = mutableSetOf<String>()
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (!seen.add(key)) {
                recordAndRemove(image, debug, "deduplicateImages", "img[src]", "duplicate image")
            }
        }
    }

    private fun removeCoverImage(
        content: Element,
        metadataImage: String?,
        debug: MutableList<RemovalRecord>,
    ) {
        val coverKey = metadataImage?.trim()?.takeIf { it.isNotBlank() } ?: return
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (key == coverKey) {
                recordAndRemove(image, debug, "removeCoverImage", "img[src]", "duplicates metadata image")
            }
        }
    }

    private fun isProtected(element: Element): Boolean {
        val hints = partialHaystack(element)
        return element.`is`("pre, code, figure, picture, table, math, blockquote") ||
            element.parents().any { it.`is`("pre, code, figure, picture, table, math, blockquote") } ||
            "footnote" in hints ||
            "footnotes" in hints ||
            "callout" in hints ||
            "admonition" in hints
    }

    private fun isLikelyProse(element: Element): Boolean {
        val paragraphs = element.select("p").count { it.text().split(Regex("""\s+""")).size >= 8 }
        if (paragraphs >= 1) return true
        val text = element.text()
        val words = text.split(Regex("""\s+""")).count { it.isNotBlank() }
        return words >= 35 && text.count { it == '.' || it == ',' } >= 2
    }

    private fun partialHaystack(element: Element): String {
        val attrs = element.attributes().asList().joinToString(" ") { attribute ->
            if (attribute.key.startsWith("data-")) "${attribute.key} ${attribute.value}" else attribute.value
        }
        return "${element.id()} ${element.className()} $attrs".lowercase()
    }

    private fun recordAndRemove(
        element: Element,
        debug: MutableList<RemovalRecord>,
        step: String,
        selector: String?,
        reason: String,
    ) {
        debug += RemovalRecord(
            step = step,
            selector = selector,
            reason = reason,
            preview = element.text().take(100),
        )
        element.removeSafely()
    }

    private fun Element.imageKey(): String? =
        absUrl("src").ifBlank { attr("src").trim() }.ifBlank { null }

    private val EXACT_SELECTORS = listOf(
        "nav",
        "footer",
        "form",
        "button",
        "input",
        "select",
        "textarea",
        "[role=navigation]",
        ".ad",
        ".ads",
        ".advertisement",
        ".comments",
        ".comment",
        ".share",
        ".sharing",
        ".related",
        ".related-posts",
        ".toc",
        ".table-of-contents",
    )

    private val PARTIAL_PATTERNS = listOf(
        "advert",
        "promo",
        "related",
        "share",
        "sidebar",
        "sponsor",
        "subscribe",
        "newsletter",
    )

    private val SUBSCRIBE_PATTERN = Regex(
        """\b(subscribe|newsletter|weekly updates|product announcements)\b""",
        RegexOption.IGNORE_CASE,
    )
}
