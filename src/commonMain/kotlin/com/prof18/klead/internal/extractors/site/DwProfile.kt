package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object DwProfile : DomExtractor {
    override val id: String = "dw"
    override val domains: Set<String> = setOf("dw.com")
    override val postContentRemoveSelectors: List<String> = listOf(
        """[data-tracking-name="sharing-icons-inline"]""",
    )

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        resolveTemplatedImages(content)
        removeEmptyTweetEmbeds(content, debug)
    }

    private fun resolveTemplatedImages(content: Element) {
        content.select("img[data-url]").forEach { image ->
            val dataUrl = image.attr("data-url")
            if (FORMAT_ID_PLACEHOLDER !in dataUrl) return@forEach

            image.attr("data-url", dataUrl.replace(FORMAT_ID_PLACEHOLDER, FALLBACK_IMAGE_FORMAT_ID))
        }
    }

    private fun removeEmptyTweetEmbeds(content: Element, debug: MutableList<RemovalRecord>) {
        content.select("blockquote.tweet").toList().forEach { blockquote ->
            if (blockquote.text().isNotBlank()) return@forEach
            if (blockquote.select("img, picture, iframe, video, audio").isNotEmpty()) return@forEach

            recordAndRemove(
                element = blockquote,
                debug = debug,
                step = "postProcess:dw",
                selector = "blockquote.tweet",
                reason = "empty DW tweet placeholder",
            )
        }
    }

    private const val FORMAT_ID_PLACEHOLDER = "\${formatId}"
    private const val FALLBACK_IMAGE_FORMAT_ID = "605"
}
