package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.appendChildNodesFrom
import com.prof18.klead.internal.dom.attrTrimmedOrNull
import com.prof18.klead.internal.dom.deepCopy
import com.prof18.klead.internal.dom.isoDatePart
import com.prof18.klead.internal.dom.textTrimmedOrNull
import com.prof18.klead.internal.dom.toAbsoluteSiteUrl
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext

internal object RedditProfile : DomExtractor {
    override val id: String = "reddit"
    override val domains: Set<String> = setOf("reddit.com")

    override fun extract(context: DomExtractorContext): ExtractorResult? =
        extractOldRedditPost(context) ?: extractShredditPost(context)

    private fun extractShredditPost(context: DomExtractorContext): ExtractorResult? {
        val post = context.document.selectFirst("shreddit-post") ?: return null
        val article = Element("article")
        post.shredditMediaUrls().forEach { source ->
            article.appendElement("p").appendElement("img").attr("src", source)
        }
        post.selectFirst("""[slot=text-body] [property="schema:articleBody"]""")?.let { body ->
            article.appendChildNodesFrom(body.deepCopy())
        }
        if (article.text().isBlank() && article.selectFirst("img") == null) return null

        val comments = context.document.select(SHREDDIT_COMMENT_TAG)
            .filter { it.parentShredditComment() == null }
            .mapNotNull { it.toShredditComment() }
        if (comments.isNotEmpty()) {
            article.appendElement("hr")
            article.appendElement("h2").text("Comments")
            comments.forEach { comment ->
                article.appendRedditComment(comment)
            }
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = post.attrTrimmedOrNull("post-title"),
                author = post.attrTrimmedOrNull("author"),
                site = post.attrTrimmedOrNull("subreddit-prefixed-name") ?: SITE_NAME,
            ),
        )
    }

    // Each image appears as a low-resolution preview and a full-size lightbox copy; keep the latter.
    // Videos are HLS streams the reader cannot play, so they are represented by their poster.
    private fun Element.shredditMediaUrls(): List<String> {
        val media = selectFirst("[slot=post-media-container]") ?: return emptyList()
        val fullSize = media.select("img.media-lightbox-img")
            .map { it.attr("src").trim() }
            .filter { it.startsWith(FULL_SIZE_IMAGE_PREFIX) }
            .distinct()
        if (fullSize.isNotEmpty()) return fullSize
        return listOfNotNull(
            media.selectFirst("img[data-post-media-primary]")?.attrTrimmedOrNull("src")
                ?: media.selectFirst("shreddit-player img[alt=media poster]")?.attrTrimmedOrNull("src"),
        )
    }

    private fun Element.toShredditComment(): RedditComment? {
        val author = attr("author").trim()
        val date = attr("created").trim().isoDatePart()
        val body = selectFirst("#${attr("thingid")}-comment-rtjson-content")?.deepCopy() ?: return null
        if (author.isBlank() || date.isBlank() || body.text().isBlank()) return null

        return RedditComment(
            author = author,
            date = date,
            permalink = attr("permalink").toAbsoluteSiteUrl("reddit.com"),
            score = attrTrimmedOrNull("score")?.toPointsLabel(),
            body = body,
            children = select(SHREDDIT_COMMENT_TAG)
                .filter { it.parentShredditComment() == this }
                .mapNotNull { it.toShredditComment() },
        )
    }

    private fun Element.parentShredditComment(): Element? =
        parents().firstOrNull { it.normalName() == SHREDDIT_COMMENT_TAG }

    private fun String.toPointsLabel(): String = if (this == "1") "1 point" else "$this points"

    private fun extractOldRedditPost(context: DomExtractorContext): ExtractorResult? {
        val post = context.document.selectFirst(".thing.link") ?: return null
        val body = post.selectFirst(".entry .usertext-body .md")?.deepCopy()
            ?: return null
        val commentRoots = context.document.selectFirst(".commentarea > .sitetable")
            ?.children()
            ?.filter { it.isRedditCommentElement() }
            ?.mapNotNull { it.toRedditComment() }
            .orEmpty()
        if (body.text().isBlank() || commentRoots.isEmpty()) return null

        val article = Element("article")
        article.appendChildNodesFrom(body)
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

    private fun Element.toRedditComment(depth: Int = 0): RedditComment? {
        val entry = children().firstOrNull { it.hasClass("entry") } ?: return null
        val author = entry.selectFirst(".tagline .author")?.text()?.trim().orEmpty()
        val score = entry.selectFirst(".tagline .score")?.text()?.trim()
        val date = entry.selectFirst(".tagline time[datetime]")
            ?.attr("datetime")
            ?.isoDatePart()
            .orEmpty()
        val body = entry.selectFirst(".usertext-body .md")?.deepCopy() ?: return null
        if (author.isBlank() || date.isBlank() || body.text().isBlank()) return null

        // Parsing and rendering recurse per reply level, so very deep chains continue as a flat list.
        val children = when {
            depth < MAX_THREAD_DEPTH -> replyElements().mapNotNull { it.toRedditComment(depth + 1) }
            depth == MAX_THREAD_DEPTH -> nestedReplyElements().mapNotNull { it.toRedditComment(depth + 1) }
            else -> emptyList()
        }

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
        quote.appendChildNodesFrom(comment.body)
        comment.children.forEach { child ->
            quote.appendRedditComment(child)
        }
    }

    private fun Element.isRedditCommentElement(): Boolean = hasClass("thing") && hasClass("comment")

    private fun Element.replyElements(): List<Element> = children()
        .firstOrNull { it.hasClass("child") }
        ?.children()
        ?.firstOrNull { it.hasClass("sitetable") }
        ?.children()
        ?.filter { it.isRedditCommentElement() }
        .orEmpty()

    private fun Element.nestedReplyElements(): List<Element> {
        val replies = mutableListOf<Element>()
        val pending = ArrayDeque(replyElements().asReversed())
        while (pending.isNotEmpty()) {
            val reply = pending.removeLast()
            replies.add(reply)
            reply.replyElements().asReversed().forEach(pending::addLast)
        }
        return replies
    }

    private data class RedditComment(
        val author: String,
        val date: String,
        val permalink: String,
        val score: String?,
        val body: Element,
        val children: List<RedditComment>,
    )

    private const val MAX_THREAD_DEPTH = 32
    private const val FULL_SIZE_IMAGE_PREFIX = "https://i.redd.it/"
    private const val SHREDDIT_COMMENT_TAG = "shreddit-comment"
    private const val SITE_NAME = "Reddit"
}
