package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element

internal object HtmlImageNormalizer {
    fun normalizeImages(content: Element) {
        normalizeWordPressCaptionFigures(content)

        content.select("img").forEach { image ->
            if (image.parent() == null) return@forEach
            image.removeBrowserManagedImageLayoutStyle()
            val pictureSourceSrcset = image.parents().firstOrNull { it.normalName() == "picture" }
                ?.selectFirst("source[srcset], source[srcSet], source[data-srcset]")
                ?.let { firstAttr(it, "srcset", "srcSet", "data-srcset") }
            val replacement = firstAttr(
                image,
                "data-src",
                "data-original",
                "data-original-src",
                "data-lazy-src",
                "data-url",
                "data-image-loader",
            )
            val nextNoscript = image.nextElementSibling()?.takeIf { it.normalName() == "noscript" }
            val noscriptReplacement = nextNoscript?.noscriptImage()
            if (pictureSourceSrcset != null) {
                image.attr("srcset", pictureSourceSrcset)
            }
            if (isPlaceholderImage(image.attr("src"))) {
                when {
                    replacement != null -> image.attr("src", replacement)

                    noscriptReplacement != null && image.hasPriorImageVariant(noscriptReplacement) -> {
                        nextNoscript.remove()
                        image.remove()
                        return@forEach
                    }

                    noscriptReplacement != null -> {
                        firstAttr(noscriptReplacement, "srcset", "srcSet", "data-srcset")?.let {
                            image.attr("srcset", it)
                        }
                        firstAttr(noscriptReplacement, "src", "data-src")?.let {
                            image.attr("src", it)
                        }
                        image.addNoscriptAltCaption(noscriptReplacement)
                        nextNoscript.remove()
                    }
                }
            }
            firstAttr(image, "data-srcset", "data-lazy-srcset")?.let { image.attr("srcset", it) }
            if (isPlaceholderImage(image.attr("src")) && image.attr("srcset").isBlank()) {
                image.remove()
            }
        }

        content.select("a[href] > img").forEach { image ->
            val link = image.parent() ?: return@forEach
            val duplicate = link.nextElementSibling()?.takeIf { it.normalName() == "img" } ?: return@forEach
            val href = link.attr("href").trim()
            val duplicateSrc = duplicate.attr("src").trim()
            if (href.isNotBlank() && href == duplicateSrc) {
                duplicate.remove()
            }
        }
    }

    fun normalizeGalleryImageLists(content: Element) {
        content.select("ul, ol").forEach { list ->
            val items = list.children().filter { it.normalName() == "li" }
            if (items.isEmpty() || items.size != list.childrenSize()) return@forEach
            if (!list.hasGalleryHint()) return@forEach
            if (items.any { it.selectFirst("img, picture") == null && !it.isEmptyGalleryPlaceholder() }) {
                return@forEach
            }

            list.tagName("div")
            items.forEach { it.tagName("div") }
        }
    }

    private fun normalizeWordPressCaptionFigures(content: Element) {
        content.select(".wp-caption").forEach { wrapper ->
            if (wrapper.selectFirst("img, picture") == null) return@forEach
            val caption = wrapper.children().firstOrNull { it.hasClass("wp-caption-text") }
                ?: return@forEach

            wrapper.tagName("figure")
            caption.tagName("figcaption")
        }
    }

    private fun Element.removeBrowserManagedImageLayoutStyle() {
        val style = attr("style")
        if (style.isBlank()) return
        if (style.contains("position:absolute", ignoreCase = true) || attr("data-nimg") == "fill") {
            removeAttr("style")
            return
        }

        if (!hasGalleryHint() || !style.hasFullHeightDeclaration()) return

        val normalized = style.withoutFullHeightDeclaration()
        if (normalized.isBlank()) {
            removeAttr("style")
        } else {
            attr("style", normalized)
        }
    }

    fun normalizeImageAspectPlaceholders(content: Element) {
        content.select(
            "div[style], figure[style], picture[style], span[style], p[style], a[style]",
        ).forEach { element ->
            if (!element.hasImageContent()) return@forEach

            val style = element.attr("style")
            if (!style.hasAspectPlaceholderPadding()) return@forEach
            if (!element.hasImageAspectPlaceholderHint() && !element.isImageOnlyWrapper()) return@forEach

            val normalized = style.withoutAspectPlaceholderPadding()
            if (normalized.isBlank()) {
                element.removeAttr("style")
            } else {
                element.attr("style", normalized)
            }
        }
    }

    private fun Element.hasImageContent(): Boolean = normalName() == "picture" || selectFirst("img, picture") != null

    private fun Element.isEmptyGalleryPlaceholder(): Boolean = text().isBlank()

