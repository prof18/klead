package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object HdMotoriProfile : DomExtractor {
    override val id: String = "hdmotori"
    override val domains: Set<String> = setOf("hdmotori.it")
    override val contentSelectors: List<String> = listOf(".article_content")
    override val preContentRemoveSelectors: List<String> = listOf(
        ".article_content > div:has(.share_buttons)",
    )
    override val postContentRemoveSelectors: List<String> = listOf(
        ".discover_button",
        ".hero_image > div",
    )

    override fun preProcess(content: Element, context: DomExtractorContext) {
        if (!content.hasClass("article_content")) return
        val introduction = context.document.selectFirst(".article_head > p") ?: return
        content.prependChild(introduction.clone())
    }
}
