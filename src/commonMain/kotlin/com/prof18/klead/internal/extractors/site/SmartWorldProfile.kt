package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object SmartWorldProfile : DomExtractor {
    override val id: String = "smartworld"
    override val domains: Set<String> = setOf("smartworld.it")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".tw-heading-category",
        ".tw-leaf-info-box",
        ".tw-google-discover",
        "#modal-single",
        ".tw-pros-block svg",
        ".tw-cons-block svg",
        ".tw-module-rate svg",
        ".tw-icon-gallery-expand",
        ".tw-gallery-more",
        ".tw-gallery-head",
        ".tw-swiper-button",
        ".tw-author-btn-channel-list",
    )

    override fun preProcess(content: Element, context: DomExtractorContext) {
        // The site draws the score with CSS; keep its value without the oversized SVG.
        content.select(".tw-module-rate[data-rate]").forEach { badge ->
            val rating = badge.attr("data-rate").trim()
            val value = rating.replace(',', '.').toDoubleOrNull() ?: return@forEach
            if (value !in 0.0..10.0) return@forEach
            badge.replaceWith(Element("p").text("Voto: $rating/10"))
        }
    }
}
