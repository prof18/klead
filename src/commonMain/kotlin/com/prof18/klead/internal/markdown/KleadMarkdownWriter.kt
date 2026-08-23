package com.prof18.klead.internal.markdown

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.resolveUrl

internal object KleadMarkdownWriter {
    fun write(root: Element, baseUrl: String): String {
        val renderer = Renderer(baseUrl)
        return renderer.write(root)
    }
}

private class Renderer(private val baseUrl: String) {
    private val footnotes = linkedMapOf<String, String>()
    private val footnoteTargets = mutableMapOf<String, String>()
    private var footnoteHeading: String? = null

    fun write(root: Element): String {
        collectFootnotes(root)
        val body = renderBlocks(root.childNodes(), listDepth = 0)
        val withFootnotes = if (footnotes.isEmpty()) {
            body
        } else {
            val definitions = footnotes.entries.joinToString("\n\n") { (id, text) -> "[^$id]: $text" }
            body + "\n\n" + listOfNotNull(footnoteHeading, definitions).joinToString("\n\n")
        }
        return postProcessMarkdown(withFootnotes)
    }

    private fun renderBlocks(nodes: List<Node>, listDepth: Int): String {
        val blocks = mutableListOf<RenderedBlock>()
        val inlineNodes = mutableListOf<Node>()

        fun addBlock(markdown: String, standaloneImage: Boolean, codeBlock: Boolean, listBlock: Boolean) {
            if (markdown.isBlank()) return
            if (standaloneImage && blocks.lastOrNull()?.standaloneImage == true) {
                val previous = blocks.removeLast()
                blocks += previous.copy(markdown = "${previous.markdown} $markdown")
            } else {
                blocks += RenderedBlock(
                    markdown = markdown,
                    standaloneImage = standaloneImage,
                    codeBlock = codeBlock,
                    listBlock = listBlock,
                )
            }
        }

        fun flushInlineNodes() {
            if (inlineNodes.isEmpty()) return
            val hasPreviousBlock = blocks.isNotEmpty()
            addBlock(
                markdown = renderInlineNodes(
                    inlineNodes,
                    inLink = false,
                ).trimInlineBlock(preserveLeadingHardBreaks = hasPreviousBlock),
                standaloneImage = false,
                codeBlock = false,
                listBlock = false,
            )
            inlineNodes.clear()
        }

        nodes.forEachIndexed { index, node ->
            if (node is TextNode && node.text().isBlank()) {
                if (inlineNodes.isNotEmpty() && nodes.nextNonBlankNodeAfter(index)?.isInlineFlowNode() == true) {
                    inlineNodes += TextNode(" ")
                }
                return@forEachIndexed
            }
            if (node.isInlineFlowNode()) {
                inlineNodes += node
                return@forEachIndexed
            }
            flushInlineNodes()
            val markdown = renderBlock(node, listDepth).takeIf(String::isNotBlank) ?: return@forEachIndexed
            addBlock(
                markdown = markdown,
                standaloneImage = node.isStandaloneImageBlock(),
                codeBlock = node.isCodeBlock(),
                listBlock = node.isListBlock(),
            )
        }
        flushInlineNodes()
        return blocks.joinToStringWithSeparators()
    }

    private fun List<Node>.nextNonBlankNodeAfter(index: Int): Node? =
        drop(index + 1).firstOrNull { node -> node !is TextNode || node.text().isNotBlank() }

    private fun renderBlock(node: Node, listDepth: Int): String = when (node) {
        is TextNode -> escapeInline(node.text()).trim()
        is Element -> renderElementBlock(node, listDepth)
        else -> ""
    }

    private fun renderElementBlock(element: Element, listDepth: Int): String {
        if (element.hasClass("callout") || element.hasAttr("data-callout")) {
            return renderCallout(element, listDepth)
        }

        return when (element.normalName()) {
            "strong", "b", "em", "i", "del", "s", "sub", "sup", "mark", "ins", "small", "math" -> {
                renderInlineElement(element, inLink = false).trim()
            }

            "code", "pre" -> renderPreformattedBlock(element)

            "span" -> {
                if (element.attr("data-as") == "p") {
                    renderInline(element).trim()
                } else {
                    renderBlocks(element.childNodes(), listDepth).ifBlank { renderInline(element).trim() }
                }
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.normalName().removePrefix("h").toInt()
                val text = renderInline(element).trim()
                if (text.isBlank()) "" else "${"#".repeat(level)} $text"
            }

            "p" -> renderInline(element).trimInlineBlock()

            "a" -> renderLinkedImage(element, baseUrl)
                ?: renderLinkedHeadingBlock(element, listDepth)
                ?: renderInline(element).trimInlineBlock()

            "blockquote" -> blockquote(renderBlocks(element.childNodes(), listDepth))

            "ul" -> renderList(element, ordered = false, listDepth = listDepth)

            "ol" -> renderList(element, ordered = true, listDepth = listDepth)

            "li" -> renderInline(element).trimInlineBlock()

            "hr" -> "---"

            "img" -> renderImage(element, baseUrl)

            "picture" -> element.select("img").firstOrNull()?.let { renderImage(it, baseUrl) }.orEmpty()

            "figure" -> renderFigure(element, listDepth)

            "table" -> renderTable(element)

            "iframe" -> renderEmbeddedMedia(element, baseUrl)

            "svg" -> renderSvg(element)

            "section", "div", "article", "main" -> renderContainerBlock(element, listDepth)

            else -> renderBlocks(element.childNodes(), listDepth).ifBlank { renderInline(element).trimInlineBlock() }
        }
    }

