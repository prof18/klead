package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.RemovalRecord
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.attrTrimmedOrNull
import com.prof18.klead.internal.dom.selectFirstSafe
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.removal.recordAndRemove

internal object SubstackProfile : DomExtractor {
    override val id: String = "substack"
    override val domains: Set<String> = setOf("substack.com", "20percent.berlin")
    override val postContentRemoveSelectors: List<String> = listOf(
        "#substack-comments",
        ".comments-section",
        ".more-comments",
        ".portable-archive",
        ".portable-archive-list",
        ".portable-archive-empty",
        ".image-link-expand",
        """[aria-label="Top Posts Footer"]""",
    )

    override fun matches(context: DomExtractorContext): Boolean =
        context.hostMatches(domains) || SUBSTACK_DOM_SIGNALS.any { context.document.selectFirstSafe(it) != null }

    override fun extract(context: DomExtractorContext): ExtractorResult? {
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

    override fun postProcess(content: Element, context: DomExtractorContext, debug: MutableList<RemovalRecord>) {
        val metadataImageKey = (
            context.document.metaContent("og:image")
                ?: context.document.metaContent("twitter:image")
        )?.substackImageKey() ?: return

        for (image in content.select("img[data-attrs]").toList()) {
            if (!TOP_IMAGE_PATTERN.containsMatchIn(image.attr("data-attrs"))) continue
            val imageKey = image.absUrl("src").ifBlank { image.attr("src").trim() }.substackImageKey()
            if (imageKey != metadataImageKey) continue
            if (image.hasVisibleCaption()) continue

            val target = image.parents().firstOrNull { it.hasClass("captioned-image-container") }
                ?: image.parents().firstOrNull { it.normalName() == "figure" }
                ?: image
            recordAndRemove(
                element = target,
                debug = debug,
                step = "postProcess:substack",
                selector = "img[data-attrs]",
                reason = "Substack top image duplicates metadata image",
            )
        }
    }

    private fun Document.noteUrl(): String? = listOfNotNull(
        metaContent("og:url"),
        selectFirst("""link[rel=canonical][href]""")?.attrTrimmedOrNull("href"),
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

    private fun Element.hasVisibleCaption(): Boolean = parents().any { ancestor ->
        ancestor.normalName() == "figure" &&
            ancestor.select("figcaption, [class*=caption], [class*=credit], [id*=caption], [id*=credit]")
                .any { it.text().trim().isNotBlank() }
    }

    private fun String.substackImageKey(): String? {
        val value = trim().substringBefore('#').ifBlank { return null }
        val embeddedOriginIndex = value.indexOf(ENCODED_ORIGIN_MARKER, ignoreCase = true)
        return if (embeddedOriginIndex >= 0) value.substring(embeddedOriginIndex + 1) else value
    }

    private val SUBSTACK_DOM_SIGNALS = listOf(
        "#substack-comments",
        ".portable-archive",
        ".portable-archive-list",
        """[aria-label="Top Posts Footer"]""",
        """script[src*="substackcdn.com"]""",
        """link[href*="substackcdn.com"]""",
    )

    private val WHITESPACE_PATTERN = Regex("""\s+""")
    private val TOP_IMAGE_PATTERN = Regex("""[\"']topImage[\"']\s*:\s*true""", RegexOption.IGNORE_CASE)

    private const val ENCODED_ORIGIN_MARKER = "/https%3A%2F%2F"
    private const val DESCRIPTION_MATCH_PREFIX_LENGTH = 80
}
