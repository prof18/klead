package com.prof18.klead.internal.markdown

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Stateless spacing/whitespace normalization helpers used while rendering inline markdown.
 *
 * These operate purely on the already-rendered [StringBuilder]/[String] output and the source
 * node lists; they hold no renderer state, so they live here rather than inside the renderer.
 */

internal fun normalizeLeadingInlineSpacing(
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

internal fun normalizeTrailingInlineSpacing(nodes: List<Node>, index: Int, node: Node, value: String): String {
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

internal fun appendSpecialInlineSpacing(
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
