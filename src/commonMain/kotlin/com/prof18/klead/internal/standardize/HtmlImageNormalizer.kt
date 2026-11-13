package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.QueryParser
import com.prof18.klead.internal.dom.SMALL_IMAGE_MAX_DIMENSION
import com.prof18.klead.internal.dom.hasLargeResponsiveSource
import com.prof18.klead.internal.dom.removeAllBatched
import com.prof18.klead.internal.markdown.largestSrcsetUrl

internal object HtmlImageNormalizer {
    fun normalizeImages(content: Element) {
        normalizeWordPressCaptionFigures(content)
        normalizeFixedImageWrappers(content)
        normalizeOutOfFlowImageWrappers(content)

        // Removals of src-less placeholders are applied with one rebuild per parent after the pass:
        // removing them one by one reindexed the parent each time. Only the current image is ever queued, and
        // a queued image has no real source, so later images' sibling checks are unaffected.
        val removedPlaceholders = mutableListOf<Element>()
        val pictureVerdicts = HashMap<Element, Boolean>()
        content.select("img").forEach { image ->
            if (image.parent() == null) return@forEach
            // Ghost sizes bookmark favicons with publisher CSS, which reader output lacks.
            if (image.hasClass("kg-bookmark-icon") && image.parents().any { it.hasClass("kg-bookmark-card") }) {
                image.remove()
                return@forEach
            }
            image.removeBrowserManagedImageLayoutStyle()
            val picture = image.parents().firstOrNull { it.normalName() == "picture" }
            val preserveFallbackSrcset = picture?.hasAttr("data-klead-preserve-fallback-srcset") == true
            if (preserveFallbackSrcset) picture.removeAttr("data-klead-preserve-fallback-srcset")
            val pictureSourceSrcset = picture
                ?.takeUnless { preserveFallbackSrcset }
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
            if (pictureSourceSrcset != null) {
                image.attr("srcset", pictureSourceSrcset)
            }
            if (image.hasSmallDeclaredDimensions() && image.hasLargeResponsiveSource(pictureVerdicts)) {
                image.removeAttr("width")
                image.removeAttr("height")
            }
            // A sibling <noscript> fallback never reaches this point: ContentSanitizer removes every noscript first.
            if (replacement != null &&
                (isPlaceholderImage(image.attr("src")) || isExternalPlaceholderImage(image.attr("src")))
            ) {
                image.attr("src", replacement)
            }
            firstAttr(image, "data-srcset", "data-lazy-srcset")?.let { image.attr("srcset", it) }
            if (isPlaceholderImage(image.attr("src"))) {
                val srcsetSource = largestSrcsetUrl(image.attr("srcset"))
                if (srcsetSource == null) {
                    removedPlaceholders.add(image)
                } else {
                    // Readers that ignore srcset would otherwise render the lazy-load placeholder.
                    image.attr("src", srcsetSource)
                }
            }
        }
        removeAllBatched(removedPlaceholders)

        content.select(LINKED_IMAGE_EVALUATOR).forEach { image ->
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

    private fun normalizeFixedImageWrappers(content: Element) {
        content.select("span[style]").forEach { wrapper ->
            val declarations = CssDeclarations.split(wrapper.attr("style"))
            val properties = declarations.associate { declaration ->
                val clean = CssDeclarations.withoutComments(declaration)
                clean.substringBefore(':').trim().lowercase() to clean.substringAfter(':', "").trim().lowercase()
            }
            if (properties["display"] != "inline-block" || properties["overflow"] != "hidden") return@forEach
            if (!FIXED_PIXEL_SIZE.matches(properties["width"].orEmpty()) ||
                !FIXED_PIXEL_SIZE.matches(properties["height"].orEmpty())
            ) {
                return@forEach
            }
            if (!wrapper.hasImageContent() || !wrapper.isImageOnlyWrapper()) return@forEach

            val retained = declarations.filterNot { declaration ->
                CssDeclarations.withoutComments(declaration).substringBefore(':').trim().lowercase() in
                    FIXED_IMAGE_WRAPPER_PROPERTIES
            }
            if (retained.isEmpty()) wrapper.removeAttr("style") else wrapper.attr("style", retained.joinToString("; "))
        }
    }

    /**
     * Site builders such as Framer fill a sized box with an absolutely positioned image wrapper
     * (`position: absolute; inset: 0`). Reader output has no positioned ancestor, so the wrapper
     * would stretch over the whole viewport and cover the article.
     */
    private fun normalizeOutOfFlowImageWrappers(content: Element) {
        content.select("div[style], span[style], picture[style], figure[style], a[style]").forEach { wrapper ->
            val declarations = CssDeclarations.split(wrapper.attr("style"))
            val properties = declarations.map { declaration ->
                CssDeclarations.withoutComments(declaration).substringBefore(':').trim().lowercase()
            }
            val position = declarations.zip(properties)
                .lastOrNull { (_, property) -> property == "position" }
                ?.let { (declaration, _) -> CssDeclarations.withoutComments(declaration).substringAfter(':').trim() }
                ?.lowercase()
                .orEmpty()
            if (!position.startsWith("absolute") && !position.startsWith("fixed")) return@forEach
            if (!wrapper.hasImageContent() || !wrapper.isImageOnlyWrapper()) return@forEach

            val retained = declarations.zip(properties)
                .filterNot { (_, property) -> property in OUT_OF_FLOW_PROPERTIES }
                .map { (declaration, _) -> declaration }
            if (retained.isEmpty()) wrapper.removeAttr("style") else wrapper.attr("style", retained.joinToString("; "))

            // The image filled the positioned box; in normal flow it keeps its own aspect ratio.
            wrapper.select("img[style]").forEach { image ->
                val imageRetained = CssDeclarations.split(image.attr("style")).filterNot { declaration ->
                    val clean = CssDeclarations.withoutComments(declaration)
                    clean.substringBefore(':').trim().lowercase() in BOX_FILL_PROPERTIES ||
                        clean.trim().isFullHeightDeclaration()
                }
                if (imageRetained.isEmpty()) {
                    image.removeAttr("style")
                } else {
                    image.attr("style", imageRetained.joinToString("; "))
                }
            }
        }
    }

    private val OUT_OF_FLOW_PROPERTIES = setOf("position", "top", "right", "bottom", "left", "inset", "z-index")
    private val BOX_FILL_PROPERTIES = setOf("object-fit", "object-position")

    private val FIXED_PIXEL_SIZE = Regex("""\d+(?:\.\d+)?px""")
    private val FIXED_IMAGE_WRAPPER_PROPERTIES = setOf("width", "height", "overflow", "display")

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

    // Visible text outside media and captions, found without copying: this runs for every nested
    // placeholder wrapper, and cloning each one made deep wrapper chains quadratic.
    private fun Element.isImageOnlyWrapper(): Boolean {
        val pending = ArrayDeque(childNodes())
        while (pending.isNotEmpty()) {
            when (val node = pending.removeLast()) {
                is TextNode -> if (node.getWholeText().isNotBlank()) return false
                is Element -> if (node.normalName() !in IMAGE_WRAPPER_IGNORED_TAGS) pending.addAll(node.childNodes())
            }
        }
        return true
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

    private fun Element.hasSmallDeclaredDimensions(): Boolean {
        val width = attr("width").trim().toIntOrNull()
        val height = attr("height").trim().toIntOrNull()
        return width != null && height != null &&
            width in 1..SMALL_IMAGE_MAX_DIMENSION &&
            height in 1..SMALL_IMAGE_MAX_DIMENSION
    }

    private fun isPlaceholderImage(src: String): Boolean = src.isBlank() ||
        src.startsWith("data:image/svg", ignoreCase = true) ||
        src.startsWith("data:image/gif", ignoreCase = true)

    private fun isExternalPlaceholderImage(src: String): Boolean = EXTERNAL_PLACEHOLDER_FILENAME.matches(
        src.trim().substringBefore('?').substringBefore('#').substringAfterLast('/'),
    )

    private val EXTERNAL_PLACEHOLDER_FILENAME = Regex(
        """(?:placeholder|(?:[a-z0-9]+[_-])?spacer)\.(?:png|gif|jpe?g|webp|svg)""",
        RegexOption.IGNORE_CASE,
    )
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

    private val IMAGE_WRAPPER_IGNORED_TAGS = setOf("img", "picture", "source", "noscript", "figcaption", "small")
}

// Parsed once: see ParsedSelectors for why selectors with combinators are not parsed per call.
private val LINKED_IMAGE_EVALUATOR = QueryParser.parse("a[href] > img")
