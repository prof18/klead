package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object AthleticNewsletterProfile : DomExtractor {
    override val id: String = "athletic-newsletter"
    override val domains: Set<String> = setOf("kill-the-newsletter.com")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) && context.document.hasAthleticNewsletterStructure()

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val document = context.document
        val content = Element("div").attr("id", CONTENT_ID)

        document.select(".nl-container table.row").forEach { row ->
            val text = row.text()
            val isMasthead = row.selectFirst("img[alt='The Athletic FC']") != null
            val isLoginBanner = text.contains(LOGIN_BANNER, ignoreCase = true)
            val isSubscriptionFooter = FOOTER_MARKERS.any { marker ->
                text.contains(marker, ignoreCase = true)
            }

            if (isMasthead || isLoginBanner || isSubscriptionFooter) return@forEach

            row.select(".column")
                .filter { column -> column.parents().none { it !== row && it.hasClass("column") } }
                .forEach { column -> content.appendChild(column.clone()) }
        }

        if (content.text().isBlank()) return null
        NewsletterDomNormalizer.normalize(content)

        val author = content.select("p").firstOrNull { it.text().trim().startsWith("By ") }
            ?.text()
            ?.trim()
            ?.removePrefix("By ")
            ?.trim()

        return ExtractorResult(
            contentHtml = content.outerHtml(),
            contentSelector = "#$CONTENT_ID",
            metadata = ExtractorMetadata(
                title = content.select("p").firstOrNull { paragraph ->
                    val text = paragraph.text().trim()
                    text.length > MIN_TITLE_LENGTH &&
                        paragraph.selectFirst("strong")?.text()?.trim() == text
                }?.text()?.trim(),
                author = author,
                site = "The Athletic",
            ),
        )
    }

    private fun com.fleeksoft.ksoup.nodes.Document.hasAthleticNewsletterStructure(): Boolean =
        selectFirstSafe(".nl-container .text_block") != null &&
            selectFirstSafe(".nl-container img[alt='The Athletic FC']") != null

    private const val LOGIN_BANNER = "LOG IN TO READ THESE STORIES FOR FREE"
    private const val CONTENT_ID = "athletic-newsletter-content"
    private const val MIN_TITLE_LENGTH = 25
    private val FOOTER_MARKERS = listOf(
        "Manage Preferences",
        "Unsubscribe",
        "The Athletic Media Company. All Rights Reserved.",
    )
}
