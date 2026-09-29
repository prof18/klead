package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object MacStoriesProfile : DomExtractor {
    override val id: String = "macstories"
    override val domains: Set<String> = setOf("macstories.net")
    override val postContentRemoveSelectors: List<String> = listOf(
        ".view-full-size",
    )

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        val articleUrl = context.url?.substringBefore('#') ?: return
        content.select(".ms-widget figure.mx[data-figure]").forEach { figure ->
            val heading = figure.selectFirst("figcaption .mx-t") ?: return@forEach
            val title = heading.text().trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val description = figure.selectFirst("figcaption .mx-l")?.text()?.trim().orEmpty()
            val target = heading.id().takeIf { it.isNotBlank() }
                ?.let { "$articleUrl#$it" } ?: articleUrl

            val fallback = Element("p")
            fallback.appendElement("a").attr("href", target).text("View interactive chart: $title")
            if (description.isNotEmpty()) fallback.appendText(" — $description")
            figure.replaceWith(fallback)
        }
    }
}
