package com.prof18.klead.internal.markdown

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

// Block assembly and final document cleanup: classifying nodes into block vs inline flow,
// joining rendered blocks with the right separators, and the whole-document post-processing
// pass that collapses blank runs. Pure functions over rendered strings and node shapes.

internal const val PRESERVED_BLANK_SPACER = "<!-- klead-preserve-blank-spacer -->"

internal data class RenderedBlock(
    val markdown: String,
    val standaloneImage: Boolean,
    val codeBlock: Boolean,
    val listBlock: Boolean,
)

internal fun List<RenderedBlock>.joinToStringWithSeparators(): String {
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

internal fun Node.isStandaloneImageBlock(): Boolean = this is Element && normalName() == "img"

internal fun Node.isCodeBlock(): Boolean = this is Element && (normalName() == "pre" || isBlockCodeElement())

internal fun Node.isListBlock(): Boolean = this is Element && normalName() in setOf("ol", "ul")

internal fun Node.isInlineFlowNode(): Boolean = when (this) {
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

internal fun String.trimInlineBlock(preserveLeadingHardBreaks: Boolean = true): String {
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

internal data class InlineWhitespace(
    val original: String,
    val leading: String = "",
    val body: String = "",
    val trailing: String = "",
)

internal fun String.splitInlineWhitespace(): InlineWhitespace {
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

internal fun postProcessMarkdown(markdown: String): String {
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

private const val MAX_CONSECUTIVE_BLANK_LINES = 1
private const val MAX_CONSECUTIVE_BLANK_LINES_BEFORE_MEDIA = 2
