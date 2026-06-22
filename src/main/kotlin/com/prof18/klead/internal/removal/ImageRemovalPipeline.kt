package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal object ImageRemovalPipeline {
    fun apply(
        content: Element,
        metadataImage: String?,
        debug: MutableList<RemovalRecord>,
        hintFor: (Element) -> String,
    ) {
        removeSmallImages(content, debug)
        deduplicateImages(content, debug)
        removeCoverImage(content, metadataImage, debug, hintFor)
    }

    private fun removeSmallImages(content: Element, debug: MutableList<RemovalRecord>) {
        for (image in content.select("img").toList()) {
            if (image.isSmallImage() && !image.isLinkedAuthorImage()) {
                recordAndRemove(image, debug, "removeSmallImages", "img", "small image dimensions")
            }
        }
    }

    private fun deduplicateImages(content: Element, debug: MutableList<RemovalRecord>) {
        val seen = mutableMapOf<String, Element>()
        val seenVariants = mutableMapOf<String, Element>()
        for (image in content.select("img[src]").toList()) {
            if (!image.isDeduplicableImage()) continue
            val key = image.imageKey() ?: continue
            val firstImage = seen[key]
            if (firstImage == null) {
                seen[key] = image
            } else if (!image.isRepeatedCaptionedFigureImage(firstImage)) {
                recordAndRemove(image, debug, "deduplicateImages", "img[src]", "duplicate image")
                continue
            }

            val variantKey = imageVariantKey(key) ?: continue
            val firstVariant = seenVariants[variantKey]
            if (firstVariant == null) {
                seenVariants[variantKey] = image
            } else if (
                image.isSameVisualImageVariant(firstVariant) &&
                !image.isRepeatedCaptionedFigureImage(firstVariant)
            ) {
                recordAndRemove(image, debug, "deduplicateImages", "img[src]", "duplicate image variant")
            }
        }
    }

    private fun removeCoverImage(
        content: Element,
        metadataImage: String?,
        debug: MutableList<RemovalRecord>,
        hintFor: (Element) -> String,
    ) {
        val coverKey = metadataImage?.trim()?.takeIf { it.isNotBlank() } ?: return
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (key == coverKey) {
                if (image.hasVisibleImageCaption()) continue
                val target = image.coverImageRemovalTarget(content)
                if (!target.hasCoverImageHint(image, hintFor)) continue
                recordAndRemove(
                    target,
                    debug,
                    "removeCoverImage",
                    coverImageSelector(target),
                    "duplicates metadata image",
                )
            }
        }
    }

    private fun Element.coverImageRemovalTarget(root: Element): Element {
        var target = this
        var current = parent()
        while (current != null && current != root && current.isVisualOnlyImageWrapper()) {
            target = current
            current = current.parent()
        }
        return target
    }

    private fun Element.isVisualOnlyImageWrapper(): Boolean {
        if (normalName() !in VISUAL_IMAGE_WRAPPER_TAGS) return false
        if (text().trim().isNotBlank()) return false
        return children().all { child ->
            child.normalName() in VISUAL_IMAGE_WRAPPER_TAGS ||
                child.normalName() in VISUAL_IMAGE_LEAF_TAGS
        }
    }

    private fun Element.isDeduplicableImage(): Boolean {
        val parent = parent() ?: return true
        if (parent.normalName() == "p" && parent.textOutside(this).isNotBlank()) return false
        return true
    }

    private fun Element.isRepeatedCaptionedFigureImage(firstImage: Element): Boolean {
        val figure = captionedFigureAncestor() ?: return false
        val firstFigure = firstImage.captionedFigureAncestor() ?: return false
        return figure !== firstFigure
    }

    private fun Element.isSameVisualImageVariant(firstImage: Element): Boolean {
        val firstParents = firstImage.parents()
        var current = parent()
        while (current != null) {
            if (current.isVisualOnlyImageWrapper() && current in firstParents) return true
            current = current.parent()
        }
        return false
    }

    private fun Element.captionedFigureAncestor(): Element? =
        parents().firstOrNull { it.normalName() == "figure" && it.selectFirst("figcaption") != null }

    private fun Element.hasVisibleImageCaption(): Boolean = parents().any { figure ->
        figure.normalName() == "figure" &&
            figure.select("figcaption, [class*=caption], [class*=credit], [id*=caption], [id*=credit]")
                .any { it.text().trim().isNotBlank() }
    }

    private fun Element.textOutside(excluded: Element): String =
        childNodes().filterNot { it === excluded }.joinToString(" ") { node ->
            when (node) {
                is TextNode -> node.text()
                is Element -> node.text()
                else -> ""
            }
        }.trim()

    private fun Element.hasCoverImageHint(image: Element, hintFor: (Element) -> String): Boolean {
        val hints = listOfNotNull(
            hintFor(this),
            parent()?.let(hintFor),
            hintFor(image),
        ).joinToString(" ")
        return COVER_IMAGE_HINTS.any { it in hints }
    }

    private fun Element.imageKey(): String? = replacementImageSource()
        ?: absUrl("src").ifBlank { attr("src").trim() }.ifBlank { null }

    private fun Element.replacementImageSource(): String? {
        val lazySource = firstAttr(
            "data-src",
            "data-original",
            "data-original-src",
            "data-lazy-src",
            "data-url",
            "data-image-loader",
        )
        if (lazySource != null) return lazySource

        val pictureSrcset = parents().firstOrNull { it.normalName() == "picture" }
            ?.selectFirst("source[srcset], source[srcSet], source[data-srcset]")
            ?.firstAttr("srcset", "srcSet", "data-srcset")
        return pictureSrcset?.let(::largestSrcsetUrl)
    }

    private fun Element.firstAttr(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
        absUrl(name).ifBlank { attr(name).trim() }.ifBlank { null }
    }

    private fun largestSrcsetUrl(srcset: String): String? = srcset.split(srcsetDelimiter)
        .mapNotNull { candidate ->
            val parts = candidate.trim().split(WHITESPACE_PATTERN)
            val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull()
                ?: parts.getOrNull(1)?.removeSuffix("x")?.toDoubleOrNull()?.times(1_000)?.toInt()
                ?: 0
            url to width
        }
        .maxByOrNull { it.second }
        ?.first

    private fun imageVariantKey(source: String): String? {
        val url = source
            .substringBefore(",")
            .trim()
            .split(WHITESPACE_PATTERN)
            .firstOrNull()
            ?.substringBefore("#")
            ?.substringBefore("?")
            ?.trimEnd('/')
            ?: return null
        return url.substringAfterLast('/').lowercase().ifBlank { null }
    }

    private fun coverImageSelector(element: Element): String = when {
        element.id().isNotBlank() -> "#${element.id()}"
        element.className().isNotBlank() -> ".${element.classNames().joinToString(".")}"
        else -> element.normalName()
    }

    private fun Element.isSmallImage(): Boolean {
        val width = dimension("width")
        val height = dimension("height")
        return width != null &&
            height != null &&
            width > 0 &&
            height > 0 &&
            width <= SMALL_IMAGE_MAX_DIMENSION &&
            height <= SMALL_IMAGE_MAX_DIMENSION
    }

    private fun Element.isLinkedAuthorImage(): Boolean {
        if (attr("alt").trim().isBlank()) return false
        val link = parent()?.takeIf { it.normalName() == "a" } ?: return false
        val href = link.attr("href").trim().lowercase()
        if (href.isBlank()) return false
        return link.attr("rel").contains("author", ignoreCase = true) ||
            "/author" in href ||
            href.startsWith("/@") ||
            href.contains("/@")
    }

    private fun Element.dimension(name: String): Int? = attr(name).dimensionValue()
        ?: styleDimensionPattern(name)
            .find(attr("style"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

    private fun styleDimensionPattern(name: String): Regex = when (name) {
        "width" -> WIDTH_STYLE_DIMENSION_PATTERN
        "height" -> HEIGHT_STYLE_DIMENSION_PATTERN
        else -> Regex("""$name\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)
    }

    private fun String.dimensionValue(): Int? =
        DIMENSION_VALUE_PATTERN.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private val VISUAL_IMAGE_WRAPPER_TAGS = setOf(
        "a",
        "div",
        "figure",
        "picture",
        "span",
    )

    private val VISUAL_IMAGE_LEAF_TAGS = setOf(
        "img",
        "source",
    )

    private val COVER_IMAGE_HINTS = listOf(
        "post-thumbnail",
        "featured",
        "hero",
        "cover",
        "wp-post-image",
        "image-link",
    )

    private val DIMENSION_VALUE_PATTERN = Regex("""^\s*(\d+)""")
    private val srcsetDelimiter = Regex(""",\s+""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val WIDTH_STYLE_DIMENSION_PATTERN = Regex("""width\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)
    private val HEIGHT_STYLE_DIMENSION_PATTERN = Regex("""height\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)

    private const val SMALL_IMAGE_MAX_DIMENSION = 64
}
