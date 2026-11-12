package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.extractors.ExtractorMetadata
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.extractors.DomExtractor
import com.prof18.klead.internal.extractors.DomExtractorContext
import com.prof18.klead.internal.json.parseToJsonElementOrNull
import com.prof18.klead.internal.media.TrustedEmbeds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object SteamPartnerEventExtractor : DomExtractor {
    override val id: String = "steam-partner-event"

    override fun matches(context: DomExtractorContext): Boolean =
        context.signals.hasId("application_config") &&
            context.document.selectFirst("#application_config[data-partnereventstore]") != null

    override fun extract(context: DomExtractorContext): ExtractorResult? {
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
        for (match in bbCodeBlocks()) {
            preserveLeadingMediaSpacer = appendParagraphs(article, substring(lastIndex, match.range.first)) ||
                preserveLeadingMediaSpacer
            match.paragraph.takeIf { it.isNotBlank() }?.let { rawParagraph ->
                preserveLeadingMediaSpacer = appendParagraphs(article, rawParagraph)
                matched = true
            }
            match.youtubeId?.let { videoId ->
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
        // Same as matching \n\s*\n\s*$, without the regex's heavy backtracking on long newline runs.
        val hasTrailingBlankLine = normalized.takeLastWhile { it in REGEX_WHITESPACE }.count { it == '\n' } >= 2
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
        for (match in raw.bbCodeLinks()) {
            appendPlainText(raw.substring(lastIndex, match.range.first))
            val href = match.href
            val label = match.label.stripSimpleBbCode()
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

    private fun String.parseJsonArray(): JsonArray? = JSON.parseToJsonElementOrNull(this) as? JsonArray

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private class BbCodeBlock(val range: IntRange, val paragraph: String, val youtubeId: String?)

    private class BbCodeLink(val range: IntRange, val href: String, val label: String)

    // Finds what the regex (?is)\[p](.*?)\[/p]|\[previewyoutube=(?:"([^"]+)"|([^\]]+))]\s*\[/previewyoutube]
    // would, in linear time: the regex rescanned to the end of the body from every unclosed [p].
    private fun String.bbCodeBlocks(): List<BbCodeBlock> {
        val blocks = mutableListOf<BbCodeBlock>()
        val paragraphEnd = ForwardTokenFinder(this, "[/p]")
        val tagValue = BbCodeTagValue(this)
        var index = 0
        while (true) {
            val open = indexOf('[', index)
            if (open < 0) break
            val block = if (regionMatches(open, "[p]", 0, 3, ignoreCase = true)) {
                val end = paragraphEnd.from(open + 3)
                if (end < 0) null else BbCodeBlock(open..end + 3, substring(open + 3, end), null)
            } else if (regionMatches(open, PREVIEW_YOUTUBE_OPEN, 0, PREVIEW_YOUTUBE_OPEN.length, ignoreCase = true)) {
                tagValue.candidates(open + PREVIEW_YOUTUBE_OPEN.length).firstNotNullOfOrNull { (value, afterValue) ->
                    var close = afterValue
                    while (close < length && this[close] in REGEX_WHITESPACE) close++
                    if (regionMatches(close, PREVIEW_YOUTUBE_CLOSE, 0, PREVIEW_YOUTUBE_CLOSE.length, ignoreCase = true)) {
                        BbCodeBlock(open until close + PREVIEW_YOUTUBE_CLOSE.length, "", value)
                    } else {
                        null
                    }
                }
            } else {
                null
            }
            index = block?.range?.last?.plus(1) ?: (open + 1)
            if (block != null) blocks += block
        }
        return blocks
    }

    // Same as matching (?is)\[url=(?:"([^"]+)"|([^\]]+))](.*?)\[/url], without rescanning per opener.
    private fun String.bbCodeLinks(): List<BbCodeLink> {
        val links = mutableListOf<BbCodeLink>()
        val linkEnd = ForwardTokenFinder(this, URL_CLOSE)
        val tagValue = BbCodeTagValue(this)
        var index = 0
        while (true) {
            val open = indexOf('[', index)
            if (open < 0) break
            val link = if (regionMatches(open, URL_OPEN, 0, URL_OPEN.length, ignoreCase = true)) {
                tagValue.candidates(open + URL_OPEN.length).firstNotNullOfOrNull { (value, afterValue) ->
                    val end = linkEnd.from(afterValue)
                    if (end < 0) null else BbCodeLink(open until end + URL_CLOSE.length, value, substring(afterValue, end))
                }
            } else {
                null
            }
            index = link?.range?.last?.plus(1) ?: (open + 1)
            if (link != null) links += link
        }
        return links
    }

    // Reads a tag value the way (?:"([^"]+)"|([^\]]+))] does: the quoted reading first, then the bare one.
    // Each candidate is the value and the index just after its closing bracket.
    private class BbCodeTagValue(private val text: String) {
        private val quote = ForwardTokenFinder(text, "\"")
        private val bracket = ForwardTokenFinder(text, "]")

        fun candidates(start: Int): List<Pair<String, Int>> {
            val result = ArrayList<Pair<String, Int>>(2)
            if (text.getOrNull(start) == '"') {
                val endQuote = quote.from(start + 1)
                if (endQuote > start + 1 && text.getOrNull(endQuote + 1) == ']') {
                    result += text.substring(start + 1, endQuote) to endQuote + 2
                }
            }
            val end = bracket.from(start)
            if (end > start) result += text.substring(start, end) to end + 1
            return result
        }
    }

    // The next index of a token at or after a position. Scans move forward through the text, so the last
    // answer is reused while it still lies ahead, keeping repeated lookups linear overall.
    private class ForwardTokenFinder(private val text: String, private val token: String) {
        private var lastStart = Int.MAX_VALUE
        private var lastFound = -1

        fun from(start: Int): Int {
            if (start >= lastStart && (lastFound < 0 || lastFound >= start)) return lastFound
            lastStart = start
            lastFound = text.indexOf(token, start, ignoreCase = true)
            return lastFound
        }
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

    private fun String.stripSimpleBbCode(): String = simpleTagPattern.replace(this, "")

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private const val PREVIEW_YOUTUBE_OPEN = "[previewyoutube="
    private const val PREVIEW_YOUTUBE_CLOSE = "[/previewyoutube]"
    private const val URL_OPEN = "[url="
    private const val URL_CLOSE = "[/url]"
    private val simpleTagPattern = Regex("""(?i)\[/?(?:b|i|u|p)]""")
    private val blankLinePattern = Regex("""\n\s*\n""")
    private const val REGEX_WHITESPACE = " \t\n\u000B\u000C\r"
}