    private fun renderPreformattedBlock(element: Element): String =
        if (element.normalName() == "pre" || element.isBlockCodeElement()) {
            renderCodeBlock(element)
        } else {
            renderInlineElement(element, inLink = false).trim()
        }

    private fun renderContainerBlock(element: Element, listDepth: Int): String =
        if (element.hasAttr("data-klead-blank-spacer")) {
            PRESERVED_BLANK_SPACER
        } else if (element.normalName() == "section" && element.hasAttr("data-footnotes")) {
            ""
        } else {
            renderBlocks(element.childNodes(), listDepth)
        }

    private fun renderInline(element: Element): String = renderInlineNodes(element.childNodes(), inLink = false)

    private fun renderInlineNodes(nodes: List<Node>, inLink: Boolean): String {
        val rendered = StringBuilder()
        var previousInlineWasLinkedImage = false
        nodes.forEachIndexed { index, node ->
            var value = renderInlineNode(node, inLink)
            value = normalizeLeadingInlineSpacing(
                rendered = rendered,
                nodes = nodes,
                index = index,
                node = node,
                value = value,
                previousInlineWasLinkedImage = previousInlineWasLinkedImage,
            )
            value = normalizeTrailingInlineSpacing(nodes, index, node, value)
            appendSpecialInlineSpacing(rendered, nodes, index, node, value)
            rendered.append(value)
            previousInlineWasLinkedImage = node is Element && node.linkedImageOnlyChild() != null
        }
        return rendered.toString()
            .replace(inlineWhitespacePattern, " ")
            .replace(inlineIndentedNewlinePattern, "\n")
    }

    private fun renderInlineNode(node: Node, inLink: Boolean): String = when (node) {
        is TextNode -> escapeInline(node.text())
        is Element -> renderFootnoteReference(node) ?: renderInlineElement(node, inLink)
        else -> ""
    }

    private fun renderInlineElement(element: Element, inLink: Boolean): String = when (element.normalName()) {
        "strong", "b" -> renderDelimitedInline(element, inLink, "**")

        "em", "i" -> renderDelimitedInline(element, inLink, "*")

        "code" -> codeSpan(renderCodeSpanContent(element).ifBlank { element.text() })

        "a" -> renderLink(element, inLink)

        "img" -> renderImage(element, baseUrl)

        "br" -> "  \n"

        "sup" -> renderFootnoteReference(element) ?: renderHtmlInlineTag(element, "sup", inLink)

        "sub" -> renderHtmlInlineTag(element, "sub", inLink)

        "span", "mark", "ins", "small" -> renderMath(
            element,
        ) ?: renderInlineNodes(element.childNodes(), inLink)

        "del", "s" -> renderDelimitedInline(element, inLink, "~~")

        "math" -> element.text()

        "svg" -> renderSvg(element)

        else -> {
            if (element.normalName() in blockFlowTags) {
                renderBlocks(element.childNodes(), listDepth = 0)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { "\n$it\n" }
                    .orEmpty()
            } else {
                renderInlineNodes(element.childNodes(), inLink)
            }
        }
    }

    private fun renderDelimitedInline(element: Element, inLink: Boolean, delimiter: String): String {
        val parts = renderInlineNodes(element.childNodes(), inLink).splitInlineWhitespace()
        if (parts.body.isBlank()) return parts.original
        return "${parts.leading}$delimiter${parts.body}$delimiter${parts.trailing}"
    }

    private fun renderHtmlInlineTag(element: Element, tag: String, inLink: Boolean): String {
        val body = renderInlineNodes(element.childNodes(), inLink).trim()
        return if (body.isBlank()) "" else "<$tag>$body</$tag>"
    }

