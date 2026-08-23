package com.prof18.klead.internal.markdown

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Stateless block-level markdown formatting helpers (blockquotes, list items, code blocks and
 * figure captions). They depend only on their inputs, so they live outside the stateful renderer.
 */

internal fun blockquote(markdown: String): String {
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

internal fun listItemIndent(listDepth: Int, itemIndex: Int): String {
    val effectiveDepth = if (itemIndex == 0) listDepth else listDepth * 2
    return "\t".repeat(effectiveDepth)
}

internal fun renderListItemLine(indent: String, marker: String, content: String): String {
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

internal fun List<Node>.hasSignParagraphListItemShape(): Boolean {
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

internal fun Element.isFlattenableSignListBlock(): Boolean = normalName() in setOf("p", "div")

internal fun renderCodeBlock(element: Element): String {
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

internal fun Element.isBlockCodeElement(): Boolean = normalName() == "code" && classNames().contains("block")

private fun Node.codeBlockText(trimSurroundingWhitespace: Boolean): String {
    val text = when (this) {
        is TextNode -> getWholeText()
        is Element -> childNodes().joinToString("") { it.codeBlockText(trimSurroundingWhitespace = false) }
        else -> ""
    }
    return if (trimSurroundingWhitespace) text.trim() else text
}

private fun String.normalizeFinalNewline(): String = replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n') + "\n"

internal fun renderCaptionInline(element: Element): String = renderCaptionNodes(element.childNodes())

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
