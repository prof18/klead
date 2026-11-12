package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.cloneElement
import com.prof18.klead.internal.dom.isoDatePart
import com.prof18.klead.internal.dom.toAbsoluteSiteUrl
import com.prof18.klead.internal.dom.transferChildrenTo
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import kotlin.time.Instant

internal object XProfile : DomExtractor {
    override val id: String = "x"
    override val domains: Set<String> = setOf("x.com", "twitter.com")

    override fun matches(context: DomExtractorContext): Boolean =
        super.matches(context) || context.document.selectFirst("""[data-testid="twitterArticleRichTextView"]""") != null

    override fun extract(context: DomExtractorContext): ExtractorResult? =
        extractLoggedOutArticle(context) ?: extractLongformArticle(context) ?: extractConversation(context)

    private fun extractLoggedOutArticle(context: DomExtractorContext): ExtractorResult? {
        val body = context.document.select(".x-article-body")
            .firstOrNull { it.text().isNotBlank() }
            ?: return null
        // Newer pages wrap cover, title, byline and body in sibling data-article-part blocks.
        val source = body.parents().firstOrNull { it.attr("data-article-part") == "body" }?.parent()
            ?: body.parent()
            ?: body
        val title = source.selectFirst("[itemprop=headline], h1")
        val cover = source.selectFirst("img[itemprop=image][src]")
            ?: source.selectFirst("""[data-article-part="cover"] img[src]""")
            ?: title?.previousElementSibling()?.takeIf { it.normalName() == "img" && it.hasAttr("src") }
        val article = Element("article")
        cover?.let { image ->
            article.appendChild(image.cleanXClone())
        }
        title?.let { article.appendChild(it.cleanXClone()) }
        article.appendChild(body.cleanXClone())

        // X can omit schema and itemprop attributes. Anchor on its prose block so the
        // surrounding engagement controls and duplicate article in the replies stay out.
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = title?.text()?.trim()?.ifBlank { null },
                author = source.longformAuthor()
                    ?: context.document.authorFromXOgTitle()
                    ?: context.authorFromStatusUrl(),
                site = SITE_NAME,
            ),
        )
    }

    private fun extractLongformArticle(context: DomExtractorContext): ExtractorResult? {
        val richText = context.document.selectFirst("""[data-testid="twitterArticleRichTextView"]""") ?: return null
        if (richText.text().isBlank()) return null

        val article = Element("article")
        leadingTweetPhoto(richText)?.let { image ->
            article.appendChild(image.cleanXClone())
        }

        val body = richText.cloneElement()
        body.cleanXContent()
        body.insertLongformTitleSpacer()
        body.transferChildrenTo(article)

        if (article.text().isBlank() && article.select("img[src]").isEmpty()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = context.document.xArticleTitle(),
                author = richText.longformAuthor()
                    ?: context.document.authorFromXOgTitle()
                    ?: context.authorFromStatusUrl(),
                site = SITE_NAME,
            ),
        )
    }

    private fun leadingTweetPhoto(richText: Element): Element? = richText.ownerDocument()
        ?.select("""[data-testid="tweetPhoto"] img[src]""")
        ?.firstOrNull { image -> image.parents().none { it === richText } }

    private fun extractConversation(context: DomExtractorContext): ExtractorResult? {
        val tweets = context.document.select("""article[data-testid="tweet"]""")
            .mapNotNull { it.toTweet() }
            .ifEmpty { context.loggedOutPosts() }
        if (tweets.isEmpty()) return null

        val mainIndex = tweets.indexOfFirst { tweet ->
            val statusId = context.statusId()
            statusId != null && tweet.statusUrl?.contains("/status/$statusId") == true
        }.takeIf { it >= 0 } ?: 0

        val mainTweet = tweets[mainIndex]
        val article = Element("article")
        article.appendTweetBody(mainTweet)

        val comments = tweets.drop(mainIndex + 1)
            .filter { it.body.text().isNotBlank() }
        if (comments.isNotEmpty()) {
            article.appendElement("hr")
            article.appendElement("h2").text("Comments")
            comments.forEach { comment ->
                article.appendComment(comment)
            }
        }

        if (article.text().isBlank()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = mainTweet.handle
                    ?.let { "Post by $it on X" }
                    ?: mainTweet.displayName?.let { "Post by $it on X" },
                author = mainTweet.handle ?: mainTweet.displayName,
                site = SITE_NAME,
            ),
        )
    }

    private fun Element.toTweet(): XTweet? {
        val body = selectFirst("""[data-testid="tweetText"]""")?.cloneElement() ?: return null
        body.cleanXContent()
        if (body.text().isBlank()) return null

        val userName = selectFirst("""[data-testid="User-Name"]""")
        val displayName = userName?.select("a[href]")
            ?.firstOrNull { link -> link.text().trim().isNotBlank() && !link.text().trim().startsWith("@") }
            ?.text()
            ?.trim()
        val handle = userName?.select("a[href]")
            ?.firstOrNull { link -> link.text().trim().startsWith("@") }
            ?.text()
            ?.trim()
        val statusLink = select("a[href]")
            .firstOrNull { link -> "/status/" in link.attr("href") && link.selectFirst("time[datetime]") != null }
            ?: select("a[href]").firstOrNull { link -> "/status/" in link.attr("href") }
        val statusUrl = statusLink?.attr("href")?.toAbsoluteSiteUrl("x.com")
        val date = statusLink
            ?.selectFirst("time[datetime]")
            ?.attr("datetime")
            ?.isoDatePart()
            ?.takeIf { it.isNotBlank() }

        return XTweet(
            displayName = displayName,
            handle = handle,
            date = date,
            statusUrl = statusUrl,
            body = body,
        )
    }

    // X's server-rendered markup for logged-out readers has no test ids. Posts stream in
    // twice (fallback and resolved timeline), so the status link de-duplicates them.
    private fun DomExtractorContext.loggedOutPosts(): List<XTweet> {
        val seenStatusUrls = mutableSetOf<String>()
        return document.select("article")
            .filter { it.selectFirst("[data-post-avatar]") != null }
            .mapNotNull { it.toLoggedOutPost() }
            .filter { post -> post.statusUrl == null || seenStatusUrls.add(post.statusUrl) }
    }

    private fun Element.toLoggedOutPost(): XTweet? {
        val body = select("div[dir=auto]")
            .firstOrNull { text -> text.parents().none { it.normalName() == "a" } && text.text().isNotBlank() }
            ?.cloneElement()
            ?: return null
        body.select("button, script").remove()
        body.cleanXContent()

        val links = select("a[href]")
        val statusLink = links.firstOrNull { statusPathPattern.matches(it.attr("href")) }
        val media = select("img[src]")
            .filter { "pbs.twimg.com/media/" in it.attr("src") }
            .map { image -> image.cleanXClone().also { it.removeAttr("srcset").removeAttr("sizes") } }

        return XTweet(
            displayName = links.firstOrNull { link ->
                val text = link.text().trim()
                text.isNotBlank() && !text.startsWith("@") && link !== statusLink
            }?.text()?.trim(),
            handle = links.firstOrNull { it.text().trim().startsWith("@") }?.text()?.trim(),
            date = statusLink?.parent()?.relativeTimestampDate(),
            statusUrl = statusLink?.attr("href")?.toAbsoluteSiteUrl("x.com"),
            body = body,
            media = media,
        )
    }

    private fun Element.relativeTimestampDate(): String? = select("script")
        .firstNotNullOfOrNull { script -> relativeTimestampPattern.find(script.data())?.groupValues?.get(1) }
        ?.toLongOrNull()
        ?.let { Instant.fromEpochMilliseconds(it).toString().isoDatePart() }

    private fun Element.appendTweetBody(tweet: XTweet) {
        appendPreWrapParagraphs(tweet.body)
        tweet.media.forEach { appendChild(it) }
    }

    // Post text is rendered with `white-space: pre-wrap`, so its line breaks live in text
    // nodes. Blank lines become paragraphs and single line breaks become <br>.
    private fun Element.appendPreWrapParagraphs(source: Element) {
        source.select("span").forEach { it.unwrap() }
        var paragraph = appendElement("p")
        var pendingBreaks = 0

        fun flushBreaks() {
            if (paragraph.childNodeSize() > 0) {
                when {
                    pendingBreaks >= 2 -> paragraph = appendElement("p")
                    pendingBreaks == 1 -> paragraph.appendElement("br")
                }
            }
            pendingBreaks = 0
        }

        source.childNodes().toList().forEach { node ->
            if (node is TextNode) {
                node.getWholeText().split('\n').forEachIndexed { index, line ->
                    if (index > 0) pendingBreaks++
                    if (line.isNotEmpty() && !(line.isBlank() && pendingBreaks > 0)) {
                        flushBreaks()
                        paragraph.appendText(line)
                    }
                }
            } else {
                flushBreaks()
                paragraph.appendChild(node)
            }
        }
        if (paragraph.childNodeSize() == 0) paragraph.remove()
    }

    private fun Element.appendComment(comment: XTweet) {
        val quote = appendElement("blockquote")
        quote.appendElement("p").also { header ->
            header.appendElement("strong").text(comment.authorLabel())
            if (comment.date != null) {
                header.appendText(" · ")
                val date = header.appendElement("a").text(comment.date)
                comment.statusUrl?.let { date.attr("href", it) }
            }
        }
        quote.appendTweetBody(comment)
    }

    private fun Element.cleanXClone(): Element = cloneElement().also { it.cleanXContent() }

    private fun Element.insertLongformTitleSpacer() {
        if (selectFirst("[itemprop=author]") == null) return
        selectFirst("""[data-testid="twitter-article-title"]""")
            ?.after(Element("div").attr("data-klead-blank-spacer", "x-title"))
    }

    private fun Element.cleanXContent() {
        unwrapInlineLinkContainers()
        select("a[href]").forEach { link ->
            link.attr("href", link.attr("href").normalizeXHref())
        }
        select("img[src]").forEach { image ->
            image.attr("src", image.attr("src").toLargeXImageUrl())
        }
        select("[style]").forEach { element ->
            if (element.normalName() == "span" && element.isBoldStyle()) {
                element.tagName("strong")
                element.removeAttr("style")
            }
        }
    }

    private fun Element.unwrapInlineLinkContainers() {
        select("div").forEach { element ->
            if (element.childrenSize() == 1 && element.child(0).normalName() == "a" && element.ownText().isBlank()) {
                element.unwrap()
            }
        }
    }

    private fun Element.isBoldStyle(): Boolean {
        val style = attr("style").lowercase()
        return boldStylePattern.containsMatchIn(style)
    }

    private fun XTweet.authorLabel(): String = listOfNotNull(displayName, handle)
        .joinToString(" ")
        .ifBlank { handle ?: displayName ?: "Comment" }

    private fun Element.longformAuthor(): String? {
        val author = selectFirst("[itemprop~=author]") ?: return null
        val name = author.selectFirst("""meta[itemprop="name"]""")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }
            ?: author.selectFirst("[itemprop=name]")?.text()?.trim()?.ifBlank { null }
        val handle = author.selectFirst("""meta[itemprop="additionalName"]""")
            ?.attr("content")
            ?.trim()
            ?.trimStart('@')
            ?.ifBlank { null }
        return when {
            name != null && handle != null -> "$name (@$handle)"
            name != null -> name
            handle != null -> "@$handle"
            else -> null
        }
    }

    private fun com.fleeksoft.ksoup.nodes.Document.xArticleTitle(): String? = selectFirst(
        """[data-testid="twitter-article-title"]""",
    )
        ?.text()
        ?.trim()
        ?.ifBlank { null }

    private fun com.fleeksoft.ksoup.nodes.Document.authorFromXOgTitle(): String? = selectFirst(
        """meta[property="og:title"]""",
    )
        ?.attr("content")
        ?.trim()
        ?.let { title ->
            X_AUTHOR_TITLE_REGEX.find(title)?.groupValues?.getOrNull(1)
        }
        ?.replace(Regex("""^\(\d+\)\s*"""), "")
        ?.trim()
        ?.ifBlank { null }

    private fun DomExtractorContext.authorFromStatusUrl(): String? {
        val rawUrl = url.orEmpty()
        val path = when {
            "x.com/" in rawUrl -> rawUrl.substringAfter("x.com/")
            "twitter.com/" in rawUrl -> rawUrl.substringAfter("twitter.com/")
            else -> return null
        }
        return path
            .substringBefore("/")
            .takeIf { it.isNotBlank() && it != "i" }
            ?.let { "@$it" }
    }

    private fun DomExtractorContext.statusId(): String? =
        url.orEmpty().substringAfter("/status/", missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.takeWhile(Char::isDigit)
            ?.takeIf { it.isNotBlank() }

    private fun String.normalizeXHref(): String {
        if (!startsWith("//")) return this
        val withoutProtocol = removePrefix("//")
        val host = withoutProtocol.substringBefore('/').lowercase()
        val path = withoutProtocol.substringAfter('/', missingDelimiterValue = "")
        return if (path.isBlank()) "https://$host/" else "https://$host/$path"
    }

    private fun String.toLargeXImageUrl(): String =
        xImageNameParameter.replace(this) { match -> "${match.groupValues[1]}large" }

    private data class XTweet(
        val displayName: String?,
        val handle: String?,
        val date: String?,
        val statusUrl: String?,
        val body: Element,
        val media: List<Element> = emptyList(),
    )

    private val statusPathPattern = Regex("""^(?:https://(?:x|twitter)\.com)?/\w+/status/\d+/?$""")
    private val relativeTimestampPattern = Regex(""""timestamp"\s*:\s*(\d+)""")
    private val boldStylePattern = Regex("""font-weight\s*:\s*(?:bold|[6-9]00)\b""")
    private val xImageNameParameter = Regex("""([?&]name=)[^&#]+""")
    private val X_AUTHOR_TITLE_REGEX = Regex("""^(.+?)\s+on\s+X(?::|$)""")
    private const val SITE_NAME = "X (Twitter)"
}
