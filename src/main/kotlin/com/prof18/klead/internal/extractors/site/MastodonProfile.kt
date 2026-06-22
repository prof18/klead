package com.prof18.klead.internal.extractors.site

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.dom.isoDatePart
import org.jsoup.nodes.Element
import java.net.URI

internal object MastodonProfile : Extractor {
    override val id: String = "mastodon"

    override fun matches(context: ExtractorContext): Boolean =
        context.document.selectFirst(".detailed-status") != null &&
            context.document.selectFirst("#mastodon, .app-holder, script#initial-state") != null

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val baseUrl = context.baseUrl()
        val mainStatus = context.document.selectFirst(".detailed-status") ?: return null
        val mainContent = mainStatus.selectFirst(".status__content__text")?.clone() ?: return null
        val mainDisplayName = mainStatus.selectFirst(".display-name__html")?.text()?.trim().orEmpty()
        val mainAccount = mainStatus.selectFirst(".display-name__account")?.text()?.trim().orEmpty()
        val instanceName = mainAccount.substringAfterLast("@", missingDelimiterValue = "")
            .ifBlank { context.document.selectFirst("""meta[property="og:site_name"]""")?.attr("content").orEmpty() }

        val threadStatuses = context.document.select(".status.status--in-thread")
            .mapNotNull { it.toMastodonStatus(baseUrl) }

        val article = Element("article")
        article.appendStatusBody(mainContent, baseUrl)
        mainStatus.select(".media-gallery__item-thumbnail[href]").forEach { thumbnail ->
            val image = thumbnail.selectFirst("img[src]") ?: return@forEach
            article.appendElement("p")
                .appendElement("img")
                .attr("src", thumbnail.attr("href").toAbsoluteUrl(baseUrl))
                .attr("alt", image.attr("alt"))
        }

        val continuations = mutableListOf<MastodonStatus>()
        val commentRoots = mutableListOf<MastodonStatus>()
        var currentRoot: MastodonStatus? = null
        var commentsStarted = false
        for (status in threadStatuses) {
            if (!commentsStarted && !status.isTopLevelReply && status.account.matchesMainAccount(mainAccount)) {
                continuations += status
                continue
            }

            commentsStarted = true
            if (status.isTopLevelReply || currentRoot == null) {
                commentRoots += status
                currentRoot = status
            } else {
                currentRoot.children += status
            }
        }

        continuations.forEach { continuation ->
            article.appendElement("hr")
            article.appendStatusBody(continuation.body, baseUrl)
            article.appendCards(continuation.cards)
        }

        if (commentRoots.isNotEmpty()) {
            article.appendElement("hr")
            article.appendElement("h2").text("Comments")
            commentRoots.forEach { comment ->
                article.appendMastodonComment(comment, baseUrl)
            }
        }

        if (article.text().isBlank()) return null
        return ExtractorResult(
            contentHtml = article.outerHtml(),
            metadata = ExtractorMetadata(
                title = if (mainDisplayName.isNotBlank() && instanceName.isNotBlank()) {
                    "Post by $mainDisplayName on $instanceName"
                } else {
                    null
                },
                author = mainDisplayName.ifBlank { null },
                site = instanceName.ifBlank { SITE_NAME },
            ),
        )
    }

    private fun Element.toMastodonStatus(baseUrl: String): MastodonStatus? {
        val body = selectFirst(".status__content__text")?.clone() ?: return null
        val displayName = selectFirst(".display-name__html")?.text()?.trim().orEmpty()
        val account = selectFirst(".display-name__account")?.text()?.trim().orEmpty()
        val date = selectFirst(".status__relative-time time[datetime]")
            ?.attr("datetime")
            ?.isoDatePart()
            .orEmpty()
        val permalink = selectFirst(".status__relative-time[href]")
            ?.attr("href")
            ?.toAbsoluteUrl(baseUrl)
            .orEmpty()
        if (displayName.isBlank() || account.isBlank() || date.isBlank() || permalink.isBlank()) return null

        return MastodonStatus(
            author = "$displayName $account",
            account = account,
            date = date,
            permalink = permalink,
            body = body,
            isTopLevelReply = hasClass("status--first-in-thread"),
            cards = select(".status-card[href]").mapNotNull { it.toMastodonCard(baseUrl) },
        )
    }

    private fun Element.toMastodonCard(baseUrl: String): MastodonCard? {
        val url = attr("href").toAbsoluteUrl(baseUrl)
        val title = selectFirst(".status-card__title")?.text()?.trim().orEmpty()
        val description = selectFirst(".status-card__description")?.text()?.trim()
        val image = selectFirst(".status-card__image img[src]")?.attr("src")?.toAbsoluteUrl(baseUrl)
        if (url.isBlank() || title.isBlank()) return null
        return MastodonCard(
            url = url,
            title = title,
            description = description,
            image = image,
        )
    }

    private fun Element.appendMastodonComment(comment: MastodonStatus, baseUrl: String) {
        val quote = appendElement("blockquote")
        quote.appendElement("p").also { header ->
            header.appendElement("strong").text(comment.author)
            header.appendText(" · ")
            header.appendElement("a")
                .attr("href", comment.permalink)
                .text(comment.date)
        }
        quote.appendStatusBody(comment.body, baseUrl)
        quote.appendCards(comment.cards)
        comment.children.forEach { child ->
            quote.appendMastodonComment(child, baseUrl)
        }
    }

    private fun Element.appendStatusBody(body: Element, baseUrl: String) {
        body.prepareMastodonBody(baseUrl)
        body.childNodes().forEach { node ->
            appendChild(node.clone())
        }
    }

    private fun Element.appendCards(cards: List<MastodonCard>) {
        cards.forEach { card ->
            card.image?.let { image ->
                appendElement("p")
                    .appendElement("a")
                    .attr("href", card.url)
                    .appendElement("img")
                    .attr("src", image)
                    .attr("alt", card.title)
            }
            appendElement("p")
                .appendElement("a")
                .attr("href", card.url)
                .text(card.title)
            card.description?.takeIf { it.isNotBlank() }?.let { description ->
                appendElement("p").text(description)
            }
        }
    }

    private fun Element.prepareMastodonBody(baseUrl: String) {
        select(".invisible").remove()
        select("a[href]").forEach { link ->
            link.attr("href", link.attr("href").toAbsoluteUrl(baseUrl))
        }
        select("img[src]").forEach { image ->
            image.attr("src", image.attr("src").toAbsoluteUrl(baseUrl))
        }
    }

    private fun ExtractorContext.baseUrl(): String = url?.takeIf { it.isNotBlank() }
        ?: document.selectFirst("""meta[property=og:url][content]""")?.attr("content").orEmpty()

    private fun String.matchesMainAccount(mainAccount: String): Boolean {
        if (isBlank() || mainAccount.isBlank()) return false
        return this == mainAccount || this == mainAccount.substringBeforeLast("@", missingDelimiterValue = mainAccount)
    }

    private fun String.toAbsoluteUrl(baseUrl: String): String {
        if (isBlank()) return this
        return runCatching { URI(baseUrl).resolve(this).toString() }.getOrElse { this }
    }

    private data class MastodonStatus(
        val author: String,
        val account: String,
        val date: String,
        val permalink: String,
        val body: Element,
        val isTopLevelReply: Boolean,
        val cards: List<MastodonCard>,
        val children: MutableList<MastodonStatus> = mutableListOf(),
    )

    private data class MastodonCard(val url: String, val title: String, val description: String?, val image: String?)

    private const val SITE_NAME = "Mastodon"
}
