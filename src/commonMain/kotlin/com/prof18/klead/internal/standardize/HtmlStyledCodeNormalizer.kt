package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/** Promotes paragraph-based snippets in padded panels before publisher fonts are removed. */
internal object HtmlStyledCodeNormalizer {
    fun normalize(content: Element): Set<Element> {
        val promoted = mutableSetOf<Element>()
        for (panel in content.select("div[style]")) {
            val properties = panel.styleProperties()
            if (properties.keys.none { it == "padding" || it.startsWith("padding-") }) continue
            if (properties.keys.none { it == "background" || it == "background-color" }) continue
            if (panel.parents().any { it.normalName() in setOf("pre", "code") }) continue
            val lines = panel.children().toList()
            if (lines.isEmpty() || lines.any { it.normalName() != "p" } || panel.ownText().isNotBlank()) continue
            val inheritedFont = properties["font-family"]
            if (!lines.all { it.hasOnlyMonospaceText(inheritedFont) }) continue

            val code = Element("code")
            code.text(lines.joinToString("\n") { it.codeLineText() }.replace('\u00A0', ' '))
            val pre = Element("pre")
            pre.appendChild(code)
            panel.replaceWith(pre)
            promoted.add(pre)
        }
        return promoted
    }

    private fun Node.hasOnlyMonospaceText(inheritedFont: String?): Boolean = when (this) {
        is TextNode -> getWholeText().isBlank() || inheritedFont.isMonospace()

        is Element -> {
            val font = styleProperties()["font-family"] ?: inheritedFont
            normalName() in INLINE_LINE_TAGS && childNodes().all { it.hasOnlyMonospaceText(font) }
        }

        else -> false
    }

    private fun String?.isMonospace(): Boolean = this?.split(',')?.any {
        it.substringBefore('!').trim().trim('\'', '"').equals("monospace", ignoreCase = true)
    } == true

    private fun Element.styleProperties(): Map<String, String> = CssDeclarations.split(attr("style"))
        .associate { declaration ->
            val clean = CssDeclarations.withoutComments(declaration)
            clean.substringBefore(':').trim().lowercase() to clean.substringAfter(':', "").trim()
        }

    private fun Node.codeLineText(): String = when (this) {
        is TextNode -> getWholeText()
        is Element -> if (normalName() == "br") "\n" else childNodes().joinToString("") { it.codeLineText() }
        else -> ""
    }

    private val INLINE_LINE_TAGS = setOf("p", "span", "code", "b", "strong", "i", "em", "br")
}
