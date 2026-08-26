package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object KarakartalProfile : DomExtractor {
    override val id: String = "karakartal"
    override val domains: Set<String> = setOf("karakartal.com")
    override val contentSelectors: List<String> = listOf("#haberBody")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#contextual > a[href*=\"karakartal.com/mobil\"]",
    )

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        content.children()
            .filter { child -> child.attr("style").contains("float:right", ignoreCase = true) }
            .forEach { child -> child.removeAttr("style") }
    }
}
