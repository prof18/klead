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
            val declarations = CssDeclarations.split(original)
            val retained = declarations.flatMap { declaration ->
                val property = CssDeclarations.withoutComments(declaration).substringBefore(':').trim().lowercase()
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

    private fun semanticFontProperties(declaration: String): List<String> {
        val value = CssDeclarations.withoutComments(declaration).substringAfter(':', "").trim()
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
