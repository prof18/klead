package dev.defuddle.markdown

import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.resolveUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URI

object DefuddleMarkdownWriter {
    fun write(root: Element, baseUrl: String): String {
        val renderer = Renderer(baseUrl)
        return renderer.write(root)
    }
}

private class Renderer(private val baseUrl: String) {
    private val footnotes = linkedMapOf<String, String>()

    fun write(root: Element): String {
        collectFootnotes(root)
        val body = renderBlocks(root.childNodes(), listDepth = 0)
        val withFootnotes = if (footnotes.isEmpty()) {
            body
        } else {
            body + "\n\n" + footnotes.entries.joinToString("\n\n") { (id, text) -> "[^$id]: $text" }
        }
        return postProcess(withFootnotes)
    }

    private fun renderBlocks(nodes: List<Node>, listDepth: Int): String =
        nodes.mapNotNull { renderBlock(it, listDepth).takeIf(String::isNotBlank) }
            .joinToString("\n\n")

    private fun renderBlock(node: Node, listDepth: Int): String = when (node) {
        is TextNode -> escapeInline(node.text()).trim()
        is Element -> renderElementBlock(node, listDepth)
        else -> ""
    }

    private fun renderElementBlock(element: Element, listDepth: Int): String = when (element.normalName()) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            val level = element.normalName().removePrefix("h").toInt()
            val text = renderInline(element).trim()
            if (text.isBlank()) "" else "${"#".repeat(level)} $text"
        }

        "p" -> renderInline(element).trim()

        "blockquote" -> blockquote(renderBlocks(element.childNodes(), listDepth))

        "ul" -> renderList(element, ordered = false, listDepth = listDepth)

        "ol" -> renderList(element, ordered = true, listDepth = listDepth)

        "li" -> renderInline(element).trim()

        "pre" -> renderCodeBlock(element)

        "hr" -> "---"

        "img" -> renderImage(element)

        "picture" -> element.select("img").firstOrNull()?.let { renderImage(it) }.orEmpty()

        "figure" -> renderFigure(element, listDepth)

        "table" -> renderTable(element)

        "iframe" -> renderEmbeddedMedia(element)

        "section" -> if (element.hasAttr("data-footnotes")) "" else renderBlocks(element.childNodes(), listDepth)

        "div" -> if (element.hasClass("callout") || element.hasAttr("data-callout")) {
            renderCallout(element, listDepth)
        } else {
            renderBlocks(element.childNodes(), listDepth)
        }

        "article", "main" -> renderBlocks(element.childNodes(), listDepth)

        else -> renderBlocks(element.childNodes(), listDepth).ifBlank { renderInline(element).trim() }
    }

    private fun renderInline(element: Element): String = renderInlineNodes(element.childNodes(), inLink = false)

    private fun renderInlineNodes(nodes: List<Node>, inLink: Boolean): String = nodes.joinToString("") { node ->
        when (node) {
            is TextNode -> escapeInline(node.text())
            is Element -> renderInlineElement(node, inLink)
            else -> ""
        }
    }.replace(Regex("""[ \t]+"""), " ")

    private fun renderInlineElement(element: Element, inLink: Boolean): String = when (element.normalName()) {
        "strong", "b" -> renderDelimitedInline(element, inLink, "**")

        "em", "i" -> renderDelimitedInline(element, inLink, "*")

        "code" -> codeSpan(element.wholeText().ifBlank { element.text() })

        "a" -> renderLink(element, inLink)

        "img" -> renderImage(element)

        "br" -> "  \n"

        "sup" -> renderFootnoteReference(element) ?: renderInlineNodes(element.childNodes(), inLink)

        "sub", "span", "mark", "ins", "small" -> renderMath(
            element,
        ) ?: renderInlineNodes(element.childNodes(), inLink)

        "del", "s" -> renderDelimitedInline(element, inLink, "~~")

        "math" -> element.text()

        else -> renderInlineNodes(element.childNodes(), inLink)
    }

    private fun renderDelimitedInline(element: Element, inLink: Boolean, delimiter: String): String {
        val parts = renderInlineNodes(element.childNodes(), inLink).splitInlineWhitespace()
        if (parts.body.isBlank()) return parts.original
        return "${parts.leading}$delimiter${parts.body}$delimiter${parts.trailing}"
    }

    private fun renderLink(element: Element, inLink: Boolean): String {
        val parts = renderLinkTextNodes(element.childNodes()).splitInlineWhitespace()
        val text = parts.body
        val href = element.attr("href").trim()
        if (inLink || href.isBlank() || isDangerousUrl(href)) return parts.original
        val url = resolveUrl(baseUrl, href)
        if (url.isBlank()) return parts.original
        if (text.isBlank()) return parts.original
        return "${parts.leading}[$text](${escapeDestination(url)})${parts.trailing}"
    }

    private fun renderLinkTextNodes(nodes: List<Node>): String = nodes.joinToString("") { node ->
        when (node) {
            is TextNode -> escapeInline(node.text())
            is Element -> renderLinkTextElement(node)
            else -> ""
        }
    }.replace(Regex("""[ \t]+"""), " ")

    private fun renderLinkTextElement(element: Element): String = when (element.normalName()) {
        "br" -> " "
        "img" -> escapeInline(element.attr("alt").ifBlank { element.attr("title") })
        "math" -> escapeInline(element.text())
        else -> renderLinkTextNodes(element.childNodes())
    }

    private fun renderImage(element: Element): String {
        val sources = listOfNotNull(
            largestSrcsetUrl(element.attr("srcset")),
            element.attr("src").trim().takeIf { it.isNotBlank() },
        ).distinct()
        for (src in sources) {
            if (isDangerousUrl(src)) continue
            val url = resolveUrl(baseUrl, src)
            if (url.isNotBlank()) {
                return "![${escapeInline(element.attr("alt"))}](${escapeDestination(url)})"
            }
        }
        return ""
    }

    private fun renderEmbeddedMedia(element: Element): String {
        val href = element.attr("data-defuddle-video-url").trim().ifBlank {
            videoWatchUrlFromEmbed(element.attr("src").trim()).orEmpty()
        }.ifBlank {
            element.attr("src").trim()
        }
        if (href.isBlank() || isDangerousUrl(href)) return ""
        val url = resolveUrl(baseUrl, href)
        if (url.isBlank()) return ""

        val title = element.attr("title").trim().ifBlank {
            if (url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)) {
                "YouTube video"
            } else {
                "Embedded video"
            }
        }
        return "[${escapeInline(title)}](${escapeDestination(url)})"
    }

    private fun renderList(element: Element, ordered: Boolean, listDepth: Int): String {
        var number = element.attr("start").toIntOrNull() ?: 1
        return element.children().filter { it.normalName() == "li" }.mapNotNull { item ->
            val indent = "  ".repeat(listDepth)
            val inlineNodes = item.childNodes().filterNot { it is Element && it.normalName() in setOf("ul", "ol") }
            val firstLine = renderInlineNodes(inlineNodes, inLink = false).trim()
            val nested = item.children()
                .filter { it.normalName() == "ul" || it.normalName() == "ol" }
                .joinToString("\n") { renderElementBlock(it, listDepth + 1) }
            if (firstLine.isBlank() && nested.isBlank()) {
                return@mapNotNull null
            }
            val marker = if (ordered) "${number++}." else "-"
            val currentLine = if (firstLine.isBlank()) "" else "$indent$marker $firstLine"
            listOf(currentLine, nested)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }.joinToString("\n")
    }

    private fun blockquote(markdown: String): String = markdown.lines().joinToString("\n") { line ->
        if (line.isBlank()) ">" else "> $line"
    }

    private fun renderCodeBlock(element: Element): String {
        val code = element.selectFirst("code")
        val language = code?.attr("data-lang")?.ifBlank { null }
            ?: languageFrom(code)
            ?: languageFrom(element)
            ?: ""
        val text = (code?.wholeText() ?: element.wholeText()).normalizeFinalNewline()
        val fence = codeFence(text)
        return "$fence$language\n$text$fence"
    }

    private fun renderFigure(element: Element, listDepth: Int): String {
        val imageMarkdown = element.select("img").joinToString("\n") { renderImage(it) }.trim()
        val caption = element.select("figcaption").firstOrNull()?.let { renderInline(it).trim() }
        return listOfNotNull(
            imageMarkdown.takeIf { it.isNotBlank() },
            caption?.takeIf { it.isNotBlank() }?.let { "*$it*" },
        ).joinToString("\n\n").ifBlank { renderBlocks(element.childNodes(), listDepth) }
    }

    private fun renderTable(element: Element): String {
        if (element.selectFirst("table table") != null || element.select("th, td").any { it.hasComplexSpan() }) {
            return element.select("tr").joinToString("\n") { row ->
                row.select("th, td").joinToString(" ") { it.text().trim() }
            }.trim()
        }
        val rows = element.select("tr").map { row -> row.select("th, td").map { renderInline(it).escapeTableCell() } }
        if (rows.isEmpty() || rows.map { it.size }.distinct().size != 1) {
            return element.text()
        }
        val header = rows.first()
        val body = rows.drop(1)
        return buildString {
            append("| ${header.joinToString(" | ")} |\n")
            append("| ${header.joinToString(" | ") { "---" }} |")
            for (row in body) {
                append("\n| ${row.joinToString(" | ")} |")
            }
        }
    }

    private fun renderCallout(element: Element, listDepth: Int): String {
        val type = element.attr("data-callout").ifBlank { "note" }.lowercase()
        val title = element.selectFirst(".callout-title-inner")?.text()?.trim()?.ifBlank { null }
        val content = element.selectFirst(".callout-content") ?: element
        val body = renderBlocks(content.childNodes(), listDepth)
        return blockquote(
            listOf(
                "[!$type]${title?.let { " $it" }.orEmpty()}",
                body,
            ).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    private fun collectFootnotes(root: Element) {
        root.select("section[data-footnotes] li[id], ol.footnotes li[id]").forEach { item ->
            val id = cleanFootnoteId(item.id())
            footnotes.putIfAbsent(id, renderInline(item).trim())
        }
    }

    private fun renderFootnoteReference(element: Element): String? {
        val href = element.selectFirst("a[href^=#]")?.attr("href") ?: return null
        val id = cleanFootnoteId(href.removePrefix("#"))
        return "[^$id]"
    }

    private fun cleanFootnoteId(raw: String): String =
        raw.removePrefix("fnref").removePrefix("fn").trim('-', ':', '_').ifBlank { raw }

    private fun renderMath(element: Element): String? {
        val latex = element.attr("data-latex").trim().ifBlank { null } ?: return null
        val display = element.hasClass("display") || element.attr("display") == "block"
        return if (display) "$$\n$latex\n$$" else "$$latex$"
    }

    private fun largestSrcsetUrl(srcset: String): String? = srcset.split(srcsetDelimiter)
        .mapNotNull { candidate ->
            val parts = candidate.trim().split(Regex("""\s+"""))
            val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
            url to width
        }
        .maxByOrNull { it.second }
        ?.first

    private fun videoWatchUrlFromEmbed(src: String): String? {
        val uri = runCatching { URI(src) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.")
        val path = uri?.rawPath.orEmpty()
        val id = path.takeIf { host in youtubeEmbedHosts && it.startsWith("/embed/") }
            ?.removePrefix("/embed/")
            ?.substringBefore('/')
            ?.takeIf(youtubeId::matches)

        return id?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun codeSpan(text: String): String {
        val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        val ticks = "`".repeat(maxTicks + 1)
        return if ("`" in text) "$ticks $text $ticks" else "$ticks$text$ticks"
    }

    private fun codeFence(text: String): String {
        val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat((maxTicks + 1).coerceAtLeast(3))
    }

    private fun languageFrom(element: Element?): String? {
        if (element == null) return null
        val languageClass = element.classNames().firstOrNull { it.startsWith("language-") }
        return languageClass?.removePrefix("language-")
    }

    private fun escapeInline(text: String): String = text
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")

    private fun escapeDestination(url: String): String {
        val escaped = url.replace("(", "\\(").replace(")", "\\)")
        return if (escaped.any { it.isWhitespace() }) "<$escaped>" else escaped
    }

    private fun String.escapeTableCell(): String = replace("|", "\\|").replace("\n", " ").trim()

    private fun Element.hasComplexSpan(): Boolean = hasComplexSpan("rowspan") || hasComplexSpan("colspan")

    private fun Element.hasComplexSpan(name: String): Boolean {
        if (!hasAttr(name)) return false
        return attr(name).trim().toIntOrNull() != 1
    }

    private fun String.normalizeFinalNewline(): String = replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"

    private fun String.splitInlineWhitespace(): InlineWhitespace {
        val firstBodyIndex = indexOfFirst { !it.isWhitespace() }
        if (firstBodyIndex == -1) return InlineWhitespace(original = this)
        val lastBodyIndex = indexOfLast { !it.isWhitespace() }
        return InlineWhitespace(
            original = this,
            leading = substring(0, firstBodyIndex),
            body = substring(firstBodyIndex, lastBodyIndex + 1),
            trailing = substring(lastBodyIndex + 1),
        )
    }

    private fun postProcess(markdown: String): String {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return ""
        val result = mutableListOf<String>()
        var blankCount = 0
        var inFence = false
        for (line in normalized.lines()) {
            if (line.startsWith("```")) inFence = !inFence
            val processed = if (inFence) line else line.trimEnd()
            if (!inFence && processed.isBlank()) {
                blankCount++
                if (blankCount <= 2) result += ""
            } else {
                blankCount = 0
                result += processed
            }
        }
        return result.joinToString("\n").trimEnd() + "\n"
    }

    private val srcsetDelimiter = Regex(""",\s+""")
    private val youtubeId = Regex("""[A-Za-z0-9_-]{6,32}""")
    private val youtubeEmbedHosts = setOf("youtube.com", "youtube-nocookie.com")

    private data class InlineWhitespace(
        val original: String,
        val leading: String = "",
        val body: String = "",
        val trailing: String = "",
    )
}
