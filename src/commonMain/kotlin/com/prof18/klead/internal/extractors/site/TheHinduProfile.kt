package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object TheHinduProfile : DomExtractor {
    override val id: String = "thehindu"
    override val domains: Set<String> = setOf("thehindu.com")

    // The page has no article or main landmark, so detection falls back to the body and keeps the
    // account side menu and sign-in modal that sit outside the story column.
    override val contentSelectors: List<String> = listOf(".storyline")

    override fun preProcess(content: Element, context: DomExtractorContext) {
        // Lazy images keep a 1x1 spacer in src and the real URL in data-src-template, which a script swaps in.
        content.select("img[data-src-template]").forEach { image ->
            if (image.hasAttr("data-src")) return@forEach
            val template = image.attr("data-src-template").trim().takeIf { it.isNotBlank() } ?: return@forEach
            image.attr("data-src", template)
        }
    }
}
