package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.ParsedSelectors
import com.prof18.klead.internal.dom.parseKleadUri
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

/**
 * The Nixers newsletter serves every issue on `entries.php`, whatever `?id=` names, so the whole
 * archive would become the article. Keep only the issue whose post date matches the
 * `nixersnewsletter_YYYYMMDD` id from the feed link.
 */
internal object NixersNewsletterProfile : DomExtractor {
    override val id: String = "nixers-newsletter"
    override val domains: Set<String> = setOf("newsletter.nixers.net")

    private val issueIdPattern = Regex("""(?:^|&)id=nixersnewsletter_(\d{4})(\d{2})(\d{2})(?:&|$)""")
    private val postDatePattern = Regex("""\b(\d{1,2}) ([A-Z][a-z]{2}) (\d{4})\b""")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) && context.requestedIssueDate() != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
        val issueDate = context.requestedIssueDate() ?: return null
        val post = context.document.select("table.postbit_table")
            .firstOrNull { it.postDate() == issueDate }
            ?: return null
        val content = post.selectFirst(".feed_content") ?: return null
        val issueNumber = post.selectFirst(
            ParsedSelectors[".post_author a[href^=#]"],
        )?.text()?.trim()?.takeIf { it.isNotEmpty() }
        return ExtractorResult(
            contentHtml = content.outerHtml(),
            contentSelector = "table.postbit_table .feed_content",
            metadata = ExtractorMetadata(
                title = listOfNotNull("Nixers Newsletter", issueNumber).joinToString(" "),
            ),
        )
    }

    private fun DomExtractorContext.requestedIssueDate(): IssueDate? {
        val query = url?.let(::parseKleadUri)?.rawQuery ?: return null
        val (year, month, day) = issueIdPattern.find(query)?.destructured ?: return null
        return IssueDate(year = year.toInt(), month = month.toInt(), day = day.toInt())
    }

    private fun Element.postDate(): IssueDate? {
        val dateText = selectFirst(".post_author .smalltext")?.text() ?: return null
        val (day, monthName, year) = postDatePattern.find(dateText)?.destructured ?: return null
        val month = months.indexOf(monthName).takeIf { it >= 0 } ?: return null
        return IssueDate(year = year.toInt(), month = month + 1, day = day.toInt())
    }

    private data class IssueDate(val year: Int, val month: Int, val day: Int)
}
