package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element

/** Removes publisher styling that can override the reader's theme from cleaned HTML output. */
internal object HtmlPresentationNormalizer {
    fun normalize(content: Element) {
        for (element in content.select("*").toList()) {
            if (isVisualMarkup(element)) continue

            element.removeAttr("bgcolor")
            element.removeAttr("color")
            element.removeAttr("face")
            if (element.tagName().equals("font", ignoreCase = true)) element.removeAttr("size")

            if (!element.hasAttr("style")) continue
            val original = element.attr("style")
            val declarations = splitDeclarations(original)
            val retained = declarations.flatMap { declaration ->
                val property = stripCssComments(declaration).substringBefore(':').trim().lowercase()
                when {
                    property == "font" -> semanticFontProperties(declaration)
                    property in READER_OWNED_PROPERTIES -> emptyList()
                    else -> listOf(declaration)
                }
            }
            if (retained != declarations) {
                if (retained.isEmpty()) {
                    element.removeAttr("style")
                } else {
                    element.attr("style", retained.joinToString("; "))
                }
            }
        }
    }

    private fun isVisualMarkup(element: Element): Boolean {
        val isVisualTag = element.tagName().equals("svg", ignoreCase = true) ||
            element.tagName().equals("math", ignoreCase = true)
        if (isVisualTag) return true

        return element.parents().any { ancestor ->
            ancestor.tagName().equals("svg", ignoreCase = true) ||
                ancestor.tagName().equals("math", ignoreCase = true)
        }
    }

    /** Split declarations while honoring strings, comments, and function arguments. */
    private fun splitDeclarations(style: String): List<String> {
        val declarations = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var quote: Char? = null
        var escaped = false
        var inComment = false
        var index = 0
        while (index < style.length) {
            val character = style[index]
            if (escaped) {
                escaped = false
                index++
                continue
            }
            if (quote != null) {
                if (character == quote) quote = null
                if (character == '\\') escaped = true
                index++
                continue
            }
            if (inComment) {
                if (character == '*' && style.getOrNull(index + 1) == '/') {
                    inComment = false
                    index += 2
                } else {
                    index++
                }
                continue
            }
            if (character == '/' && style.getOrNull(index + 1) == '*') {
                inComment = true
                index += 2
                continue
            }
            when (character) {
                '\'', '"' -> quote = character

                '(' -> parentheses++

                ')' -> if (parentheses > 0) parentheses--

                ';' -> if (parentheses == 0) {
                    style.substring(start, index).trim().takeIf(String::isNotEmpty)?.let(declarations::add)
                    start = index + 1
                }
            }
            if (character == '\\') escaped = true
            index++
        }

        style.substring(start).trim().takeIf(String::isNotEmpty)?.let(declarations::add)
        return declarations
    }

    private fun stripCssComments(value: String): String {
        val result = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (escaped) {
                result.append(character)
                escaped = false
            } else if (quote != null) {
                result.append(character)
                if (character == quote) quote = null
                if (character == '\\') escaped = true
            } else if (character == '\\') {
                result.append(character)
                escaped = true
            } else if (character == '\'' || character == '"') {
                result.append(character)
                quote = character
            } else if (character == '/' && value.getOrNull(index + 1) == '*') {
                val end = value.indexOf("*/", startIndex = index + 2)
                if (end < 0) break
                index = end + 2
                result.append(' ')
                continue
            } else {
                result.append(character)
            }
            index++
        }
        return result.toString()
    }

    private fun semanticFontProperties(declaration: String): List<String> {
        val value = stripCssComments(declaration).substringAfter(':', "").trim()
        val sizeMatch = FONT_SIZE_TOKEN.find(value) ?: return emptyList()
        val prefix = value.substring(0, sizeMatch.range.first).trim()
        val tokens = prefix.split(WHITESPACE).filter(String::isNotBlank)
        val important = if (value.endsWith("!important", ignoreCase = true)) " !important" else ""
        val properties = mutableListOf<String>()
        tokens.firstOrNull { it.equals("italic", ignoreCase = true) || it.equals("oblique", ignoreCase = true) }
            ?.let { properties += "font-style: $it$important" }
        tokens.firstOrNull {
            it.equals("bold", ignoreCase = true) || it.equals("bolder", ignoreCase = true) ||
                it.equals("lighter", ignoreCase = true) || it.toIntOrNull() in 100..900
        }?.let { properties += "font-weight: $it$important" }
        return properties
    }

    private val FONT_SIZE_TOKEN = Regex(
        """(?i)(?:^|\s)(?:xx-small|x-small|small|medium|large|x-large|xx-large|xxx-large|larger|smaller|[+-]?(?:\d*\.)?\d+(?:px|pt|pc|em|rem|ex|ch|vw|vh|vmin|vmax|cm|mm|in|q|%))(?:/[^\s]+)?(?=\s|$)""",
    )

    private val WHITESPACE = Regex("\\s+")

    private val READER_OWNED_PROPERTIES = setOf(
        "color",
        "background",
        "background-color",
        "font-family",
        "font-size",
        "line-height",
    )
}
