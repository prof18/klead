package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/** Extracts Guardian newsletter articles forwarded through Kill the Newsletter. */
internal object GuardianNewsletterProfile : DomExtractor {
    override val id: String = "guardian-newsletter"
    override val domains: Set<String> = setOf("kill-the-newsletter.com")

    override fun matches(context: DomExtractorContext): Boolean {
        val document = context.document
        return context.hostMatches(domains) &&
            document.title().endsWith(" | The Guardian", ignoreCase = true) &&
            document.selectFirst("img[alt*=\"Football Daily\"][src*=\"i.guim.co.uk\"]") != null &&
            document.selectFirst("[aria-roledescription=email][role=article]") != null
    }

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val document = context.document
        val title = document.selectFirst("h1")?.text()?.trim()?.ifBlank { null } ?: return null
        val output = Element("article")

        document.select(".text, .text-with-border, .dark-theme-text, img[alt]").forEach { source ->
            when {
                source.normalName() == "img" && source.isNewsletterEditorialImage() &&
                    source.parents().none { it.isEditorialTextCell() } -> {
                    val imageOrLink = source.parent()?.takeIf { it.normalName() == "a" } ?: source
                    source.authorNameFromImageRow()?.let { name -> output.appendElement("p").text(name) }
                    output.appendChild(imageOrLink.clone())
                }

                source.normalName() == "img" -> Unit

                source.normalName() == "td" && source.isEditorialTextCell() && source.isNewsletterEditorialText() ->
                    output.appendChild(source.clone())
            }
        }

        if (output.text().isBlank()) return null
        output.select("img").toList().filterNot { it.isNewsletterEditorialImage() }.forEach { it.remove() }
        NewsletterDomNormalizer.normalize(output)
        return ExtractorResult(
            contentHtml = output.outerHtml(),
            metadata = ExtractorMetadata(
                title = title,
                site = "The Guardian",
            ),
        )
    }

    private fun Element.isNewsletterEditorialText(): Boolean {
        val text = text().trim()
        if (text.isBlank()) return false
        if (isNewsletterFooterText(text) || text.equals("unsubscribe", ignoreCase = true)) return false
        val module = closestNewsletterModule() ?: return true
        if (module.isAcquisitionModule() || module.isNewsletterFooterModule()) return false
        val sectionHeading = selectFirst("h2")?.text()?.trim()
        return sectionHeading == null || text != sectionHeading || module.hasFollowingEditorialTextCell(this)
    }

    private fun Element.hasFollowingEditorialTextCell(headingCell: Element): Boolean {
        val cells = select("td.text, td.text-with-border, td.dark-theme-text")
        val index = cells.indexOfFirst { it === headingCell }
        return index >= 0 && cells.drop(index + 1).any { cell ->
            cell.text().isNotBlank() && cell.selectFirst("h1, h2") == null &&
                !isNewsletterFooterText(cell.text())
        }
    }

    private fun Element.isNewsletterEditorialImage(): Boolean {
        val altText = attr("alt").trim()
        if (altText.isBlank() || altText.lowercase() in DECORATIVE_ALT_TEXT) return false
        val isMasthead = altText.contains("Guardian", ignoreCase = true) &&
            (altText.contains("newsletter", ignoreCase = true) || altText.contains("Football Daily", ignoreCase = true))
        val isFooterLogo = altText.startsWith("https://www.theguardian.com/", ignoreCase = true)
        if (isMasthead || isFooterLogo || altText in SOCIAL_ICON_ALT_TEXT) return false
        if (attr("src").contains("podcast", ignoreCase = true)) return false
        val module = closestNewsletterModule()
        if (module?.isNewsletterFooterModule() == true) return false
        return !isNewsletterFooterText(altText)
    }

    private fun Element.isEditorialTextCell(): Boolean = normalName() == "td" &&
        (hasClass("text") || hasClass("text-with-border") || hasClass("dark-theme-text"))

    private fun Element.authorNameFromImageRow(): String? {
        val name = attr("alt").trim().takeIf { it.isNotBlank() } ?: return null
        val row = generateSequence(parent()) { it.parent() }.firstOrNull { it.normalName() == "tr" } ?: return null
        return name.takeIf { row.text().trim() == it }
    }

    private fun Element.closestNewsletterModule(): Element? = generateSequence(this as Element?) { it.parent() }
        .firstOrNull { it.normalName() == "div" && it.attr("style").contains("max-width", ignoreCase = true) }

    private fun Element.isAcquisitionModule(): Boolean =
        selectFirst("img[alt='Person Image']") != null && selectFirst("a[href*=support.theguardian.com]") != null

    private fun Element.isNewsletterFooterModule(): Boolean =
        (isNewsletterFooterText(text()) || selectFirst("a[href*=unsubscribe]") != null) &&
            selectFirst("h1, h2") == null

    private fun isNewsletterFooterText(text: String): Boolean =
        text.trim().equals("get in touch", ignoreCase = true) || listOf(
            "view this email in your browser",
            "view online",
            "manage your emails",
            "questions or comments about any of our newsletters",
            "you are receiving this email because",
            "registered office",
        ).any { marker -> text.contains(marker, ignoreCase = true) }

    private val DECORATIVE_ALT_TEXT = setOf("camera", "person image")
    private val SOCIAL_ICON_ALT_TEXT = setOf("Facebook", "Instagram", "X", "LinkedIn", "YouTube")
}
