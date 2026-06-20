package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import org.jsoup.nodes.Element

internal object WikipediaExtractor : Extractor {
    override val id: String = "wikipedia"

    override fun matches(context: ExtractorContext): Boolean = context.isWikipediaPage() &&
        context.document.selectFirst(WIKIPEDIA_CONTENT_SELECTOR) != null

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val content = context.document.selectFirst(WIKIPEDIA_CONTENT_SELECTOR)?.clone() ?: return null
        content.cleanWikipediaChrome()
        if (content.text().isBlank()) return null

        return ExtractorResult(
            contentHtml = content.outerHtml(),
            contentSelector = ".mw-parser-output",
            metadata = ExtractorMetadata(
                title = context.document.selectFirst("""meta[property="og:title"]""")
                    ?.attr("content")
                    ?.trim()
                    ?.removeSuffix(" - Wikipedia")
                    ?.ifBlank { null }
                    ?: context.document.selectFirst("h1")?.text()?.trim()?.ifBlank { null },
                site = "Wikipedia",
            ),
        )
    }

    private fun Element.cleanWikipediaChrome() {
        select(
            "table.infobox, #toc, .toc, .mw-editsection, .mw-cite-backlink, .noprint, .metadata, .ambox, .navbox",
        ).remove()

        removeRepeatedCitationParagraphBeforeAppendix()

        select("ul, ol").forEach { list ->
            if (list.isPreservedWikipediaSectionList()) {
                list.attr(PRESERVE_LINK_LIST_ATTR, PRESERVE_LINK_LIST_VALUE)
            }
        }
    }

    private fun Element.removeRepeatedCitationParagraphBeforeAppendix() {
        val seeAlsoHeading = children().firstOrNull { it.isWikipediaSectionHeading("See also") } ?: return
        val paragraph = seeAlsoHeading.previousElementSibling()?.takeIf { it.normalName() == "p" } ?: return
        val paragraphTargets = paragraph.wikipediaCitationTargets()
        if (paragraphTargets.isEmpty()) return

        val earlierTargets = children()
            .takeWhile { it !== paragraph }
            .flatMap { it.wikipediaCitationTargets() }
            .toSet()
        if (earlierTargets.containsAll(paragraphTargets)) {
            paragraph.remove()
        }
    }

    private fun Element.isWikipediaSectionHeading(text: String): Boolean {
        val heading = if (normalName().matches(headingTagPattern)) {
            this
        } else {
            children().singleOrNull { it.normalName().matches(headingTagPattern) }
        } ?: return false
        return heading.text().trim() == text
    }

    private fun Element.wikipediaCitationTargets(): List<String> = select("""sup.reference a[href^="#cite_note"]""")
        .mapNotNull { link -> link.attr("href").removePrefix("#").takeIf { it.isNotBlank() } }

    private fun Element.isPreservedWikipediaSectionList(): Boolean {
        val heading = previousElementSibling()
            ?.takeIf { it.normalName().matches(headingTagPattern) }
            ?: return false
        return heading.text().trim() in preservedLinkSectionHeadings
    }

    private fun String.isWikipediaHost(): Boolean {
        val host = lowercase().trim('.')
        return host == "wikipedia" ||
            host == "wikipedia.org" ||
            host.endsWith(".wikipedia.org")
    }

    private fun ExtractorContext.isWikipediaPage(): Boolean {
        if (host.orEmpty().isWikipediaHost()) return true

        val siteName = document.selectFirst("""meta[property="og:site_name"]""")
            ?.attr("content")
            ?.trim()
        return siteName == "Wikipedia" && document.selectFirst("body.mediawiki") != null
    }

    private val preservedLinkSectionHeadings = setOf("See also", "External links")
    private val headingTagPattern = Regex("""h[1-6]""")
    private const val WIKIPEDIA_CONTENT_SELECTOR = "#mw-content-text .mw-parser-output, .mw-parser-output"
    private const val PRESERVE_LINK_LIST_ATTR = "data-klead-preserve-link-list"
    private const val PRESERVE_LINK_LIST_VALUE = "footnote-preserve"
}
