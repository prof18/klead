package com.prof18.klead.internal

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// When the detected content references footnotes (<sup>N</sup>) whose definitions live outside
// the content element — a sibling "Footnotes" section, say — clones the matching definition
// blocks into the content so the footnote normalizer can pick them up.
internal object ExternalFootnoteMerger {
    fun merge(document: Document, content: Element) {
        val referencedNumbers = content.select("sup")
            .mapNotNull { it.text().normalizedFootnoteNumber() }
            .toSet()
        if (referencedNumbers.isEmpty()) return

        document.select("section, aside, div")
            .filterNot { it.isInside(content) }
            .filter { it.isExternalFootnoteBlock(referencedNumbers) }
            .forEach { content.appendChild(it.clone()) }
    }

    private fun Element.isExternalFootnoteBlock(referencedNumbers: Set<String>): Boolean {
        val hints = "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()
        if (!EXTERNAL_FOOTNOTE_HINT_PATTERN.containsMatchIn(hints)) return false

        val definitionNumbers = select("p, li")
            .mapNotNull { it.leadingFootnoteDefinitionNumber() }
            .toSet()
        return definitionNumbers.any { it in referencedNumbers }
    }

    private fun Element.leadingFootnoteDefinitionNumber(): String? {
        val marker = children().firstOrNull() ?: return null
        return when (marker.normalName()) {
            "sup", "span", "a" -> marker.text().normalizedFootnoteNumber()
            else -> null
        }
    }

    private fun Element.isInside(root: Element): Boolean = this === root || parents().any { it === root }

    private fun String.normalizedFootnoteNumber(): String? = trim()
        .trim('[', ']')
        .takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }

    private val FOOTNOTE_NUMBER_PATTERN = Regex("""\d{1,4}""")
    private val EXTERNAL_FOOTNOTE_HINT_PATTERN = Regex("""(?i)(footnotes?|endnotes?)""")
}
