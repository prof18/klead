package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object SkyTg24Profile : DomExtractor {
    override val id: String = "sky-tg24"
    override val domains: Set<String> = setOf("tg24.sky.it")

    override fun preProcess(content: Element, context: DomExtractorContext) {
        content.select("a.c-inline-card[href]").toList().forEach { card ->
            val title = card.selectFirst(".c-inline-card__title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@forEach
            val href = card.attr("href").trim().takeIf { it.isNotBlank() } ?: return@forEach
            val label = card.selectFirst(".c-section-title")?.text()?.trim().orEmpty()
            val paragraph = Element("p")
            if (label.isNotBlank()) paragraph.appendText("${label.trimEnd(':')}: ")
            paragraph.appendElement("a").attr("href", href).text(title)
            card.replaceWith(paragraph)
        }
    }
}
