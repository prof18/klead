package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.isoDatePart
import com.prof18.klead.internal.dom.toAbsoluteSiteUrl
import org.jsoup.nodes.Element

internal object XProfile : Extractor {
    override val id: String = "x"
    override val domains: Set<String> = setOf("x.com", "twitter.com")

    override fun matches(context: ExtractorContext): Boolean =
        super.matches(context) || context.document.selectFirst("""[data-testid="twitterArticleRichTextView"]""") != null

    override fun extract(context: ExtractorContext): ExtractorResult? =
        extractLongformArticle(context) ?: extractConversation(context)

    private fun extractLongformArticle(context: ExtractorContext): ExtractorResult? {
        val richText = context.document.selectFirst("""[data-testid="twitterArticleRichTextView"]""") ?: return null
        if (richText.text().isBlank()) return null

        val article = Element("article")
        leadingTweetPhoto(richText)?.let { image ->
            article.appendChild(image.cleanXClone())
        }

        val body = richText.clone()
        body.cleanXContent()
        body.insertLongformTitleSpacer()
        body.childNodes().forEach { node ->
            article.appendChild(node.clone())
        }

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

    private fun extractConversation(context: ExtractorContext): ExtractorResult? {
        val tweets = context.document.select("""article[data-testid="tweet"]""")
            .mapNotNull { it.toTweet() }
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
        val body = selectFirst("""[data-testid="tweetText"]""")?.clone() ?: return null
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

    private fun Element.appendTweetBody(tweet: XTweet) {
        val paragraph = appendElement("p")
        tweet.body.childNodes().forEach { node ->
            paragraph.appendChild(node.clone())
        }
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

    private fun Element.cleanXClone(): Element = clone().also { it.cleanXContent() }

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
        val author = selectFirst("[itemprop=author]") ?: return null
        val name = author.selectFirst("""meta[itemprop="name"]""")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }
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

    private fun org.jsoup.nodes.Document.xArticleTitle(): String? = selectFirst(
        """[data-testid="twitter-article-title"]""",
    )
        ?.text()
        ?.trim()
        ?.ifBlank { null }

    private fun org.jsoup.nodes.Document.authorFromXOgTitle(): String? = selectFirst("""meta[property="og:title"]""")
        ?.attr("content")
        ?.trim()
        ?.let { title ->
            X_AUTHOR_TITLE_REGEX.find(title)?.groupValues?.getOrNull(1)
        }
        ?.replace(Regex("""^\(\d+\)\s*"""), "")
        ?.trim()
        ?.ifBlank { null }

    private fun ExtractorContext.authorFromStatusUrl(): String? {
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

    private fun ExtractorContext.statusId(): String? =
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
    )

    private val boldStylePattern = Regex("""font-weight\s*:\s*(?:bold|[6-9]00)\b""")
    private val xImageNameParameter = Regex("""([?&]name=)[^&#]+""")
    private val X_AUTHOR_TITLE_REGEX = Regex("""^(.+?)\s+on\s+X:""")
    private const val SITE_NAME = "X (Twitter)"
}
