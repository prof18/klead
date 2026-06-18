package dev.defuddle.internal.removal

import dev.defuddle.RemovalRecord
import org.jsoup.nodes.Element

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
            if (image.isSmallImage()) {
                recordAndRemove(image, debug, "removeSmallImages", "img", "small image dimensions")
            }
        }
    }

    private fun deduplicateImages(content: Element, debug: MutableList<RemovalRecord>) {
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
        hintFor: (Element) -> String,
    ) {
        val coverKey = metadataImage?.trim()?.takeIf { it.isNotBlank() } ?: return
        for (image in content.select("img[src]").toList()) {
            val key = image.imageKey() ?: continue
            if (key == coverKey) {
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

    private fun Element.hasCoverImageHint(image: Element, hintFor: (Element) -> String): Boolean {
        val hints = listOfNotNull(
            hintFor(this),
            parent()?.let(hintFor),
            hintFor(image),
        ).joinToString(" ")
        return COVER_IMAGE_HINTS.any { it in hints }
    }

    private fun Element.imageKey(): String? = absUrl("src").ifBlank { attr("src").trim() }.ifBlank { null }

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

    private fun Element.dimension(name: String): Int? = attr(name).dimensionValue()
        ?: Regex("""$name\s*:\s*(\d+)px""", RegexOption.IGNORE_CASE)
            .find(attr("style"))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

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

    private const val SMALL_IMAGE_MAX_DIMENSION = 64
}
