package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object BeehiivProfile : DomExtractor {
    override val id: String = "beehiiv"
    override val domains: Set<String> = setOf("beehiiv.com")
    override val contentSelectors: List<String> = listOf("#content-blocks")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) || context.document.hasBeehiivPostStructure()

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        content.children().forEach { block ->
            val style = block.attr("style")
            if (style.isBlank()) return@forEach

            val normalizedStyle = style.withoutPaddingDeclarations()
            if (normalizedStyle.isBlank()) {
                block.removeAttr("style")
            } else if (normalizedStyle != style) {
                block.attr("style", normalizedStyle)
            }
        }

        content.select("p").toList().forEach { paragraph ->
            if (
                paragraph.text().isNotBlank() ||
                paragraph.selectFirst("img, picture, svg, video, audio, iframe") != null
            ) {
                return@forEach
            }
            recordAndRemove(
                element = paragraph,
                debug = debug,
                step = "postProcess:beehiiv",
                selector = "p",
                reason = "empty Beehiiv content paragraph",
            )
        }
    }

    private fun com.fleeksoft.ksoup.nodes.Document.hasBeehiivPostStructure(): Boolean =
        selectFirstSafe(".rendered-post #content-blocks") != null &&
            selectFirstSafe(".bh__byline_wrapper") != null

    private fun String.withoutPaddingDeclarations(): String = split(';')
        .map(String::trim)
        .filter(String::isNotBlank)
        .filterNot { declaration ->
            declaration.substringBefore(':').trim().startsWith("padding", ignoreCase = true)
        }
        .joinToString(";")
}
