package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult

internal object MaggieAppletonProfile : Extractor {
    override val id: String = "maggie-appleton"
    override val domains: Set<String> = setOf("maggieappleton.com")
    override val contentSelectors: List<String> = listOf("article.prose-wrapper")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".desktop-container",
        ".mobile-container",
        "template.tooltip-content",
        ".tooltip-content",
        ".book-card .metadata",
        ".tweet-embed",
        ".mentions-content-container",
        ".backlink-container",
        "svg[data-icon]",
        "svg[data-astro-cid-yuxp3hac]",
    )

    override fun extract(context: ExtractorContext): ExtractorResult? =
        ExtractorResult(metadata = ExtractorMetadata(site = "maggieappleton.com"))

    override fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) {
        content.select("figure.container")
            .filter { it.selectFirst("figcaption") == null }
            .forEach { figure ->
                val image = figure.select("img[alt]").singleOrNull() ?: return@forEach
                val caption = image.attr("alt").trim().takeIf { it.isNotBlank() } ?: return@forEach
                figure.appendElement("figcaption").text(caption)
            }
    }
}
