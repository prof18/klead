package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object MaggieAppletonProfile : DomExtractor {
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
    )

    override fun extract(context: ExtractorContext): ExtractorResult? =
        ExtractorResult(metadata = ExtractorMetadata(site = "maggieappleton.com"))

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        // The Astro scope suffix changes between builds. Recognize the tiny dashed
        // divider itself, while retaining illustrations and accessible SVG content.
        content.select("svg[height=2]").filter { it.isDecorativeDivider() }.forEach { divider ->
            recordAndRemove(
                element = divider,
                debug = debug,
                step = "removeExtractorSelectors:$id",
                selector = "svg[height=2]",
                reason = "decorative dashed divider",
            )
        }
        content.select("figure.container")
            .filter { it.selectFirst("figcaption") == null }
            .forEach { figure ->
                val image = figure.select("img[alt]").singleOrNull() ?: return@forEach
                val caption = image.attr("alt").trim().takeIf { it.isNotBlank() } ?: return@forEach
                figure.appendElement("figcaption").text(caption)
            }
    }

    private fun Element.isDecorativeDivider(): Boolean {
        if (hasAttr("role") || hasAttr("aria-label") || hasAttr("aria-labelledby") || text().isNotBlank()) return false
        val path = children().singleOrNull() ?: return false
        return path.normalName() == "path" && path.children().isEmpty() &&
            path.attr("stroke-width") == "2" && path.attr("stroke-dasharray").isNotBlank() &&
            HORIZONTAL_DIVIDER.matches(path.attr("d").trim())
    }

    private val HORIZONTAL_DIVIDER = Regex("""M\s*[-\d.]+\s*,\s*0\s+L\s*[-\d.]+\s*,\s*0""")
}
