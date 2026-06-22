package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.attrTrimmedOrNull
import com.prof18.klead.internal.dom.isoDatePart
import com.prof18.klead.internal.dom.textTrimmedOrNull
import com.prof18.klead.internal.dom.toAbsoluteSiteUrl
import org.jsoup.nodes.Element

internal object RedditProfile : Extractor {
    override val id: String = "reddit"
    override val domains: Set<String> = setOf("reddit.com")

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val post = context.document.selectFirst(".thing.link") ?: return null
        val body = post.selectFirst(".entry .usertext-body .md")?.clone()
            ?: return null
        val commentRoots = context.document.selectFirst(".commentarea > .sitetable")
            ?.children()
            ?.filter { it.isRedditCommentElement() }
            ?.mapNotNull { it.toRedditComment() }
            .orEmpty()
        if (body.text().isBlank() || commentRoots.isEmpty()) return null

        val article = Element("article")
        article.appendMarkdownBody(body)
        article.appendElement("hr")
        article.appendElement("h2").text("Comments")
        commentRoots.forEach { comment ->
            article.appendRedditComment(comment)
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = post.selectFirst(".title")?.textTrimmedOrNull(),
                author = post.attrTrimmedOrNull("data-author"),
                site = post.attr("data-subreddit")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { "r/$it" }
                    ?: SITE_NAME,
            ),
        )
    }

    private fun Element.toRedditComment(): RedditComment? {
        val entry = children().firstOrNull { it.hasClass("entry") } ?: return null
        val author = entry.selectFirst(".tagline .author")?.text()?.trim().orEmpty()
        val score = entry.selectFirst(".tagline .score")?.text()?.trim()
        val date = entry.selectFirst(".tagline time[datetime]")
            ?.attr("datetime")
            ?.isoDatePart()
            .orEmpty()
        val body = entry.selectFirst(".usertext-body .md")?.clone() ?: return null
        if (author.isBlank() || date.isBlank() || body.text().isBlank()) return null

        val children = children()
            .firstOrNull { it.hasClass("child") }
            ?.children()
            ?.firstOrNull { it.hasClass("sitetable") }
            ?.children()
            ?.filter { it.isRedditCommentElement() }
            ?.mapNotNull { it.toRedditComment() }
            .orEmpty()

        return RedditComment(
            author = author,
            date = date,
            permalink = attr("data-permalink").toAbsoluteSiteUrl("reddit.com"),
            score = score,
            body = body,
            children = children,
        )
    }

    private fun Element.appendRedditComment(comment: RedditComment) {
        val quote = appendElement("blockquote")
        quote.appendElement("p").also { header ->
            header.appendElement("strong").text(comment.author)
            header.appendText(" · ")
            header.appendElement("a")
                .attr("href", comment.permalink)
                .text(comment.date)
            comment.score?.takeIf { it.isNotBlank() }?.let { score ->
                header.appendText(" · $score")
            }
        }
        quote.appendMarkdownBody(comment.body)
        comment.children.forEach { child ->
            quote.appendRedditComment(child)
        }
    }

    private fun Element.appendMarkdownBody(body: Element) {
        body.childNodes().forEach { node ->
            appendChild(node.clone())
        }
    }

    private fun Element.isRedditCommentElement(): Boolean = hasClass("thing") && hasClass("comment")

    private data class RedditComment(
        val author: String,
        val date: String,
        val permalink: String,
        val score: String?,
        val body: Element,
        val children: List<RedditComment>,
    )

    private const val SITE_NAME = "Reddit"
}
