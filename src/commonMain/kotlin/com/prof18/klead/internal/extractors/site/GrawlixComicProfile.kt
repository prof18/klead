package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.QueryParser
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.deepCopy
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/**
 * Grawlix CMS webcomics render the comic page and the author's blog post as sibling articles, so
 * the text-heavy blog post wins detection and the comic itself is lost. Keep the blog post as the
 * body and put the comic figure above it.
 */
internal object GrawlixComicProfile : DomExtractor {
    // Probed against the document's <meta> elements only (see DocumentSignals).
    private val GENERATOR_META = QueryParser.parse("""meta[name=generator][content]""")

    override val id: String = "grawlix-comic"
    override val contentSelectors: List<String> = listOf("#blog-post")

    private const val COMIC_FIGURE_SELECTOR = "#comic figure#page"

    override fun matches(context: DomExtractorContext): Boolean {
        val generator = context.signals.firstMeta(GENERATOR_META)?.attr("content")
            ?: return false
        return generator.startsWith("The Grawlix CMS", ignoreCase = true) &&
            context.document.selectFirst("$COMIC_FIGURE_SELECTOR img[src]") != null
    }

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        val figure = context.document.selectFirst(COMIC_FIGURE_SELECTOR) ?: return
        val comicSources = figure.select("img[src]").map { it.absUrl("src").ifBlank { it.attr("src") } }
        if (comicSources.isEmpty()) return
        val contentSources = content.select("img[src]").map { it.absUrl("src").ifBlank { it.attr("src") } }.toSet()
        if (comicSources.all { it in contentSources }) return

        val clone = figure.deepCopy()
        clone.select("img[src]").forEach { image ->
            image.absUrl("src").takeIf { it.isNotBlank() }?.let { image.attr("src", it) }
        }
        content.prependChild(clone)
    }
}
