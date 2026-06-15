package dev.defuddle.markdown

import dev.defuddle.dom.isDangerousUrl
import dev.defuddle.dom.resolveUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object DefuddleMarkdownWriter {
    fun write(
        root: Element,
        baseUrl: String,
    ): String {
        val renderer = Renderer(baseUrl)
        return renderer.write(root)
    }
}

private class Renderer(
    private val baseUrl: String,
) {
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

    private fun renderBlocks(
        nodes: List<Node>,
        listDepth: Int,
    ): String =
        nodes.mapNotNull { renderBlock(it, listDepth).takeIf(String::isNotBlank) }
            .joinToString("\n\n")

    private fun renderBlock(
        node: Node,
        listDepth: Int,
    ): String =
        when (node) {
            is TextNode -> escapeInline(node.text()).trim()
            is Element -> renderElementBlock(node, listDepth)
            else -> ""
        }

    private fun renderElementBlock(
        element: Element,
        listDepth: Int,
    ): String =
        when (element.normalName()) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.normalName().removePrefix("h").toInt()
                "${"#".repeat(level)} ${renderInline(element).trim()}"
            }
            "p" -> renderInline(element).trim()
            "blockquote" -> blockquote(renderBlocks(element.childNodes(), listDepth))
            "ul" -> renderList(element, ordered = false, listDepth = listDepth)
            "ol" -> renderList(element, ordered = true, listDepth = listDepth)
            "li" -> renderInline(element).trim()
            "pre" -> renderCodeBlock(element)
            "hr" -> "---"
            "img" -> renderImage(element)
            "picture" -> element.selectFirst("img")?.let { renderImage(it) }.orEmpty()
            "figure" -> renderFigure(element, listDepth)
            "table" -> renderTable(element)
            "section" -> if (element.hasAttr("data-footnotes")) "" else renderBlocks(element.childNodes(), listDepth)
            "div" -> if (element.hasClass("callout") || element.hasAttr("data-callout")) {
                renderCallout(element, listDepth)
            } else {
                renderBlocks(element.childNodes(), listDepth)
            }
            "article", "main" -> renderBlocks(element.childNodes(), listDepth)
            else -> renderBlocks(element.childNodes(), listDepth).ifBlank { renderInline(element).trim() }
        }

    private fun renderInline(element: Element): String =
        renderInlineNodes(element.childNodes(), inLink = false)

    private fun renderInlineNodes(
        nodes: List<Node>,
        inLink: Boolean,
    ): String =
        nodes.joinToString("") { node ->
            when (node) {
                is TextNode -> escapeInline(node.text())
                is Element -> renderInlineElement(node, inLink)
                else -> ""
            }
        }.replace(Regex("""[ \t]+"""), " ")

    private fun renderInlineElement(
        element: Element,
        inLink: Boolean,
    ): String =
        when (element.normalName()) {
            "strong", "b" -> "**${renderInlineNodes(element.childNodes(), inLink)}**"
            "em", "i" -> "*${renderInlineNodes(element.childNodes(), inLink)}*"
            "code" -> codeSpan(element.wholeText().ifBlank { element.text() })
            "a" -> renderLink(element, inLink)
            "img" -> renderImage(element)
            "br" -> "  \n"
            "sup" -> renderFootnoteReference(element) ?: renderInlineNodes(element.childNodes(), inLink)
            "sub", "span", "mark", "ins", "small" -> renderMath(element) ?: renderInlineNodes(element.childNodes(), inLink)
            "del", "s" -> "~~${renderInlineNodes(element.childNodes(), inLink)}~~"
            "math" -> element.text()
            else -> renderInlineNodes(element.childNodes(), inLink)
        }

    private fun renderLink(
        element: Element,
        inLink: Boolean,
    ): String {
        val text = renderInlineNodes(element.childNodes(), inLink = true).trim()
        val href = element.attr("href").trim()
        if (inLink || href.isBlank() || isDangerousUrl(href)) return text
        val url = resolveUrl(baseUrl, href)
        if (url.isBlank()) return text
        if (text.isBlank() && element.selectFirst("img") != null) return renderInlineNodes(element.childNodes(), inLink = true)
        return "[$text](${escapeDestination(url)})"
    }

    private fun renderImage(element: Element): String {
        val src = largestSrcsetUrl(element.attr("srcset")) ?: element.attr("src").trim()
        if (src.isBlank() || isDangerousUrl(src)) return ""
        val url = resolveUrl(baseUrl, src)
        if (url.isBlank()) return ""
        return "![${escapeInline(element.attr("alt"))}](${escapeDestination(url)})"
    }

    private fun renderList(
        element: Element,
        ordered: Boolean,
        listDepth: Int,
    ): String {
        var number = element.attr("start").toIntOrNull() ?: 1
        return element.children().filter { it.normalName() == "li" }.joinToString("\n") { item ->
            val marker = if (ordered) "${number++}." else "-"
            val indent = "  ".repeat(listDepth)
            val inlineNodes = item.childNodes().filterNot { it is Element && it.normalName() in setOf("ul", "ol") }
            val firstLine = renderInlineNodes(inlineNodes, inLink = false).trim()
            val nested = item.children()
                .filter { it.normalName() == "ul" || it.normalName() == "ol" }
                .joinToString("\n") { renderElementBlock(it, listDepth + 1) }
            listOf("$indent$marker $firstLine", nested)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
    }

    private fun blockquote(markdown: String): String =
        markdown.lines().joinToString("\n") { line ->
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

    private fun renderFigure(
        element: Element,
        listDepth: Int,
    ): String {
        val imageMarkdown = element.select("img").joinToString("\n") { renderImage(it) }.trim()
        val caption = element.selectFirst("figcaption")?.let { renderInline(it).trim() }
        return listOfNotNull(
            imageMarkdown.takeIf { it.isNotBlank() },
            caption?.takeIf { it.isNotBlank() }?.let { "*$it*" },
        ).joinToString("\n\n").ifBlank { renderBlocks(element.childNodes(), listDepth) }
    }

    private fun renderTable(element: Element): String {
        if (element.selectFirst("table table, [rowspan], [colspan]") != null) {
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

    private fun renderCallout(
        element: Element,
        listDepth: Int,
    ): String {
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

    private fun largestSrcsetUrl(srcset: String): String? =
        srcset.split(",")
            .mapNotNull { candidate ->
                val parts = candidate.trim().split(Regex("""\s+"""))
                val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
                url to width
            }
            .maxByOrNull { it.second }
            ?.first

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

    private fun escapeInline(text: String): String =
        text
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

    private fun String.escapeTableCell(): String =
        replace("|", "\\|").replace("\n", " ").trim()

    private fun String.normalizeFinalNewline(): String =
        replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"

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
}
