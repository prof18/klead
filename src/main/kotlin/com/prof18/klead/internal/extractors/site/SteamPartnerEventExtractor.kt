package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.media.TrustedEmbeds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object SteamPartnerEventExtractor : Extractor {
    override val id: String = "steam-partner-event"

    override fun matches(context: ExtractorContext): Boolean =
        context.document.selectFirst("#application_config[data-partnereventstore]") != null

    override fun extract(context: ExtractorContext): ExtractorResult? {
        val event = context.document.partnerEvent() ?: return null
        val announcement = event["announcement_body"] as? JsonObject ?: return null
        val body = announcement.string("body") ?: return null
        val article = body.toArticleHtml()

        return article
            .takeUnless { it.text().isBlank() && it.select("iframe").isEmpty() }
            ?.let {
                ExtractorResult(
                    contentHtml = it.outerHtml(),
                    metadata = ExtractorMetadata(
                        title = announcement.string("headline") ?: event.string("event_name"),
                        author = context.document.groupName(),
                        site = "",
                    ),
                )
            }
    }

    private fun String.toArticleHtml(): Element {
        val article = Element("article")
        var lastIndex = 0
        var matched = false
        var preserveLeadingMediaSpacer = false
        for (match in blockPattern.findAll(this)) {
            preserveLeadingMediaSpacer = appendParagraphs(article, substring(lastIndex, match.range.first)) ||
                preserveLeadingMediaSpacer
            match.groupValues[1].takeIf { it.isNotBlank() }?.let { rawParagraph ->
                preserveLeadingMediaSpacer = appendParagraphs(article, rawParagraph)
                matched = true
            }
            match.youtubeId()?.let { videoId ->
                appendYoutube(article, videoId, preserveLeadingMediaSpacer)
                preserveLeadingMediaSpacer = false
                matched = true
            }
            lastIndex = match.range.last + 1
        }
        appendParagraphs(article, substring(lastIndex))

        if (!matched && article.children().isEmpty()) {
            appendParagraphs(article, this)
        }
        return article
    }

    private fun appendParagraphs(article: Element, raw: String): Boolean {
        val normalized = raw.normalizeLineEndings()
        val hasTrailingBlankLine = trailingBlankLinePattern.containsMatchIn(normalized)
        val paragraphs = normalized
            .trim()
            .split(blankLinePattern)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        paragraphs.forEach { paragraph ->
            val element = article.appendElement("p")
            element.appendInlineBbCode(paragraph)
        }
        return hasTrailingBlankLine && paragraphs.isNotEmpty()
    }

    private fun Element.appendInlineBbCode(raw: String) {
        var lastIndex = 0
        for (match in urlPattern.findAll(raw)) {
            appendPlainText(raw.substring(lastIndex, match.range.first))
            val href = match.groupValues[1].ifBlank { match.groupValues[2] }
            val label = match.groupValues[3].stripSimpleBbCode()
            appendElement("a").attr("href", href).text(label)
            lastIndex = match.range.last + 1
        }
        appendPlainText(raw.substring(lastIndex))
    }

    private fun Element.appendPlainText(raw: String) {
        val text = raw.stripSimpleBbCode()
        if (text.isNotEmpty()) {
            appendText(text)
        }
    }

    private fun appendYoutube(article: Element, rawId: String, preserveLeadingSpacer: Boolean) {
        val id = rawId.substringBefore(';').trim()
        val media = TrustedEmbeds.youtubeVideoFromId(id) ?: return
        val iframe = article.appendElement("iframe")
            .attr("title", media.defaultTitle)
            .attr("src", media.normalizedIframeSrc.orEmpty())
            .attr("data-klead-video-url", media.watchUrl)
        if (preserveLeadingSpacer) {
            iframe.attr("data-klead-leading-spacer", "true")
        }
    }

    private fun Document.groupName(): String? {
        val config = selectFirst("#application_config[data-groupvanityinfo]") ?: return null
        return config.attr("data-groupvanityinfo").parseJsonArray()
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.string("group_name") }
    }

    private fun Document.partnerEvent(): JsonObject? = selectFirst("#application_config[data-partnereventstore]")
        ?.attr("data-partnereventstore")
        ?.parseJsonArray()
        ?.firstNotNullOfOrNull { it as? JsonObject }

    private fun String.parseJsonArray(): JsonArray? = runCatching {
        JSON.parseToJsonElement(this) as? JsonArray
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun MatchResult.youtubeId(): String? = groupValues[2].ifBlank { groupValues[3] }.ifBlank { null }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

    private fun String.stripSimpleBbCode(): String = simpleTagPattern.replace(this, "")

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val blockPattern = Regex(
        """(?is)\[p](.*?)\[/p]|\[previewyoutube=(?:"([^"]+)"|([^\]]+))]\s*\[/previewyoutube]""",
    )
    private val urlPattern = Regex("""(?is)\[url=(?:"([^"]+)"|([^\]]+))](.*?)\[/url]""")
    private val simpleTagPattern = Regex("""(?i)\[/?(?:b|i|u|p)]""")
    private val blankLinePattern = Regex("""\n\s*\n""")
    private val trailingBlankLinePattern = Regex("""\n\s*\n\s*$""")
}
