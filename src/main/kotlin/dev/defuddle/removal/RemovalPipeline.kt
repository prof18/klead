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
        if (options.removeImages) {
            removeImages(content, debug)
        } else {
            if (options.removeSmallImages) {
                removeSmallImages(content, debug)
            }
            deduplicateImages(content, debug)
            removeCoverImage(content, metadataImage, debug)
        }
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
            if (isTrailingRecommendationBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing recommendation block")
                continue
            }
            if (isTrailingTagBlock(element)) {
                recordAndRemove(element, debug, "removeContentPatterns", null, "trailing tag list")
                continue
            }
            break
        }
    }

    private fun removeImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (element in content.select("picture, img").toList()) {
            if (element.normalName() == "img" && element.parents().any { it.normalName() == "picture" }) continue
            recordAndRemove(element, debug, "removeImages", element.normalName(), "removeImages option")
        }
    }

    private fun removeSmallImages(
        content: Element,
        debug: MutableList<RemovalRecord>,
    ) {
        for (image in content.select("img").toList()) {
            if (image.isSmallImage()) {
                recordAndRemove(image, debug, "removeSmallImages", "img", "small image dimensions")
            }
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

    private fun isTrailingRecommendationBlock(element: Element): Boolean {
        val heading = element.selectFirst("h1, h2, h3, h4, h5, h6")?.text()
            ?: element.ownText()
        if (!RECOMMENDATION_HEADING_PATTERN.containsMatchIn(heading.trim())) return false

        val linkCount = element.select("a").size
        val articleCount = element.select("article").size
        val imageCount = element.select("img, figure, picture").size
        return linkCount >= RECOMMENDATION_MIN_LINKS ||
            articleCount >= RECOMMENDATION_MIN_ARTICLES ||
            imageCount >= RECOMMENDATION_MIN_IMAGES
    }

    private fun isTrailingTagBlock(element: Element): Boolean {
        val text = element.text().trim()
        if (!TRAILING_TAG_LABEL_PATTERN.containsMatchIn(text)) return false

        val linkCount = element.select("a").size
        val tagLinkCount = element.select("""a[href*="/tag/"], a[rel~=tag]""").size
        val wordCount = text.split(Regex("""\s+""")).count { it.isNotBlank() }

        return tagLinkCount >= TRAILING_TAG_MIN_LINKS ||
            (linkCount >= TRAILING_TAG_MIN_LINKS && wordCount <= TRAILING_TAG_MAX_WORDS)
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

    private fun Element.isSmallImage(): Boolean {
        val width = dimension("width")
        val height = dimension("height")
        return if (width != null && height != null) {
            width > 0 && height > 0 && width <= SMALL_IMAGE_MAX_DIMENSION && height <= SMALL_IMAGE_MAX_DIMENSION
        } else {
            false
        }
    }

    private fun Element.dimension(name: String): Int? =
        attr(name).dimensionValue()
            ?: Regex("""$name\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)
                .find(attr("style"))
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

    private fun String.dimensionValue(): Int? =
        Regex("""^\s*(\d+)""").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

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
        "breadcrumb",
        "promo",
        "recommend",
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

    private val RECOMMENDATION_HEADING_PATTERN = Regex(
        """\b(recommended|related|more stories|more from|read more|you may also like|consigliati|altre storie)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_TAG_LABEL_PATTERN = Regex(
        """^\s*(tags?|tagged|etichette?)\s*:""",
        RegexOption.IGNORE_CASE,
    )

    private const val RECOMMENDATION_MIN_LINKS = 2
    private const val RECOMMENDATION_MIN_ARTICLES = 2
    private const val RECOMMENDATION_MIN_IMAGES = 2
    private const val TRAILING_TAG_MIN_LINKS = 1
    private const val TRAILING_TAG_MAX_WORDS = 16
    private const val SMALL_IMAGE_MAX_DIMENSION = 64
}