    private fun renderLink(element: Element, inLink: Boolean): String {
        if (!inLink) {
            renderLinkedImage(element, baseUrl)?.let { return it }
        }
        val parts = renderLinkTextNodes(element.childNodes()).splitInlineWhitespace()
        val text = parts.body
        val href = element.attr("href").trim()
        if (inLink || href.isBlank() || isDangerousUrl(href)) {
            return renderInlineNodes(element.childNodes(), inLink = true)
        }
        val url = if (href.startsWith("#")) href else resolveUrl(baseUrl, href)
        if (url.isBlank()) return parts.original
        if (text.isBlank()) return parts.original
        val destination = linkDestination(
            url = url.withRootSlashForBareOrigin(),
            title = element.attr("title"),
            visibleText = element.text(),
        )
        return "${parts.leading}[$text]($destination)${parts.trailing}"
    }

    private fun renderLinkedHeadingBlock(element: Element, listDepth: Int): String? {
        if (element.children().none { it.normalName() in blockFlowTags }) return null
        val url = element.safeResolvedHref(baseUrl) ?: return null
        return element.childNodes().mapNotNull { child ->
            when (child) {
                is Element -> if (child.isHeading()) {
                    renderLinkedHeading(child, url)
                } else {
                    renderBlock(child, listDepth)
                }

                is TextNode -> escapeInline(child.text()).trim()

                else -> ""
            }.takeIf { it.isNotBlank() }
        }.joinToString("\n\n")
            .takeIf { it.isNotBlank() }
    }

    private fun renderLinkedHeading(element: Element, href: String): String {
        val level = element.normalName().removePrefix("h").toInt()
        val text = renderInline(element).trim()
        if (text.isBlank()) return ""
        return "${"#".repeat(level)} [$text](${escapeDestination(href.withRootSlashForBareOrigin())})"
    }

    private fun Element.isHeading(): Boolean = normalName().matches(headingTagPattern)

    private fun renderLinkTextNodes(nodes: List<Node>): String = nodes.joinToString("") { node ->
        when (node) {
            is TextNode -> escapeInline(node.text())
            is Element -> renderLinkTextElement(node)
            else -> ""
        }
    }.replace(horizontalWhitespacePattern, " ")

    private fun renderLinkTextElement(element: Element): String = when (element.normalName()) {
        "br" -> " "
        "img" -> escapeInline(element.attr("alt").ifBlank { element.attr("title") })
        "math" -> escapeInline(element.text())
        else -> renderInlineElement(element, inLink = true)
    }

    private fun renderList(element: Element, ordered: Boolean, listDepth: Int): String {
        var number = element.attr("start").toIntOrNull() ?: 1
        return element.children().filter { it.normalName() == "li" }.mapIndexedNotNull { itemIndex, item ->
            val indent = listItemIndent(listDepth, itemIndex)
            val inlineNodes = item.childNodes().filterNot { it is Element && it.normalName() in setOf("ul", "ol") }
            val firstLine = renderListItemInlineNodes(inlineNodes).trim()
            val nested = item.children()
                .filter { it.normalName() == "ul" || it.normalName() == "ol" }
                .joinToString("\n") { renderElementBlock(it, listDepth + 1) }
            if (firstLine.isBlank() && nested.isBlank()) {
                return@mapIndexedNotNull null
            }
            val marker = if (ordered) "${number++}." else "-"
            val currentLine = if (firstLine.isBlank()) "" else renderListItemLine(indent, marker, firstLine)
            listOf(currentLine, nested)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }.joinToString("\n")
    }

    private fun renderListItemInlineNodes(nodes: List<Node>): String {
        if (nodes.hasSignParagraphListItemShape()) {
            return nodes.joinToString("") { node ->
                when (node) {
                    is TextNode -> escapeInline(node.text())

                    is Element -> if (node.isFlattenableSignListBlock()) {
                        renderInline(node).trim()
                    } else {
                        renderInlineElement(node, inLink = false)
                    }

                    else -> ""
                }
            }.replace(inlineWhitespacePattern, " ")
        }
        return renderInlineNodes(nodes, inLink = false)
    }

    private fun renderFigure(element: Element, listDepth: Int): String {
        if (element.hasArticleBodyWrapper()) {
            return renderBlocks(element.childNodes(), listDepth)
        }
        val imageMarkdown = element.select("img")
            .distinctBy(::imageDedupKey)
            .joinToString("\n") { renderImage(it, baseUrl) }
            .trim()
        val caption = element.select("figcaption").firstOrNull()?.let { renderCaptionInline(it).trim() }
        return listOfNotNull(
            imageMarkdown.takeIf { it.isNotBlank() },
            caption?.takeIf { it.isNotBlank() }?.let { "*$it*" },
        ).joinToString("\n\n").ifBlank { renderBlocks(element.childNodes(), listDepth) }
    }

