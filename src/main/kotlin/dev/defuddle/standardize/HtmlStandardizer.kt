package dev.defuddle.standardize

import dev.defuddle.dom.replaceWithChildren
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.URI

object HtmlStandardizer {
    fun apply(content: Element, title: String?) {
        normalizeVideoEmbeds(content)
        normalizeCallouts(content)
        normalizeHeadings(content, title)
        normalizeCodeBlocks(content)
        normalizeImages(content)
        normalizeFootnotes(content)
        normalizeTables(content)
        removeEmptyWrappers(content)
    }

    private fun normalizeHeadings(content: Element, title: String?) {
        val firstHeading = content.selectFirst("h1, h2")
        if (title != null && firstHeading?.text()?.isDuplicateTitle(title) == true) {
            firstHeading.remove()
        }
        content.select("h1, h2, h3, h4, h5, h6").forEach { heading ->
            heading.select("a[href^=#].anchor, a[href^=#].permalink, a[href^=#][aria-hidden=true]").remove()
        }
    }

    private fun String.isDuplicateTitle(title: String): Boolean = comparableTitle() == title.comparableTitle()

    private fun String.comparableTitle(): String = trim()
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('“', '"')
        .replace('”', '"')
        .replace(Regex("""\s+"""), " ")
        .lowercase()

    private fun normalizeCodeBlocks(content: Element) {
        content.select("pre").forEach { pre ->
            pre.select(".lineno, .line-number, .line-numbers-rows, [aria-hidden=true]").remove()
            val code = pre.selectFirst("code") ?: Element("code").also { code ->
                code.text(pre.text())
                pre.empty()
                pre.appendChild(code)
            }
            val language = languageFrom(pre) ?: languageFrom(code)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
        }
        content.select("code > pre").forEach { pre ->
            pre.parent()?.replaceWith(pre)
        }
    }

    private fun normalizeImages(content: Element) {
        content.select("img").forEach { image ->
            val replacement = firstAttr(image, "data-src", "data-original", "data-lazy-src", "data-url")
            if (replacement != null && isPlaceholderImage(image.attr("src"))) {
                image.attr("src", replacement)
            }
            firstAttr(image, "data-srcset", "data-lazy-srcset")?.let { image.attr("srcset", it) }
        }
    }

    private fun normalizeVideoEmbeds(content: Element) {
        content.select("iframe[src]").forEach { iframe ->
            val video = trustedVideoFromUrl(iframe.attr("src")) ?: return@forEach
            val title = iframe.attr("title").trim().ifBlank { video.defaultTitle }
            iframe.clearAttributes()
            applyVideoAttributes(iframe, video, title)
        }

        content.select(".hidden_video[data-video-id]").forEach { placeholder ->
            val video = youtubeVideoFromId(placeholder.attr("data-video-id"))
                ?: trustedVideoFromUrl(
                    placeholder.selectFirst(
                        """a[href*="youtube.com/watch"], a[href*="youtu.be/"]""",
                    )?.attr("href").orEmpty(),
                )
                ?: return@forEach
            val iframe = Element("iframe")
            applyVideoAttributes(iframe, video, video.defaultTitle)
            placeholder.replaceWith(iframe)
        }
    }

    private fun applyVideoAttributes(iframe: Element, video: VideoEmbed, title: String) {
        iframe.attr("src", video.embedUrl)
        iframe.attr("title", title.ifBlank { video.defaultTitle })
        iframe.attr("loading", "lazy")
        iframe.attr("allowfullscreen", "")
        iframe.attr(
            "allow",
            "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share",
        )
        iframe.attr("data-defuddle-video-url", video.watchUrl)
    }

