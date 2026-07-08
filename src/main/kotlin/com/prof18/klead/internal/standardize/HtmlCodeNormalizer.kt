package com.prof18.klead.internal.standardize

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object HtmlCodeNormalizer {
    fun normalizeCodeBlocks(content: Element) {
        normalizeWritersideCodeBlocks(content)
        normalizeVersoLeanExamples(content)
        normalizeStandalonePreformattedCode(content)
        normalizeCodeTables(content)
        content.select("pre").forEach { pre ->
            normalizeCodeMirrorBlock(pre)
            pre.select(CODE_UI_SELECTOR).remove()
            val code = pre.selectFirst("code") ?: Element("code").also { code ->
                code.text(normalizeCodeText(pre.textWithLineBreaks()))
                pre.empty()
                pre.appendChild(code)
            }
            code.select(CODE_UI_SELECTOR).remove()
            code.text(normalizeCodeText(code.textWithLineBreaks()))
            val language = languageFrom(code) ?: languageFrom(pre) ?: languageFromCodeAncestor(pre)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
            removeCodeBlockChromeAround(pre, language)
        }
        content.select("code > pre").forEach { pre ->
            pre.parent()?.replaceWith(pre)
        }
    }

    private fun normalizeVersoLeanExamples(content: Element) {
        content.select(".example").forEach { example ->
            val children = example.children().toList()
            var index = 0
            while (index < children.size) {
                val fragment = children[index]
                if (!fragment.isVersoLeanFragment()) {
                    index += 1
                    continue
                }

                val run = children.drop(index).takeWhile { it.isVersoLeanFragment() }
                run.replaceWithMergedVersoLeanBlock()
                index += run.size
            }
        }
    }

    private fun List<Element>.replaceWithMergedVersoLeanBlock() {
        if (size <= 1) return
        val codeText = mergeVersoLeanText()
        if (codeText.isBlank()) return

        val pre = Element("pre")
        val code = Element("code")
        code.attr("data-lang", "lean")
        code.addClass("language-lean")
        code.text(codeText)
        pre.appendChild(code)
        first().replaceWith(pre)
        drop(1).forEach { it.remove() }
    }

    private fun Element.isVersoLeanFragment(): Boolean =
        (normalName() == "code" && hasClass("lean") && hasClass("block")) ||
            (normalName() == "pre" && hasClass("lean") && hasClass("lean-output"))

    private fun List<Element>.mergeVersoLeanText(): String {
        val builder = StringBuilder()
        for (fragment in this) {
            val text = fragment.versoLeanText()
            if (text.isEmpty()) continue
            if (builder.isNotEmpty() && !builder.endsWith('\n')) {
                builder.append('\n')
            }
            builder.append(text)
        }
        return builder.toString()
            .replace(MULTI_NEWLINE_PATTERN, "\n\n")
            .trimEnd()
    }

    private fun Element.versoLeanText(): String {
        val source = if (normalName() == "code") {
            clone().also { clone ->
                clone.select(".hover-container, .hover-info").remove()
            }
        } else {
            this
        }
        return source.textWithLineBreaks().normalizeVersoLeanText()
    }

    private fun String.normalizeVersoLeanText(): String {
        val normalized = replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(MULTI_NEWLINE_PATTERN, "\n\n")
        if (normalized.isBlank()) {
            return if (normalized.contains('\n')) "\n" else ""
        }
        return normalized
            .replace(LEADING_NEWLINE_PATTERN, "")
            .trimEnd(' ', '\t')
    }

    private fun normalizeWritersideCodeBlocks(content: Element) {
        content.select("div.code-block[data-lang], div.code-block[data-language]").forEach { block ->
            if (block.select("pre, code").isNotEmpty()) return@forEach
            val codeText = normalizeCodeText(block.textWithLineBreaks())
            if (codeText.isBlank()) return@forEach

            val language = languageFrom(block)
            val pre = Element("pre")
            val code = Element("code")
            code.text(codeText)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
            pre.appendChild(code)
            block.replaceWith(pre)
        }
    }

    private fun removeCodeBlockChromeAround(pre: Element, language: String?) {
        var current: Element? = pre
        repeat(CODE_CHROME_ANCESTOR_DEPTH) {
            val element = current ?: return
            element.previousElementSibling()
                ?.takeIf { it.isCodeChromeSibling(language) }
                ?.remove()
            current = element.parent()
        }
    }

    private fun normalizeStandalonePreformattedCode(content: Element) {
        content.select("code[style*=white-space]").forEach { code ->
            if (code.parents().any { it.normalName() == "pre" }) return@forEach
            if (!code.attr("style").contains("pre", ignoreCase = true)) return@forEach
            val pre = Element("pre")
            code.replaceWith(pre)
            pre.appendChild(code)
        }
    }

    private fun normalizeCodeTables(content: Element) {
        content.select("table.lntable, table.rouge-table, figure.highlight table").forEach { table ->
            val codeSource = table.selectFirst("td.rouge-code pre")
                ?: table.selectFirst("td.code pre")
                ?: table.select("td.lntd pre").lastOrNull()
                ?: return@forEach
            val code = codeSource.selectFirst("code")
            val language = code?.let(::languageFrom)
                ?: languageFrom(codeSource)
                ?: languageFromCodeAncestor(table)
            val pre = Element("pre")
            val codeElement = Element("code")
            val text = normalizeCodeText((code ?: codeSource).textWithLineBreaks())
            codeElement.text(text)
            if (language != null) {
                codeElement.attr("data-lang", language)
                codeElement.addClass("language-$language")
            }
            pre.appendChild(codeElement)
            table.replaceWith(pre)
        }
    }

    private fun normalizeCodeMirrorBlock(pre: Element) {
        val content = pre.selectFirst(".cm-content") ?: return
        val language = pre.selectFirst(".sticky, [class*=header], [class*=toolbar]")
            ?.text()
            ?.split(WHITESPACE_PATTERN)
            ?.firstOrNull { it.length in 1..24 && it.all { char -> char.isLetterOrDigit() || char in "+#_-" } }
        val code = Element("code")
        code.text(normalizeCodeText(content.textWithLineBreaks()))
        language?.lowercase()?.let { normalized ->
            code.attr("data-lang", normalized)
            code.addClass("language-$normalized")
        }
        pre.empty()
        pre.appendChild(code)
    }

    private fun languageFrom(element: Element): String? {
        val dataLanguage = firstAttr(element, "data-lang", "data-language", "language")
        if (dataLanguage != null) return dataLanguage.lowercase()
        val classLanguage = LANGUAGE_REGEX.find(element.className())?.groupValues?.getOrNull(1)
        if (classLanguage != null) return classLanguage.lowercase()
        if (element.normalName() == "pre" && element.hasClass("cf")) return "c"
        return genericLanguageClassFrom(element)
    }

    private fun genericLanguageClassFrom(element: Element): String? {
        if (element.classNames().size > MAX_GENERIC_LANGUAGE_CLASSES) return null
        for (className in element.classNames()) {
            className.takeIf {
                it.lowercase() !in CODE_LANGUAGE_CLASS_BLACKLIST &&
                    it.length <= 24 &&
                    it.none(Char::isDigit) &&
                    it.all { char -> char.isLetterOrDigit() || char in "+#_-" }
            }?.let { return it.lowercase() }
        }
        return null
    }

    private fun languageFromCodeAncestor(element: Element): String? {
        var current: Element? = element
        while (current != null) {
            languageFrom(current)?.let { return it }
            current = current.parent()
        }
        return null
    }

    @Suppress("NestedBlockDepth")
    private fun Element.textWithLineBreaks(): String {
        val builder = StringBuilder()

        fun appendNode(node: Node, preserveBlankText: Boolean = false) {
            when (node) {
                is TextNode -> {
                    if (preserveBlankText || node.wholeText.isNotBlank()) {
                        builder.append(node.wholeText)
                    }
                }

                is Element -> {
                    if (node.normalName() == "br") {
                        builder.append('\n')
                    } else if (node.isCodeLineContainer()) {
                        val lineStart = builder.length
                        node.childNodes()
                            .filterNot { it.isFormattingWhitespaceText() }
                            .dropWhile { it.isCodeLineGutter() || it.isLeadingCodeLineNumber() }
                            .forEach { child ->
                                if (child is Element && child.normalName() in CODE_LINE_CELL_TAGS) {
                                    child.childNodes()
                                        .filterNot { it.isFormattingWhitespaceText() }
                                        .forEach { appendNode(it, preserveBlankText = true) }
                                } else {
                                    appendNode(child, preserveBlankText = true)
                                }
                            }
                        while (builder.length > lineStart && builder.last() == '\n') {
                            builder.deleteAt(builder.lastIndex)
                        }
                        builder.append('\n')
                    } else if (node.normalName() == "div") {
                        node.childNodes().forEach { appendNode(it, preserveBlankText = true) }
                        builder.append('\n')
                    } else {
                        node.childNodes().forEach { appendNode(it, preserveBlankText = true) }
                    }
                }
            }
        }

        val hasStructuredLines = hasStructuredCodeLines()
        childNodes()
            .filterNot { hasStructuredLines && it is TextNode && it.wholeText.isBlank() }
            .filterNot { hasStructuredLines && it.isLineBreakAfterCodeLine() }
            .forEach { appendNode(it, preserveBlankText = !hasStructuredLines) }
        return builder.toString().replace("\u00A0", " ")
    }

    private fun Node.isLineBreakAfterCodeLine(): Boolean = this is Element &&
        normalName() == "br" &&
        previousSibling()?.let { it is Element && it.isCodeLineContainer() } == true

    private fun Element.isCodeLineContainer(): Boolean {
        val isExplicitLine = hasAttr("data-line") || classNames().any { it == "line" || it.endsWith("-line") }
        val isImplicitDivLine = normalName() == "div" &&
            childNodes().size >= 2 &&
            childNodes().firstOrNull()?.isLeadingCodeLineNumber() == true
        return normalName() in CODE_LINE_CONTAINER_TAGS && (isExplicitLine || isImplicitDivLine)
    }

    private fun Element.hasStructuredCodeLines(): Boolean =
        childNodes().filterIsInstance<Element>().count { it.isCodeLineContainer() } >= 2

    private fun Node.isLeadingCodeLineNumber(): Boolean {
        if (this !is Element) return false
        val hasLineNumberHint = className().contains("line", ignoreCase = true) ||
            className().contains("gutter", ignoreCase = true) ||
            className().contains("text-end", ignoreCase = true)
        return text().trim().matches(CODE_LINE_NUMBER_PATTERN) && hasLineNumberHint
    }

    private fun Node.isCodeLineGutter(): Boolean = this is Element &&
        className().contains("gutter", ignoreCase = true)

    private fun Node.isFormattingWhitespaceText(): Boolean = this is TextNode &&
        wholeText.isBlank() &&
        wholeText.contains('\n')

    private fun normalizeCodeText(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(LEADING_NEWLINE_PATTERN, "")
        .replace(MULTI_NEWLINE_PATTERN, "\n\n")
        .trimEnd()

    private fun Element.isCodeChromeSibling(language: String?): Boolean {
        val normalizedText = text().trim().collapseWhitespace()
        if (normalizedText.isBlank() || normalizedText.length > CODE_CHROME_MAX_LENGTH) return false
        if (select("pre, code, p, blockquote, table, figure, img, picture").isNotEmpty()) return false
        if (language != null && normalizedText.equals(language, ignoreCase = true)) return true
        val hints = partialCodeChromeHaystack()
        return select("button").isNotEmpty() ||
            "copy" in hints ||
            "toolbar" in hints ||
            ("header" in hints && language != null && normalizedText.contains(language, ignoreCase = true))
    }

    private fun Element.partialCodeChromeHaystack(): String =
        "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()

    private val MULTI_NEWLINE_PATTERN = Regex("""\n{3,}""")
    private val LEADING_NEWLINE_PATTERN = Regex("""^\n+""")
    private val LANGUAGE_REGEX = Regex("""(?:^|\s)language-([A-Za-z0-9_+#-]+)(?:\s|$)""")
    private val CODE_LINE_NUMBER_PATTERN = Regex("""\d{1,5}""")
    private val CODE_LINE_CELL_TAGS = setOf("div", "span")
    private val CODE_LINE_CONTAINER_TAGS = setOf("div", "span")
    private const val CODE_CHROME_ANCESTOR_DEPTH = 4
    private const val CODE_CHROME_MAX_LENGTH = 80
    private const val MAX_GENERIC_LANGUAGE_CLASSES = 3
    private const val CODE_UI_SELECTOR =
        ".lineno, .linenumber, .line-number, .line-numbers-rows, [class*=line-number], " +
            "[class*=linenumber], [aria-hidden=true], [style*=user-select], " +
            ".code__header, .code__copy-button, button, svg"
    private val CODE_LANGUAGE_CLASS_BLACKLIST = setOf(
        "box-root",
        "chroma",
        "code",
        "codeblock",
        "codeblock-code",
        "codeblock-content",
        "codeblock-numbered",
        "codetabgroup",
        "container",
        "document",
        "highlight",
        "highlighter-rouge",
        "hljs",
        "language",
        "line",
        "lntable",
        "lntd",
        "mx-auto",
        "page-container",
        "flex",
        "flex-col",
        "font-mono",
        "gutter",
        "highlight-wrap",
        "markdown-body",
        "pe-xs",
        "plain",
        "plaintext",
        "problem-content",
        "problem-description",
        "rouge-code",
        "rouge-gutter",
        "rouge-table",
        "section",
        "section--numbered",
        "section-content",
        "shiki",
        "text",
        "text-code-snippet",
        "typeset",
    )
}