    private fun Element.hasArticleBodyWrapper(): Boolean = children().any { child ->
        child.classNames().contains("content-wrapper") &&
            child.select("p, ul, ol, blockquote, pre, table, figure").isNotEmpty()
    }

    private fun renderTable(element: Element): String {
        if (element.select("th, td").all { it.text().trim().isBlank() }) return ""
        if (element.selectFirst("table table") != null || element.select("th, td").any { it.hasComplexSpan() }) {
            return element.select("tr").joinToString("\n") { row ->
                row.select("th, td").joinToString(" ") { it.text().trim() }
            }.trim()
        }
        val rows = element.select("tr").map { row -> row.select("th, td").map { renderInline(it).escapeTableCell() } }
        if (rows.isEmpty()) return element.text()
        // A markdown table's width is fixed by its separator row; parsers drop body
        // cells beyond it and pad rows that fall short. Source tables can be ragged,
        // so size every row to the widest one instead of dropping trailing columns.
        val columnCount = rows.maxOf { it.size }
        if (columnCount == 0) return element.text()
        fun padRow(row: List<String>): List<String> =
            if (row.size < columnCount) row + List(columnCount - row.size) { "" } else row
        val header = padRow(rows.first())
        val body = rows.drop(1).map(::padRow)
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
        val fold = element.attr("data-callout-fold").trim().takeIf { it == "-" || it == "+" }.orEmpty()
        val title = (element.selectFirst(".callout-title-inner") ?: element.selectFirst(".callout-title"))
            ?.text()
            ?.trim()
            ?.ifBlank { null }
        val content = element.selectFirst(".callout-content") ?: element
        val body = renderBlocks(content.childNodes(), listDepth)
        return blockquote(
            listOf(
                "[!$type]$fold${title?.let { " $it" }.orEmpty()}",
                body,
            ).filter { it.isNotBlank() }.joinToString("\n"),
        )
    }

    private fun collectFootnotes(root: Element) {
        footnoteHeading = root.select("section[data-footnotes]").firstOrNull()
            ?.directFootnoteHeadingMarkdown()

        root.select(
            "section[data-footnotes] li[id], ol.footnotes li[id], ol[class*=footnote] li[id], ol.references li[id]",
        ).forEachIndexed { index, item ->
            val rawId = item.id()
            val cleanedId = cleanFootnoteId(rawId)
            val id = cleanedId.takeIf { it.matches(footnoteNumberPattern) } ?: (index + 1).toString()
            footnoteTargets[rawId] = id
            footnoteTargets[cleanedId] = id
            if (id !in footnotes) {
                footnotes[id] = renderFootnoteDefinition(item)
            }
        }
    }

    private fun Element.directFootnoteHeadingMarkdown(): String? {
        val heading = children().firstOrNull { it.normalName().matches(headingTagPattern) } ?: return null
        val text = renderInline(heading).trim().ifBlank { return null }
        val level = heading.normalName().removePrefix("h").toIntOrNull() ?: return null
        return "${"#".repeat(level)} $text"
    }

    private fun renderFootnoteReference(element: Element): String? {
        val href = element.selectFirst("a[href^=#]")?.attr("href")
        if (href != null) {
            val target = href.removePrefix("#")
            if (target in footnoteTargets || element.isFootnoteReferenceLike(href)) {
                return "[^${footnoteTargets[target] ?: cleanFootnoteId(target)}]"
            }
        }

        if (element.normalName() == "sup") {
            val id = cleanFootnoteId(element.text())
            if (id in footnotes) return "[^$id]"
        }
        return null
    }

    private fun renderFootnoteDefinition(item: Element): String {
        val clone = item.clone()
        clone.cleanupFootnoteDefinition()
        val hasBlockContent = clone.children().any { it.normalName() in footnoteBlockTags }
        val rendered = if (hasBlockContent) {
            renderBlocks(clone.childNodes(), listDepth = 0).trim()
        } else {
            renderInline(clone).trim()
        }
        return rendered.stripTerminalInlineElementPeriod()
    }

    private fun Element.hasComplexSpan(): Boolean = hasComplexSpan("rowspan") || hasComplexSpan("colspan")

    private fun Element.hasComplexSpan(name: String): Boolean {
        if (!hasAttr(name)) return false
        return attr(name).trim().toIntOrNull() != 1
    }
}
