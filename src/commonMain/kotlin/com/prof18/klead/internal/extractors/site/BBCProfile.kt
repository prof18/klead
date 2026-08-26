package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object BBCProfile : DomExtractor {
    override val id: String = "bbc"
    override val domains: Set<String> = setOf("bbc.com", "bbc.co.uk")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-component="headline-block"]""",
        """[data-component="byline-block"]""",
        """[data-block="byline"]""",
        """[data-block="metadata"]""",
        """[data-block="promoList"]""",
        "img.hide-when-no-script",
        """img[aria-label="image unavailable"]""",
        """img[src*="grey-placeholder"]""",
        """p:matches((?i)\bdo\s+you\s+have\s+a\s+story\s+suggestion\b)""",
        """p:matches((?i)^follow\s+.{1,80}\s+news\s+on\b)""",
    )

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        val metadataImageKey = context.document.metaContent("og:image")?.bbcImageKey() ?: return

        for (image in content.select("""[data-block="image"] img[src]""").toList()) {
            if (image.attr("src").bbcImageKey() != metadataImageKey) continue
            val imageBlock = image.parents().firstOrNull { it.attr("data-block") == "image" } ?: continue
            if (imageBlock.select("figcaption").any { it.text().trim().isNotBlank() }) continue

            recordAndRemove(
                element = imageBlock,
                debug = debug,
                step = "postProcess:bbc",
                selector = """[data-block="image"]""",
                reason = "BBC lead image duplicates metadata image",
            )
        }
    }

    private fun Document.metaContent(name: String): String? =
        selectFirst("""meta[property="$name"], meta[name="$name"]""")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }

    private fun String.bbcImageKey(): String? {
        val path = substringAfter(BBC_IMAGE_PATH_MARKER, missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
            .removeSuffix(".webp")
            .trim('/')
        return path.ifBlank { null }
    }

    private const val BBC_IMAGE_PATH_MARKER = "/cpsprodpb/"
}
