package com.prof18.klead.internal.markdown

import com.prof18.klead.internal.dom.attrTrimmedOrNull
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.resolveUrl
import com.prof18.klead.internal.media.TrustedEmbeds
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object KleadMarkdownWriter {
    fun write(root: Element, baseUrl: String): String {
        val renderer = Renderer(baseUrl)
        return renderer.write(root)
    }
}

@Suppress("LargeClass")
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
        return postProcess(withFootnotes)
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

    private fun Node.isStandaloneImageBlock(): Boolean = this is Element && normalName() == "img"

    private fun Node.isCodeBlock(): Boolean = this is Element && (normalName() == "pre" || isBlockCodeElement())

    private fun Node.isListBlock(): Boolean = this is Element && normalName() in setOf("ol", "ul")

    private fun List<RenderedBlock>.joinToStringWithSeparators(): String {
        if (isEmpty()) return ""
        val builder = StringBuilder(first().markdown)
        for (index in 1 until size) {
            val previous = this[index - 1]
            val current = this[index]
            val separator = if (
                (previous.codeBlock && current.markdown.startsWith("![")) ||
                (previous.listBlock && current.codeBlock)
            ) {
                "\n"
            } else {
                "\n\n"
            }
            builder.append(separator)
            builder.append(current.markdown)
        }
        return builder.toString()
    }

    private fun Node.isInlineFlowNode(): Boolean = when (this) {
        is TextNode -> text().isNotBlank()

        is Element -> normalName() in inlineFlowTags &&
            !isBlockCodeElement() &&
            !isBlockLikeSpan() &&
            !(normalName() == "span" && attr("data-as") == "p") &&
            !(normalName() == "a" && selectFirst("img") != null) &&
            children().none { it.normalName() in blockFlowTags }

        else -> false
    }

    private fun Element.isBlockLikeSpan(): Boolean {
        if (normalName() != "span") return false
        val hints = listOf(
            attr("data-cy"),
            attr("data-testid"),
            id(),
        ).joinToString(" ").lowercase()
        return blockLikeSpanHint.containsMatchIn(hints)
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

            "a" -> renderLinkedImage(element)
                ?: renderLinkedHeadingBlock(element, listDepth)
                ?: renderInline(element).trimInlineBlock()

            "blockquote" -> blockquote(renderBlocks(element.childNodes(), listDepth))

            "ul" -> renderList(element, ordered = false, listDepth = listDepth)

            "ol" -> renderList(element, ordered = true, listDepth = listDepth)

            "li" -> renderInline(element).trimInlineBlock()

            "hr" -> "---"

            "img" -> renderImage(element)

            "picture" -> element.select("img").firstOrNull()?.let { renderImage(it) }.orEmpty()

            "figure" -> renderFigure(element, listDepth)

            "table" -> renderTable(element)

            "iframe" -> renderEmbeddedMedia(element)

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

    private fun normalizeLeadingInlineSpacing(
        rendered: StringBuilder,
        nodes: List<Node>,
        index: Int,
        node: Node,
        value: String,
        previousInlineWasLinkedImage: Boolean,
    ): String {
        var adjusted = value
        if (previousInlineWasLinkedImage && adjusted.needsLeadingSpaceAfterLinkedImage()) {
            rendered.append(' ')
        }
        if (adjusted.hasLeadingFootnoteWhitespaceAfterPunctuation(rendered.lastNonWhitespaceOrNull())) {
            adjusted = adjusted.trimStart()
        }
        if (adjusted.hasLeadingTightPunctuation() && rendered.lastOrNull()?.isWhitespace() == true) {
            if (!rendered.endsWithHtmlSubscriptIgnoringInlineWhitespace()) {
                rendered.trimTrailingInlineWhitespace()
            }
            adjusted = adjusted.trimStart()
        }
        if (node is Element && node.needsLeadingSpaceBeforeDelimitedInline(adjusted, rendered)) {
            rendered.append(' ')
        }
        if (node is Element && node.needsLeadingSpaceBeforeQuotedLink(adjusted, rendered)) {
            rendered.append(' ')
        }
        if (shouldAppendFootnoteSpace(rendered, nodes, index, node, adjusted)) {
            rendered.append(' ')
        }
        return adjusted
    }

    private fun normalizeTrailingInlineSpacing(nodes: List<Node>, index: Int, node: Node, value: String): String {
        if (node !is Element) return value

        var adjusted = value
        val nextText = nodes.nextInlineTextAfter(index)
        if (node.isDelimitedInlineElement() && adjusted.needsTrailingSpaceAfterDelimitedInline(nextText)) {
            adjusted += " "
        }
        if (node.isMarkdownLinkElement(adjusted) && nextText.needsTrailingSpaceBeforeClosingQuoteAfterLink()) {
            adjusted += " "
        }
        return adjusted
    }

    private fun appendSpecialInlineSpacing(
        rendered: StringBuilder,
        nodes: List<Node>,
        index: Int,
        node: Node,
        value: String,
    ) {
        if (value.startsWith(PERSIAN_COMMA) && rendered.lastOrNull()?.isWhitespace() == false) {
            rendered.append(' ')
        }
        if (node is Element && node.needsLeadingSpaceBeforeNumericVariableSubscript(value, nodes, index, rendered)) {
            rendered.append(' ')
        }
        if (node is Element && node.isReadableSubscript(value) && rendered.lastOrNull()?.isDigit() == true) {
            rendered.append(' ')
        }
    }

    private fun Element.needsLeadingSpaceBeforeDelimitedInline(value: String, rendered: StringBuilder): Boolean =
        isDelimitedInlineElement() && value.needsLeadingSpaceBeforeDelimitedInline(rendered.lastNonWhitespaceOrNull())

    private fun Element.needsLeadingSpaceBeforeQuotedLink(value: String, rendered: StringBuilder): Boolean =
        isMarkdownLinkElement(value) && rendered.lastNonWhitespaceOrNull() in linkOpeningQuoteSpacingChars

    private fun Element.needsLeadingSpaceBeforeNumericVariableSubscript(
        value: String,
        nodes: List<Node>,
        index: Int,
        rendered: StringBuilder,
    ): Boolean = isNumericVariableSubscript(value) &&
        nodes.shouldSeparateNumericVariableSubscript(index, rendered.lastOrNull())

    private fun shouldAppendFootnoteSpace(
        rendered: StringBuilder,
        nodes: List<Node>,
        index: Int,
        node: Node,
        value: String,
    ): Boolean {
        if (!value.startsWith("[^")) return false
        if (rendered.endsWithSentenceClosingQuote()) return true
        if (node !is Element) return nodes.shouldSeparateFootnote(index, rendered.lastOrNull())
        return node.needsLeadingSpaceBeforeFootnote(
            previous = rendered.lastNonWhitespaceOrNull(),
            previousElement = nodes.previousInlineElementBefore(index),
        ) || nodes.shouldSeparateFootnote(index, rendered.lastOrNull())
    }

    private fun String.hasLeadingTightPunctuation(): Boolean {
        val trimmed = trimStart()
        return !trimmed.startsWith("![") && trimmed.firstOrNull() in tightPunctuation
    }

    private fun StringBuilder.trimTrailingInlineWhitespace() {
        while (isNotEmpty() && last().isWhitespace() && last() != '\n') {
            deleteAt(lastIndex)
        }
    }

    private fun StringBuilder.endsWithHtmlSubscriptIgnoringInlineWhitespace(): Boolean {
        var index = lastIndex
        while (index >= 0 && get(index).isWhitespace() && get(index) != '\n') {
            index--
        }
        val suffix = "</sub>"
        if (index + 1 < suffix.length) return false
        return substring(index - suffix.length + 1, index + 1) == suffix
    }

    private fun Element.isDelimitedInlineElement(): Boolean = normalName() in delimitedInlineTags

    private fun Element.isMarkdownLinkElement(value: String): Boolean =
        normalName() == "a" && value.startsWith("[") && !value.startsWith("[^")

    private fun String.needsLeadingSpaceBeforeDelimitedInline(previous: Char?): Boolean =
        firstOrNull()?.let { it == '*' || it == '~' } == true &&
            previous != null &&
            !previous.isWhitespace() &&
            (previous.isLetterOrDigit() || previous in delimitedInlineLeadingSpacingChars)

    private fun String.needsTrailingSpaceAfterDelimitedInline(nextText: String?): Boolean =
        lastOrNull()?.let { it == '*' || it == '~' } == true &&
            nextText?.trimStart()?.firstOrNull() in delimitedInlineTrailingSpacingChars

    private fun String?.needsTrailingSpaceBeforeClosingQuoteAfterLink(): Boolean {
        val text = this?.trimStart() ?: return false
        val quote = text.firstOrNull() ?: return false
        if (quote !in linkClosingQuoteSpacingChars) return false
        val afterQuote = text.drop(1).trimStart().firstOrNull()
        return afterQuote == null || !afterQuote.isLetterOrDigit()
    }

    private fun List<Node>.nextInlineTextAfter(index: Int): String? {
        for (nextIndex in index + 1 until size) {
            val text = when (val node = get(nextIndex)) {
                is TextNode -> node.wholeText
                is Element -> node.text()
                else -> ""
            }
            if (text.isBlank()) continue
            return text
        }
        return null
    }

    private fun List<Node>.previousInlineElementBefore(index: Int): Element? {
        for (previousIndex in index - 1 downTo 0) {
            val node = get(previousIndex)
            if (node is TextNode && node.wholeText.isBlank()) continue
            return node as? Element
        }
        return null
    }

    private fun String.needsLeadingSpaceAfterLinkedImage(): Boolean =
        firstOrNull()?.let { it.isLetterOrDigit() || it in linkedImageSpacingLeadingChars } == true

    private fun String.hasLeadingFootnoteWhitespaceAfterPunctuation(previous: Char?): Boolean =
        firstOrNull()?.isWhitespace() == true &&
            trimStart().startsWith("[^") &&
            previous in footnoteAttachingPunctuation

    private fun StringBuilder.lastNonWhitespaceOrNull(): Char? = asSequence().lastOrNull { !it.isWhitespace() }

    private fun StringBuilder.endsWithSentenceClosingQuote(): Boolean {
        val last = indexOfLast { !it.isWhitespace() }
        if (last <= 0 || get(last) !in footnoteSeparatingClosingQuotes) return false
        val previous = substring(0, last).indexOfLast { !it.isWhitespace() }
        return previous >= 0 && get(previous) in footnoteSeparatingSentencePunctuation
    }

    private fun String.trimInlineBlock(preserveLeadingHardBreaks: Boolean = true): String {
        val leadingHardBreaks = if (preserveLeadingHardBreaks) {
            leadingHardBreakRun.find(this)?.value.orEmpty()
        } else {
            ""
        }
        var value = leadingHardBreaks + removePrefix(leadingHardBreaks).trimStart()
        if (value.endsWith('\n')) {
            value = value.dropLast(1)
        }
        return if (value.endsWith("  ")) value else value.trimEnd()
    }

    private fun renderInlineElement(element: Element, inLink: Boolean): String = when (element.normalName()) {
        "strong", "b" -> renderDelimitedInline(element, inLink, "**")

        "em", "i" -> renderDelimitedInline(element, inLink, "*")

        "code" -> codeSpan(renderCodeSpanContent(element).ifBlank { element.text() })

        "a" -> renderLink(element, inLink)

        "img" -> renderImage(element)

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

    private fun List<Node>.shouldSeparateFootnote(index: Int, previous: Char?): Boolean {
        if (previous == null || previous.isWhitespace()) return false
        var sawWhitespace = false
        for (nextIndex in index + 1 until size) {
            val text = when (val node = get(nextIndex)) {
                is TextNode -> node.wholeText
                is Element -> node.text()
                else -> ""
            }
            if (text.isBlank()) {
                if (text.any { it.isWhitespace() }) {
                    sawWhitespace = true
                }
                continue
            }
            val trimmed = text.trimStart()
            val first = trimmed.firstOrNull() ?: return false
            val hasLeadingWhitespace = sawWhitespace || text.firstOrNull()?.isWhitespace() == true
            val separatesWordFootnote = previous.isLetterOrDigit() &&
                hasLeadingWhitespace &&
                first.isLetterOrDigit()
            val separatesQuotedSentenceFootnote = previous in footnoteSeparatingClosingQuotes &&
                hasLeadingWhitespace &&
                first.isLetterOrDigit()
            val separatesWordBeforePunctuationFootnote = previous.isLetterOrDigit() &&
                first in footnoteSeparatingPunctuation
            val separatesCodeFootnote = previous == '`' && first in footnoteSeparatingPunctuation
            return separatesWordFootnote ||
                separatesQuotedSentenceFootnote ||
                separatesWordBeforePunctuationFootnote ||
                separatesCodeFootnote
        }
        return false
    }

    private fun Element.needsLeadingSpaceBeforeFootnote(previous: Char?, previousElement: Element?): Boolean {
        if (previous == null || previous.isWhitespace()) return false
        if (previous in footnoteAttachingPunctuation) return false
        if (previous == '*') return previousElement?.normalName() in setOf("em", "i")
        return previous in footnoteSeparatingInlineSuffixes ||
            previous == ')' ||
            previous == ']' ||
            (previous.isLetterOrDigit() && isInTableCell())
    }

    private fun Element.isInTableCell(): Boolean = parents().any { it.normalName() == "td" || it.normalName() == "th" }

    private fun Element.isNumericVariableSubscript(rendered: String): Boolean = normalName() == "sub" &&
        text().trim().matches(numericSubscriptPattern) &&
        rendered.startsWith("<sub>")

    private fun Element.isReadableSubscript(rendered: String): Boolean = normalName() == "sub" &&
        text().trim().any { it.isLetter() } &&
        rendered.startsWith("<sub>")

    private fun List<Node>.shouldSeparateNumericVariableSubscript(index: Int, previous: Char?): Boolean {
        if (previous?.isLetter() != true) return false
        for (nextIndex in index + 1 until size) {
            val text = when (val node = get(nextIndex)) {
                is TextNode -> node.wholeText
                is Element -> node.text()
                else -> ""
            }
            if (text.isBlank()) continue
            return nextNumericSubscriptCharPattern.matches(text.trimStart().first().toString())
        }
        return false
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
            renderLinkedImage(element)?.let { return it }
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

    private fun linkDestination(url: String, title: String, visibleText: String): String {
        val destination = escapeDestination(url)
        val cleanTitle = title.trim().takeIf { it.isNotBlank() } ?: return destination
        if (cleanTitle.normalizedLinkTitleText().equals(visibleText.normalizedLinkTitleText(), ignoreCase = true)) {
            return destination
        }
        return "$destination \"${escapeTitle(cleanTitle)}\""
    }

    private fun renderLinkedImage(element: Element): String? {
        val image = element.linkedImageOnlyChild() ?: return null

        val url = element.safeResolvedHref() ?: return null
        val imageMarkdown = renderImage(image)
        return if (imageMarkdown.isBlank()) {
            null
        } else {
            "[$imageMarkdown](${escapeDestination(url.withRootSlashForBareOrigin())})"
        }
    }

    private fun renderLinkedHeadingBlock(element: Element, listDepth: Int): String? {
        if (element.children().none { it.normalName() in blockFlowTags }) return null
        val url = element.safeResolvedHref() ?: return null
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

    private fun Element.linkedImageOnlyChild(): Element? {
        if (normalName() != "a") return null
        if (hasNonImageText()) return null
        return children().singleOrNull { it.normalName() == "img" }
    }

    private fun Element.hasNonImageText(): Boolean = childNodes().any { node ->
        when (node) {
            is TextNode -> node.text().isNotBlank()
            is Element -> node.normalName() != "img" && node.text().isNotBlank()
            else -> false
        }
    }

    private fun Element.safeResolvedHref(): String? {
        val href = attr("href").trim()
        if (href.isBlank() || isDangerousUrl(href)) return null
        return resolveUrl(baseUrl, href).takeIf { it.isNotBlank() }
    }

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

    private fun renderImage(element: Element): String {
        val sources = listOfNotNull(
            largestSrcsetUrl(element.attr("srcset")),
            element.attr("src").trim().takeIf { it.isNotBlank() },
        ).distinct()
        for (src in sources) {
            if (isDangerousUrl(src) || element.isPlaceholderImage(src)) continue
            val url = resolveUrl(baseUrl, src)
            if (url.isNotBlank()) {
                return "![${escapeInline(element.attr("alt"))}](${escapeDestination(url)})"
            }
        }
        return ""
    }

    private fun renderEmbeddedMedia(element: Element): String {
        val href = element.attr("data-klead-video-url").trim().ifBlank {
            TrustedEmbeds.markdownMediaFromUrl(element.attr("src").trim())?.watchUrl.orEmpty()
        }
        if (href.isNotBlank()) {
            return renderMarkdownMedia(href, preserveLeadingSpacer = element.hasKleadLeadingSpacer())
        }

        return renderTrustedRawIframe(element)
    }

    private fun renderMarkdownMedia(href: String, preserveLeadingSpacer: Boolean): String {
        if (isDangerousUrl(href)) return ""
        val url = resolveUrl(baseUrl, href)
        if (url.isBlank()) return ""
        val media = "![](${escapeDestination(url)})"
        return if (preserveLeadingSpacer) "\n$media" else media
    }

    private fun Element.hasKleadLeadingSpacer(): Boolean = attr("data-klead-leading-spacer") == "true"

    private fun renderTrustedRawIframe(element: Element): String {
        val src = element.attr("src").trim()
        if (src.isBlank() || isDangerousUrl(src)) return ""
        val url = resolveUrl(baseUrl, src)
        if (url.isBlank() || !TrustedEmbeds.isTrustedRawIframeSrc(url)) return ""

        val attributes = buildList {
            add("src" to url)
            rawIframeAttributes.forEach { name ->
                if (element.hasAttr(name)) {
                    add(name to element.attr(name))
                }
            }
        }
        val renderedAttributes = attributes.joinToString(" ") { (name, value) ->
            "$name=\"${escapeHtmlAttribute(value)}\""
        }
        return "<iframe $renderedAttributes></iframe>"
    }

    private fun renderSvg(element: Element): String {
        if (!element.hasRenderableSvgContent()) return ""
        val clone = element.clone()
        clone.applySvgStyleFallbacks()
        clone.select("[class]").removeAttr("class")
        return clone.outerHtml()
            .trim()
            .replace(tagGapWhitespacePattern, "><")
            .replace(svgTextLabelGroupSpacingPattern, "</text> $1")
            .replace(svgTextLabelPathSpacingPattern, "</text> $1")
            .replace(svgSelfClosingTagPattern) { match ->
                "<${match.groupValues[1]}${match.groupValues[2]}></${match.groupValues[1]}>"
            }
    }

    private fun Element.applySvgStyleFallbacks() {
        select("*").forEach { element ->
            element.applySvgAttributeValueFallbacks()
            element.applySvgClassFallbacks()
        }
        select("line.gridline").forEach { line ->
            line.prependMissingSvgAttributes(
                "stroke-opacity" to "0.2",
                "stroke" to "currentColor",
            )
        }
        select("path.path-area").forEach { path ->
            path.prependMissingSvgAttributes("fill" to "none")
        }
        select("path.path-line").forEach { path ->
            path.prependMissingSvgAttributes(
                "stroke" to "currentColor",
                "fill" to "none",
            )
        }
    }

    private fun Element.applySvgAttributeValueFallbacks() {
        for (attributeName in svgColorAttributes) {
            val fallback = svgAttributeValueFallbacks[attr(attributeName)] ?: continue
            attr(attributeName, fallback)
        }
    }

    private fun Element.applySvgClassFallbacks() {
        val classes = classNames()
        svgClassAttributeFallbacks.forEach { (className, fallback) ->
            if (className in classes) {
                prependMissingSvgAttributes(fallback)
            }
        }

        val styleFallbacks = buildList {
            if ("text-[14px]" in classes) add("font-size:14px")
            if ("font-semibold" in classes) add("font-weight:600")
        }
        if (styleFallbacks.isNotEmpty()) {
            prependMissingSvgAttributes("style" to styleFallbacks.joinToString(";"))
        }
    }

    private fun Element.prependMissingSvgAttributes(vararg defaults: Pair<String, String>) {
        val missing = defaults.filterNot { (name, _) -> hasAttr(name) }
        if (missing.isEmpty()) return

        val existing = attributes().asList().map { attribute -> attribute.key to attribute.value }
        clearAttributes()
        missing.forEach { (name, value) -> attr(name, value) }
        existing.forEach { (name, value) -> attr(name, value) }
    }

    private fun Element.hasRenderableSvgContent(): Boolean =
        !isSmallIconSvg() && (select(svgRenderableElementSelector).isNotEmpty() || text().isNotBlank())

    private fun Element.isSmallIconSvg(): Boolean {
        if (text().isNotBlank()) return false
        val viewBox = attr("viewBox")
            .trim()
            .split(svgNumberDelimiter)
            .mapNotNull { it.toDoubleOrNull() }
        if (viewBox.size == 4 && viewBox[2] <= SVG_ICON_MAX_SIZE && viewBox[3] <= SVG_ICON_MAX_SIZE) return true

        val width = attr("width").svgLengthValue()
        val height = attr("height").svgLengthValue()
        return width != null && height != null && width <= SVG_ICON_MAX_SIZE && height <= SVG_ICON_MAX_SIZE
    }

    private fun String.svgLengthValue(): Double? {
        val trimmed = trim().lowercase()
        val numeric = trimmed.removeSuffix("px").removeSuffix("em").toDoubleOrNull() ?: return null
        return if (trimmed.endsWith("em")) numeric * CSS_EM_SIZE else numeric
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

    private fun List<Node>.hasSignParagraphListItemShape(): Boolean {
        val meaningfulNodes = filterNot { it is TextNode && it.text().isBlank() }
        val blockCount = meaningfulNodes.count { it is Element && it.isFlattenableSignListBlock() }
        if (blockCount != 1) return false

        val firstNode = meaningfulNodes.firstOrNull() ?: return false
        return firstNode.signListMarkerText() in setOf("+", "-") &&
            meaningfulNodes.all { node ->
                node is TextNode ||
                    (node is Element && (node.isFlattenableSignListBlock() || node.normalName() in inlineFlowTags))
            }
    }

    private fun Node.signListMarkerText(): String? = when (this) {
        is TextNode -> text().trim()
        is Element -> if (isFlattenableSignListBlock()) null else text().trim()
        else -> null
    }

    private fun Element.isFlattenableSignListBlock(): Boolean = normalName() in setOf("p", "div")

    private fun listItemIndent(listDepth: Int, itemIndex: Int): String {
        val effectiveDepth = if (itemIndex == 0) listDepth else listDepth * 2
        return "\t".repeat(effectiveDepth)
    }

    private fun renderListItemLine(indent: String, marker: String, content: String): String {
        val lines = content.lines()
        if (lines.size == 1) return "$indent$marker ${lines.first()}"

        val continuationIndent = "$indent\t"
        return buildString {
            append(indent)
            append(marker)
            append(' ')
            append(lines.first().trimEnd())
            append("  ")
            lines.drop(1).forEach { line ->
                append('\n')
                append(continuationIndent)
                append(line.trim())
            }
        }
    }

    private fun blockquote(markdown: String): String {
        val lines = markdown.lines()
        return lines.mapIndexed { index, line ->
            if (line.isBlank()) {
                when {
                    line.endsWith("  ") -> "> $line"
                    lines.isHorizontalRuleSeparator(index) -> "> "
                    else -> ">"
                }
            } else {
                "> $line"
            }
        }.joinToString("\n")
    }

    private fun List<String>.isHorizontalRuleSeparator(index: Int): Boolean =
        previousNonBlankLine(index)?.trim() == "---" || nextNonBlankLine(index)?.trim() == "---"

    private fun List<String>.previousNonBlankLine(index: Int): String? =
        asSequence().take(index).lastOrNull { it.isNotBlank() }

    private fun List<String>.nextNonBlankLine(index: Int): String? =
        asSequence().drop(index + 1).firstOrNull { it.isNotBlank() }

    private fun renderCodeBlock(element: Element): String {
        val code = if (element.normalName() == "code") element else element.selectFirst("code")
        val language = code?.attr("data-lang")?.ifBlank { null }
            ?: languageFrom(code)
            ?: languageFrom(element)
            ?: ""
        val rawText = (code ?: element)
            .codeBlockText(trimSurroundingWhitespace = element.isBlockCodeElement())
            .normalizeFinalNewline()
            .replace("\t", "    ")
        val fence = codeFence(rawText)
        val text = rawText.replace("`", "\\`")
        return "$fence$language\n$text$fence"
    }

    private fun Element.isBlockCodeElement(): Boolean = normalName() == "code" && classNames().contains("block")

    private fun Node.codeBlockText(trimSurroundingWhitespace: Boolean): String {
        val text = when (this) {
            is TextNode -> wholeText
            is Element -> childNodes().joinToString("") { it.codeBlockText(trimSurroundingWhitespace = false) }
            else -> ""
        }
        return if (trimSurroundingWhitespace) text.trim() else text
    }

    private fun renderFigure(element: Element, listDepth: Int): String {
        if (element.hasArticleBodyWrapper()) {
            return renderBlocks(element.childNodes(), listDepth)
        }
        val imageMarkdown = element.select("img")
            .distinctBy(::imageDedupKey)
            .joinToString("\n") { renderImage(it) }
            .trim()
        val caption = element.select("figcaption").firstOrNull()?.let { renderCaptionInline(it).trim() }
        return listOfNotNull(
            imageMarkdown.takeIf { it.isNotBlank() },
            caption?.takeIf { it.isNotBlank() }?.let { "*$it*" },
        ).joinToString("\n\n").ifBlank { renderBlocks(element.childNodes(), listDepth) }
    }

    private fun renderCaptionInline(element: Element): String = renderCaptionNodes(element.childNodes())

    private fun renderCaptionNodes(nodes: List<Node>): String = nodes.joinToString("") { node ->
        when (node) {
            is TextNode -> escapeInline(node.text())
            is Element -> renderCaptionElement(node)
            else -> ""
        }
    }.replace(horizontalWhitespacePattern, " ")

    private fun renderCaptionElement(element: Element): String = when (element.normalName()) {
        "br" -> "\n"
        "img" -> escapeInline(element.attr("alt").ifBlank { element.attr("title") })
        "math" -> escapeInline(element.text())
        else -> renderCaptionNodes(element.childNodes())
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
            footnotes.putIfAbsent(id, renderFootnoteDefinition(item))
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

    private fun String.stripTerminalInlineElementPeriod(): String {
        if (!endsWith(".")) return this
        val withoutPeriod = dropLast(1)
        return if (footnoteTerminalInlineElementPattern.containsMatchIn(withoutPeriod)) withoutPeriod else this
    }

    private fun Element.cleanupFootnoteDefinition() {
        select(
            "a[href*=fnref], a[href*=ftnt_ref], a[href*=_ftnref], a[class*=backref], a[class*=to-top], " +
                "a[href*=FnAnchor], a[href*=-link], a[aria-label*=Back], a[aria-label*=back], " +
                "a[aria-label*=Jump], a[aria-label*=jump], " +
                ".footnote-backref, .data-footnote-backref, .easy-footnote-margin-adjust",
        ).remove()

        val first = children().firstOrNull()
        if (
            first != null &&
            first.normalName() in footnoteMarkerTags &&
            cleanFootnoteId(first.text()) == cleanFootnoteId(id())
        ) {
            first.remove()
        }
    }

    private fun Element.isFootnoteReferenceLike(href: String): Boolean {
        val target = href.removePrefix("#")
        if (!footnoteIdHint.containsMatchIn(target)) return false
        val text = text().trim()
        return normalName() == "sup" ||
            parent()?.normalName() == "sup" ||
            className().contains("footnote", ignoreCase = true) ||
            text.matches(footnoteReferenceTextPattern)
    }

    private fun cleanFootnoteId(raw: String): String {
        val value = raw.trim().trim('#', '-', ':', '_', '.')
        footnoteIdNumberPattern.find(value)
            ?.takeIf { match -> match.isStandaloneFootnoteNumberIn(value) }
            ?.let { return it.groupValues[1] }
        return value.removePrefix("fnref")
            .removePrefix("fn")
            .trim('-', ':', '_', '.')
            .ifBlank { raw }
    }

    private fun MatchResult.isStandaloneFootnoteNumberIn(value: String): Boolean {
        val nextIndex = range.last + 1
        return nextIndex >= value.length || !value[nextIndex].isLetterOrDigit()
    }

    private fun renderMath(element: Element): String? {
        val latex = element.attrTrimmedOrNull("data-latex") ?: return null
        val display = element.hasClass("display") || element.attr("display") == "block"
        return if (display) "$$\n$latex\n$$" else "$$latex$"
    }

    private fun largestSrcsetUrl(srcset: String): String? = srcset.split(srcsetDelimiter)
        .mapNotNull { candidate ->
            val parts = candidate.trim().split(srcsetWhitespacePattern)
            val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: 0
            url to width
        }
        .maxByOrNull { it.second }
        ?.first

    private fun Element.isPlaceholderImage(src: String): Boolean {
        val normalized = src.trimStart().lowercase()
        if (
            normalized.startsWith("data:image/png") &&
            attr("data-lqip").equals("true", ignoreCase = true) &&
            attr("alt").isNotBlank()
        ) {
            return false
        }
        return normalized.startsWith("data:image/")
    }

    private fun imageDedupKey(element: Element): String {
        val alt = element.attr("alt").trim()
        val source = largestSrcsetUrl(element.attr("srcset")) ?: element.attr("src")
        val family = source.normalizedImageFamily()
        return if (alt.isBlank()) "src:$family" else "alt:$alt|src:$family"
    }

    private fun String.normalizedImageFamily(): String = substringBefore('?')
        .substringBefore('#')
        .replace(imageDimensionSuffixPattern, "")
        .replace(imageFileExtensionPattern, "")

    private fun codeSpan(text: String): String {
        val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        val ticks = "`".repeat(maxTicks + 1)
        return if ("`" in text) "$ticks $text $ticks" else "$ticks$text$ticks"
    }

    private fun renderCodeSpanContent(element: Element): String = element.childNodes().joinToString("") { node ->
        when (node) {
            is TextNode -> node.wholeText
            is Element -> renderCodeSpanElement(node)
            else -> ""
        }
    }

    private fun renderCodeSpanElement(element: Element): String {
        val content = renderCodeSpanContent(element)
        return when (element.normalName()) {
            "strong", "b" -> "**$content**"
            "em", "i" -> "*$content*"
            "del", "s" -> "~~$content~~"
            "br" -> " "
            else -> content
        }
    }

    private fun codeFence(text: String): String {
        val maxTicks = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat((maxTicks + 1).coerceAtLeast(3))
    }

    private fun languageFrom(element: Element?): String? {
        if (element == null) return null
        val languageClass = element.classNames().firstOrNull { it.startsWith("language-") }
        if (languageClass != null) return languageClass.removePrefix("language-")
        return if (element.hasClass("hl")) {
            element.classNames().firstOrNull { className ->
                className !in highlightCodeClassNoise && codeLanguageClass.matches(className)
            }
        } else {
            null
        }
    }

    private fun escapeInline(text: String): String = text
        .replace("\uFEFF", "")
        .normalizePlaceholderDots()
        .normalizeSpacedEllipses()
        .replace('\u00A0', ' ')
        .replace('\u202F', ' ')
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")

    private fun String.normalizePlaceholderDots(): String = placeholderDotsPattern.replace(this) { match ->
        "${match.groupValues[1]}.."
    }

    private fun String.normalizeSpacedEllipses(): String = spacedEllipsisPattern.replace(this, "...")

    private fun escapeDestination(url: String): String {
        val escaped = url.replace("(", "\\(").replace(")", "\\)")
        return if (escaped.any { it.isWhitespace() }) "<$escaped>" else escaped
    }

    private fun escapeTitle(title: String): String = title
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun String.normalizedLinkTitleText(): String = replace(linkTitleWhitespacePattern, " ").trim()

    private fun String.withRootSlashForBareOrigin(): String = if (bareOriginUrl.matches(this)) "$this/" else this

    private fun escapeHtmlAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")

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
        val normalized = markdown.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(emptyLinkPattern, "")
        if (normalized.isBlank()) return ""
        val result = mutableListOf<String>()
        var blankCount = 0
        var inFence = false
        val lines = normalized.lines()
        for ((index, line) in lines.withIndex()) {
            if (line.startsWith("```")) inFence = !inFence
            if (!inFence && line == PRESERVED_BLANK_SPACER) {
                result += ""
                result += ""
                blankCount = 2
                continue
            }
            val processed = when {
                inFence -> line
                line.endsWith("  ") -> line
                line == "> " -> line
                line.trim() == "--" -> "\\--"
                else -> line.trimEnd()
            }
            if (!inFence && processed.isBlank()) {
                blankCount++
                val maxBlankLines = if (lines.nextNonBlankLineAfter(index)?.isMarkdownMediaLine() == true) {
                    MAX_CONSECUTIVE_BLANK_LINES_BEFORE_MEDIA
                } else {
                    MAX_CONSECUTIVE_BLANK_LINES
                }
                if (blankCount <= maxBlankLines) {
                    result += if (processed.endsWith("  ")) processed else ""
                }
            } else {
                blankCount = 0
                result += processed
            }
        }
        return result
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trimEnd() + "\n"
    }

    private fun List<String>.nextNonBlankLineAfter(index: Int): String? =
        drop(index + 1).firstOrNull { it.trimEnd().isNotBlank() }?.trimStart()

    private fun String.isMarkdownMediaLine(): Boolean = startsWith("![](")

    private companion object {
        const val SVG_ICON_MAX_SIZE = 32.0
        const val CSS_EM_SIZE = 16.0
        const val MAX_CONSECUTIVE_BLANK_LINES = 1
        const val MAX_CONSECUTIVE_BLANK_LINES_BEFORE_MEDIA = 2
        const val PRESERVED_BLANK_SPACER = "<!-- klead-preserve-blank-spacer -->"
    }

    private data class InlineWhitespace(
        val original: String,
        val leading: String = "",
        val body: String = "",
        val trailing: String = "",
    )

    private data class RenderedBlock(
        val markdown: String,
        val standaloneImage: Boolean,
        val codeBlock: Boolean,
        val listBlock: Boolean,
    )
}