    private fun normalizeCallouts(content: Element) {
        content.select("blockquote").forEach { blockquote ->
            val firstParagraph = blockquote.selectFirst("p") ?: return@forEach
            val marker = CALLOUT_MARKER.matchEntire(firstParagraph.text().trim()) ?: return@forEach
            val type = marker.groupValues[1].lowercase()
            firstParagraph.remove()
            blockquote.tagName("div")
            blockquote.addClass("callout")
            blockquote.attr("data-callout", type)

            val existingChildren = blockquote.childNodes().toList()
            blockquote.empty()
            blockquote.appendElement("div")
                .addClass("callout-title")
                .appendElement("div")
                .addClass("callout-title-inner")
                .text(type.replaceFirstChar { it.uppercase() })
            val body = blockquote.appendElement("div").addClass("callout-content")
            existingChildren.forEach { body.appendChild(it) }
        }
    }

    private fun normalizeFootnotes(content: Element) {
        content.select("ol.footnotes, ol[id*=footnote], ol[id*=fn]").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    private fun normalizeTables(content: Element) {
        content.select("table").forEach { table ->
            val cells = table.select("td, th")
            if (table.hasClass("layout") || cells.size == 1) {
                val nodes = cells.firstOrNull()?.childNodes()?.toList().orEmpty()
                nodes.forEach { table.before(it) }
                table.remove()
            }
        }
    }

    private fun removeEmptyWrappers(content: Element) {
        content.select("span, div").toList().asReversed().forEach { element ->
            if (element.children().isEmpty() && element.text().isBlank()) {
                element.remove()
            } else if (element.tagName() == "span" && element.attributes().isEmpty()) {
                element.replaceWithChildren()
            }
        }
        content.childNodes().filterIsInstance<TextNode>().forEach { text ->
            if (text.text().isBlank()) text.remove()
        }
    }

    private fun languageFrom(element: Element): String? {
        val attrs = listOf(element.className(), element.attr("data-lang"), element.attr("data-language"))
        for (attr in attrs) {
            LANGUAGE_REGEX.find(attr)?.let { return it.groupValues[1].lowercase() }
            attr.takeIf {
                it.isNotBlank() && it.length <= 24 &&
                    it.all { char -> char.isLetterOrDigit() || char in "+#_-" }
            }
                ?.let { return it.lowercase() }
        }
        return null
    }

    private fun firstAttr(element: Element, vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> element.attr(name).trim().ifBlank { null } }

    private fun isPlaceholderImage(src: String): Boolean = src.isBlank() ||
        src.startsWith("data:image/svg", ignoreCase = true) ||
        src.startsWith("data:image/gif", ignoreCase = true)

    private fun trustedVideoFromUrl(url: String): VideoEmbed? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https") return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = uri.rawPath.orEmpty()

        return when {
            host in YOUTUBE_EMBED_HOSTS && path.startsWith("/embed/") -> {
                youtubeVideoFromId(path.removePrefix("/embed/").substringBefore('/'))
            }

            host in YOUTUBE_EMBED_HOSTS && path == "/watch" -> {
                youtubeVideoFromId(youtubeWatchIdFromQuery(uri.rawQuery.orEmpty()))
            }

            host == "youtu.be" -> {
                youtubeVideoFromId(path.trim('/').substringBefore('/'))
            }

            else -> null
        }
    }

    private fun youtubeWatchIdFromQuery(query: String): String = query.split('&')
        .firstNotNullOfOrNull { part ->
            val pieces = part.split('=', limit = 2)
            pieces.takeIf { it.size == 2 && it[0] == "v" }?.get(1)
        }.orEmpty()

    private fun youtubeVideoFromId(rawId: String): VideoEmbed? {
        val id = rawId.trim()
        if (!YOUTUBE_ID.matches(id)) return null
        return VideoEmbed(
            embedUrl = "https://www.youtube-nocookie.com/embed/$id",
            watchUrl = "https://www.youtube.com/watch?v=$id",
            defaultTitle = "YouTube video",
        )
    }

    private val LANGUAGE_REGEX = Regex("""(?:^|\s)language-([A-Za-z0-9_+#-]+)(?:\s|$)""")
    private val CALLOUT_MARKER = Regex("""\[!(\w+)]""")
    private val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{6,32}""")
    private val YOUTUBE_EMBED_HOSTS = setOf("youtube.com", "youtube-nocookie.com")

    private data class VideoEmbed(val embedUrl: String, val watchUrl: String, val defaultTitle: String)
}
