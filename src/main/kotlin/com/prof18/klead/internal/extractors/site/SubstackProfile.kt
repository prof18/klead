package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.selectFirstSafe
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object SubstackProfile : com.prof18.klead.extractors.Extractor {
    override val id: String = "substack"
    override val domains: Set<String> = setOf("substack.com", "20percent.berlin")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#substack-comments",
        ".comments-section",
        ".more-comments",
        ".portable-archive",
        ".portable-archive-list",
        ".portable-archive-empty",
        """[aria-label="Top Posts Footer"]""",
    )

    override fun matches(context: ExtractorContext): Boolean =
        context.hostMatches(domains) || SUBSTACK_DOM_SIGNALS.any { context.document.selectFirstSafe(it) != null }

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val directNoteUrl = context.url.orEmpty().takeIf { it.contains("/note/c-") }
        if (directNoteUrl == null && context.document.noteUrl() == null) return null
        val description = context.document.metaContent("og:description")
        val bodies = context.document.select("""[class*="feedPermalinkUnit"] [class*="feedCommentBody"]""")
            .ifEmpty { context.document.select("""[class*="feedCommentBody"]""") }
        val descriptionPrefix = description
            ?.normalizedWhitespace()
            ?.take(DESCRIPTION_MATCH_PREFIX_LENGTH)
        val body = bodies
            .firstOrNull { body ->
                descriptionPrefix != null && body.text().normalizedWhitespace().contains(descriptionPrefix)
            }
            ?: bodies.singleOrNull()
            ?: return null
        val prose = body.selectFirst(".ProseMirror") ?: body
        val article = Element("article")
        article.append(prose.html())
        context.document.metaContent("og:image")?.takeIf { directNoteUrl != null }?.let { image ->
            article.appendElement("img").attr("src", image)
        }
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.metaContent("og:title"),
                description = description,
                author = context.document.substackAuthor(),
                site = context.document.metaContent("og:site_name"),
            ),
        )
    }

    private fun Document.noteUrl(): String? = listOfNotNull(
        metaContent("og:url"),
        selectFirst("""link[rel=canonical][href]""")?.attr("href")?.trim()?.ifBlank { null },
    ).firstOrNull { it.contains("/note/c-") }

    private fun Document.metaContent(name: String): String? =
        selectFirst("""meta[property="$name"], meta[name="$name"]""")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }

    private fun Document.substackAuthor(): String? = metaContent("author")
        ?: metaContent("og:title")
            ?.replace(Regex("""\s+\(@[^)]+\)$"""), "")
            ?.trim()
            ?.ifBlank { null }

    private fun String.normalizedWhitespace(): String = trim().replace(WHITESPACE_PATTERN, " ")

    private val SUBSTACK_DOM_SIGNALS = listOf(
        "#substack-comments",
        ".portable-archive",
        ".portable-archive-list",
        """[aria-label="Top Posts Footer"]""",
        """script[src*="substackcdn.com"]""",
        """link[href*="substackcdn.com"]""",
    )

    private val WHITESPACE_PATTERN = Regex("""\s+""")

    private const val DESCRIPTION_MATCH_PREFIX_LENGTH = 80
}
