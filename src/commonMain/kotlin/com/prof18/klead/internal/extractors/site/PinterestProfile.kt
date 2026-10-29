package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/**
 * Pin pages are a script-sized masonry grid; read the pin itself instead of the grid wrappers,
 * whose inline layout only works with Pinterest's own stylesheet.
 */
internal object PinterestProfile : DomExtractor {
    override val id: String = "pinterest"
    override val domains: Set<String> = setOf(
        "pinterest.com",
        "pinterest.at",
        "pinterest.ca",
        "pinterest.ch",
        "pinterest.cl",
        "pinterest.co.kr",
        "pinterest.co.uk",
        "pinterest.com.au",
        "pinterest.com.mx",
        "pinterest.de",
        "pinterest.dk",
        "pinterest.es",
        "pinterest.fr",
        "pinterest.ie",
        "pinterest.it",
        "pinterest.jp",
        "pinterest.nz",
        "pinterest.ph",
        "pinterest.pt",
        "pinterest.se",
    )

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) && context.document.selectFirst(PIN_IMAGE) != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val document = context.document
        val image = document.selectFirst(PIN_IMAGE) ?: return null
        val title = document.selectFirst("""[data-test-id="pinTitle"] h1""")?.text()?.trim().orEmpty()
        val description = document.selectFirst("""[data-test-id="truncated-description"]""")
            ?.text()
            ?.trim()
            .orEmpty()
        val sourceUrl = document.metaContent("og:see_also")
        val sourceLabel = document.selectFirst("""[data-test-id="pin-domain-link"]""")?.text()?.trim()
        val creator = document.selectFirst("""[data-test-id="creator-profile-name"]""")?.text()?.trim()

        val article = Element("article")
        article.appendElement("p")
            .appendElement("img")
            .attr("src", image.attr("src"))
            .attr("alt", image.attr("alt"))
        if (description.isNotBlank() && description != title) {
            article.appendElement("p").text(description)
        }
        if (sourceUrl != null) {
            article.appendElement("p")
                .appendElement("a")
                .attr("href", sourceUrl)
                .text(sourceLabel?.ifBlank { null } ?: sourceUrl)
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = title.ifBlank { null },
                author = creator?.ifBlank { null },
                site = SITE_NAME,
                description = description.ifBlank { null },
            ),
        )
    }

    private const val PIN_IMAGE = """[data-test-id="pin-closeup-image"] img[src]"""
    private const val SITE_NAME = "Pinterest"
}