    private fun Element.hasGalleryHint(): Boolean = generateSequence(this as Element?) { it.parent() }
        .any { element ->
            val hintText = element.componentHintHaystack()
            GALLERY_HINTS.any { it in hintText } ||
                element.attributes().asList().any { attribute ->
                    GALLERY_HINTS.any { it in attribute.key.lowercase() }
                }
        }

    private fun Element.hasImageAspectPlaceholderHint(): Boolean {
        val haystack = componentHintHaystack()
        return IMAGE_ASPECT_PLACEHOLDER_HINTS.any { it in haystack }
    }

    private fun Element.isImageOnlyWrapper(): Boolean {
        val clone = clone()
        clone.select("img, picture, source, noscript, figcaption, small").remove()
        return clone.text().trim().isBlank()
    }

    private fun String.hasAspectPlaceholderPadding(): Boolean =
        split(';').any { it.trim().isAspectPlaceholderPaddingDeclaration() }

    private fun String.withoutAspectPlaceholderPadding(): String = split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.isAspectPlaceholderPaddingDeclaration() }
        .joinToString("; ")

    private fun String.hasFullHeightDeclaration(): Boolean = split(';').any {
        it.trim().isFullHeightDeclaration()
    }

    private fun String.withoutFullHeightDeclaration(): String = split(';')
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.isFullHeightDeclaration() }
        .joinToString("; ")

    private fun String.isFullHeightDeclaration(): Boolean {
        val parts = split(':', limit = 2)
        if (parts.size != 2 || !parts[0].trim().equals("height", ignoreCase = true)) return false
        return FULL_HEIGHT_VALUE.matches(parts[1].trim())
    }

    private fun String.isAspectPlaceholderPaddingDeclaration(): Boolean {
        val parts = split(':', limit = 2)
        if (parts.size != 2) return false

        val property = parts[0].trim().lowercase()
        if (property != "padding-bottom" && property != "padding-top") return false

        return ASPECT_PLACEHOLDER_PADDING_VALUE.matches(parts[1].trim())
    }

    private fun Element.noscriptImage(): Element? =
        selectFirst("img[src], img[srcset], img[srcSet], img[data-src], img[data-srcset]")

    private fun Element.hasPriorImageVariant(replacement: Element): Boolean {
        val replacementKey = replacement.imageVariantKey() ?: return false
        val parent = parent() ?: return false
        for (sibling in parent.children()) {
            if (sibling === this) return false
            if (sibling.imageVariantKeys().any { it == replacementKey }) return true
        }
        return false
    }

    private fun Element.imageVariantKeys(): List<String> {
        val candidates = if (normalName() == "img") listOf(this) else select("img")
        return candidates
            .filter { it.hasRealImageSource() }
            .mapNotNull { it.imageVariantKey() }
    }

    private fun Element.hasRealImageSource(): Boolean {
        val src = attr("src").trim()
        return (src.isNotBlank() && !isPlaceholderImage(src)) ||
            firstAttr(this, "srcset", "srcSet", "data-src", "data-srcset") != null
    }

    private fun Element.imageVariantKey(): String? =
        firstAttr(this, "src", "data-src", "srcset", "srcSet", "data-srcset")
            ?.let(::imageVariantKey)

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

    private fun Element.addNoscriptAltCaption(noscriptImage: Element) {
        if (!hasAttr("data-nimg") && !noscriptImage.hasAttr("data-nimg")) return
        if (parents().any { it.normalName() == "figure" && it.selectFirst("figcaption") != null }) return
        if (nextElementSibling()?.normalName() == "figcaption") return

        val caption = attr("alt").trim().ifBlank {
            noscriptImage.attr("alt").trim()
        }
        if (caption.isBlank()) return

        val captionElement = Element("span")
        captionElement.text(caption)
        after(captionElement)
    }

    private fun isPlaceholderImage(src: String): Boolean = src.isBlank() ||
        src.startsWith("data:image/svg", ignoreCase = true) ||
        src.startsWith("data:image/gif", ignoreCase = true)

    private val ASPECT_PLACEHOLDER_PADDING_VALUE = Regex(
        """(?:\d+(?:\.\d+)?|\.\d+)%\s*(?:!important)?""",
        RegexOption.IGNORE_CASE,
    )
    private val FULL_HEIGHT_VALUE = Regex("""100%\s*(?:!important)?""", RegexOption.IGNORE_CASE)
    private val IMAGE_ASPECT_PLACEHOLDER_HINTS = setOf(
        "article-image",
        "article-img",
        "aspect-ratio",
        "aspectratio",
        "body-img",
        "image-aspect",
        "image-container",
        "image-expandable",
        "image-wrapper",
        "img-article-item",
        "intrinsic",
        "ratio-box",
        "ratio-container",
        "responsive-img",
    )
    private val GALLERY_HINTS = setOf(
        "carousel",
        "gallery",
        "slider",
        "slideshow",
        "splide",
    )
}
