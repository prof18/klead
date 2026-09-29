package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/** Extracts Aposto's editorial cards from its email-style Kill the Newsletter archive. */
internal object ApostoNewsletterProfile : DomExtractor {
    override val id: String = "aposto-newsletter"
    override val domains: Set<String> = setOf("kill-the-newsletter.com")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) && context.document.hasApostoNewsletterStructure()

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val document = context.document
        val cards = document.select(".channel-card")
        if (cards.isEmpty()) return null

        val article = Element("article")
        cards.forEachIndexed { index, card ->
            val content = card.selectFirst(".card-content") ?: return@forEachIndexed
            val heading = card.selectFirst(".card-header .compact-channel-label")?.text()?.trim().orEmpty()
            if (isSponsor(heading, content.text())) return@forEachIndexed

            val section = Element("section")
            card.selectFirst(".card-image")?.select("img")
                ?.filterNot { image -> isNewsletterChromeImage(image, isFirstCard = index == 0) }
                ?.forEach { image -> section.appendChild(editorialImageNode(image).clone()) }
            if (heading.isNotBlank()) section.appendElement("h2").text(heading)

            content.childNodes().forEach { child ->
                section.appendChild(child.clone())
            }
            section.select("img").filter(::isTrackingImage).forEach(Element::remove)
            removeEmptyParagraphs(section)
            if (section.text().isNotBlank() || section.selectFirst("img, table, svg, video, audio") != null) {
                article.appendChild(section)
            }
        }

        if (article.text().isBlank()) return null
        NewsletterDomNormalizer.normalize(article)

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = document.title().trim().ifBlank { null },
                site = document.metaContent("og:site_name"),
            ),
        )
    }

    private fun Document.hasApostoNewsletterStructure(): Boolean =
        selectFirstSafe(".channel-card .card-content") != null &&
            selectFirstSafe(".channel-card .card-image img[src*=aposto.com], .channel-card a[href*=apos.to]") != null

    private fun isSponsor(heading: String, contentText: String): Boolean {
        if (SPONSOR_HEADINGS.any { heading.trim().equals(it, ignoreCase = true) }) return true
        return contentText.trimStart().startsWith("SPONSORLU:", ignoreCase = true)
    }

    private fun isNewsletterChromeImage(image: Element, isFirstCard: Boolean): Boolean {
        val alt = image.attr("alt").trim()
        val src = image.attr("src")
        return isTrackingImage(image) ||
            (isFirstCard && (alt.startsWith("Aposto ", ignoreCase = true) || alt.equals("Pareto", true))) ||
            src.substringBefore('?').endsWith("_shadow.png", ignoreCase = true)
    }

    private fun editorialImageNode(image: Element): Element =
        image.parents().firstOrNull { it.normalName() == "figure" }
            ?: image.parents().firstOrNull { it.normalName() == "a" }
            ?: image.parents().firstOrNull { it.normalName() == "picture" }
            ?: image

    private fun isTrackingImage(image: Element): Boolean {
        val source = image.absUrl("src").ifBlank { image.attr("src") }
        return source.contains("link.apos.to/o", ignoreCase = true) ||
            image.attr("width").toIntOrNull()?.let { it in 0..1 } == true ||
            image.attr("height").toIntOrNull()?.let { it in 0..1 } == true ||
            image.attr("style").contains(Regex("height\\s*:\\s*0(?:px)?", RegexOption.IGNORE_CASE))
    }

    private fun removeEmptyParagraphs(content: Element) {
        content.select("p").toList().asReversed().forEach { paragraph ->
            if (
                paragraph.text().isBlank() &&
                paragraph.selectFirst("img, table, svg, video, audio") == null
            ) {
                paragraph.remove()
            }
        }
    }

    private fun Document.metaContent(name: String): String? = selectFirstSafe("meta[property=$name], meta[name=$name]")
        ?.attr("content")
        ?.trim()
        ?.ifBlank { null }

    private val SPONSOR_HEADINGS = setOf("SPONSORLU", "SPONSORLU:", "Bugünkü Destekçimiz")
}
