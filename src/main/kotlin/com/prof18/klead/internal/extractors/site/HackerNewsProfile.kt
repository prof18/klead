package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import org.jsoup.nodes.Element

internal object HackerNewsProfile : Extractor {
    override val id: String = "hacker-news"
    override val domains: Set<String> = setOf("news.ycombinator.com")

    override fun extract(context: ExtractorContext): ExtractorResult? =
        extractSingleComment(context) ?: extractStoryComments(context) ?: extractListing(context)

    private fun extractSingleComment(context: ExtractorContext): ExtractorResult? {
        val fatItem = context.document.selectFirst("table.fatitem") ?: return null
        val comment = fatItem.selectFirst(".commtext") ?: return null
        val header = fatItem.selectFirst(".comhead") ?: return null
        val author = header.selectFirst(".hnuser")?.text()?.trim().orEmpty()
        val date = header.selectFirst(".age[title]")?.attr("title")?.datePart().orEmpty()
        if (author.isBlank() || date.isBlank()) return null

        val article = Element("article")
        article.appendElement("p").also { paragraph ->
            paragraph.appendElement("strong").text(author)
            paragraph.appendText(" · $date")
        }
        article.appendCommentBody(comment)

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = "Comment by $author: ${comment.text().trim().collapseForTitle().take(50)}...",
                author = author,
                site = SITE_NAME,
            ),
        )
    }

    private fun extractListing(context: ExtractorContext): ExtractorResult? {
        val rows = context.document.select("tr.athing")
            .mapNotNull { row -> row.toHackerNewsListingItem() }
        if (rows.isEmpty()) return null

        val article = Element("article")
        val list = article.appendElement("ol")
        rows.forEach { item ->
            list.appendElement("li").appendListingItem(item)
        }
        context.document.selectFirst("a.morelink[href]")?.let { more ->
            article.appendElement("p")
                .appendElement("a")
                .attr("href", more.attr("href").toHackerNewsUrl())
                .text(more.text())
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.title().ifBlank { SITE_NAME },
                site = SITE_NAME,
            ),
        )
    }

    private fun extractStoryComments(context: ExtractorContext): ExtractorResult? {
        val story = context.document.selectFirst("table.fatitem tr.athing") ?: return null
        val storyLink = story.selectFirst(".titleline > a[href]") ?: return null
        val storyUrl = storyLink.attr("href").trim()
        if (storyUrl.isBlank()) return null
        val author = context.document.selectFirst("table.fatitem .subtext .hnuser")
            ?.text()
            ?.trim()
            ?.ifBlank { null }

        val comments = context.document.select("table.comment-tree tr.comtr")
            .mapNotNull { it.toHackerNewsComment() }
        if (comments.isEmpty()) return null

        val article = Element("article")
        article.appendElement("p")
            .appendElement("a")
            .attr("href", storyUrl)
            .text(storyUrl)
        article.appendElement("hr")
        article.appendElement("h2").text("Comments")

        comments.toCommentTree().forEach { comment ->
            article.appendHackerNewsComment(comment)
        }

        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = storyLink.text().trim().ifBlank { null },
                author = author,
                site = SITE_NAME,
            ),
        )
    }

    private fun Element.toHackerNewsComment(): HackerNewsComment? {
        val id = id().trim()
        val header = selectFirst(".comhead") ?: return null
        val author = header.selectFirst(".hnuser")?.text()?.trim().orEmpty()
        val date = header.selectFirst(".age[title]")?.attr("title")?.datePart().orEmpty()
        val body = selectFirst(".commtext")?.clone() ?: return null
        if (id.isBlank() || author.isBlank() || date.isBlank() || body.text().isBlank()) return null

        val depth = selectFirst("td.ind img[width]")
            ?.attr("width")
            ?.toIntOrNull()
            ?.div(HN_INDENT_WIDTH)
            ?: 0
        return HackerNewsComment(
            id = id,
            author = author,
            date = date,
            points = header.selectFirst(".score")?.text()?.trim(),
            depth = depth,
            body = body,
        )
    }

    private fun List<HackerNewsComment>.toCommentTree(): List<HackerNewsComment> {
        val roots = mutableListOf<HackerNewsComment>()
        val stack = mutableListOf<HackerNewsComment>()
        for (comment in this) {
            while (stack.size > comment.depth) {
                stack.removeLast()
            }
            val parent = stack.lastOrNull()
            if (parent == null) {
                roots += comment
            } else {
                parent.children += comment
            }
            stack += comment
        }
        return roots
    }

    private fun Element.appendHackerNewsComment(comment: HackerNewsComment) {
        val quote = appendElement("blockquote")
        quote.appendElement("p").also { header ->
            header.appendElement("strong").text(comment.author)
            header.appendText(" · ")
            header.appendElement("a")
                .attr("href", "https://news.ycombinator.com/item?id=${comment.id}")
                .text(comment.date)
            comment.points?.takeIf { it.isNotBlank() }?.let { points ->
                header.appendText(" · $points")
            }
        }
        quote.appendCommentBody(comment.body)
        comment.children.forEach { child ->
            quote.appendHackerNewsComment(child)
        }
    }

    private fun Element.appendCommentBody(body: Element) {
        if (body.children().isEmpty()) {
            body.textNodes().forEach { textNode ->
                textNode.text().split("\n").map(String::trim).filter(String::isNotBlank).forEach { text ->
                    appendElement("p").text(text)
                }
            }
            return
        }
        body.childNodes().forEach { node ->
            appendChild(node.clone())
        }
    }

    private fun Element.toHackerNewsListingItem(): HackerNewsListingItem? {
        val titleLink = selectFirst(".titleline > a[href]") ?: return null
        val title = titleLink.text().trim()
        val subtext = nextElementSibling()?.selectFirst(".subtext") ?: return null
        val commentLink = subtext.select("a[href]")
            .firstOrNull { link -> link.text().contains("comment", ignoreCase = true) }
            ?: return null
        val points = subtext.selectFirst(".score")?.text()?.trim().orEmpty()
        val author = subtext.selectFirst(".hnuser")?.text()?.trim().orEmpty()
        val comments = commentLink.text().replace('\u00A0', ' ').trim()
        if (title.isBlank() || points.isBlank() || author.isBlank() || comments.isBlank()) return null

        return HackerNewsListingItem(
            title = title,
            href = titleLink.attr("href").toHackerNewsUrl(),
            site = selectFirst(".sitestr")?.text()?.trim(),
            points = points,
            author = author,
            comments = comments,
            commentsHref = commentLink.attr("href").toHackerNewsUrl(),
        )
    }

    private fun Element.appendListingItem(item: HackerNewsListingItem) {
        appendElement("a")
            .attr("href", item.href)
            .text(item.title)
        item.site?.takeIf { it.isNotBlank() }?.let { site ->
            appendText(" ($site)")
        }
        appendElement("br")
        appendText("${item.points} · by ${item.author} · ")
        appendElement("a")
            .attr("href", item.commentsHref)
            .text(item.comments)
    }

    private fun String.datePart(): String = substringBefore("T").substringBefore(" ").trim()

    private fun String.collapseForTitle(): String = replace(WHITESPACE_PATTERN, " ")

    private fun String.toHackerNewsUrl(): String = when {
        startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true) -> this
        startsWith("/") -> "https://news.ycombinator.com$this"
        else -> "https://news.ycombinator.com/$this"
    }

    private data class HackerNewsComment(
        val id: String,
        val author: String,
        val date: String,
        val points: String?,
        val depth: Int,
        val body: Element,
        val children: MutableList<HackerNewsComment> = mutableListOf(),
    )

    private data class HackerNewsListingItem(
        val title: String,
        val href: String,
        val site: String?,
        val points: String,
        val author: String,
        val comments: String,
        val commentsHref: String,
    )

    private const val HN_INDENT_WIDTH = 40
    private const val SITE_NAME = "Hacker News"
    private val WHITESPACE_PATTERN = Regex("""\s+""")
}
