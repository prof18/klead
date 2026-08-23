package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.dom.replaceWithChildren

// Rewrites arXiv LaTeXML markup: bibliography lists become footnote definitions, citations
// become footnote references, cross-reference links unwrap, and footnote marks get spacing.
internal object HtmlArxivNormalizer {
    fun normalize(content: Element) {
        normalizeArxivBibliographyCitations(content)
        unwrapArxivCrossReferenceLinks(content)
        normalizeArxivFootnoteMarks(content)
    }

    private fun unwrapArxivCrossReferenceLinks(content: Element) {
        content.select("a.ltx_ref[href^=#]").forEach { link ->
            link.replaceWithChildren()
        }
    }

    private fun normalizeArxivBibliographyCitations(content: Element) {
        val bibliographyTargets = linkedMapOf<String, String>()
        content.select(".ltx_bibliography").forEach { bibliography ->
            val items = bibliography.select(".ltx_bibitem[id]")
            if (items.isEmpty()) return@forEach

            bibliography.attr("data-footnotes", "true").addClass("footnotes")
            bibliography.select(".ltx_biblist").forEach { list ->
                if (list.normalName() == "ul") {
                    list.tagName("ol")
                }
            }
            items.forEach { item ->
                val footnoteId = "fn${bibliographyTargets.size + 1}"
                bibliographyTargets[item.id()] = footnoteId
                item.attr("id", footnoteId)
                item.select(".ltx_tag_bibitem").remove()
            }
        }

        if (bibliographyTargets.isEmpty()) return

        content.select("cite.ltx_cite").forEach { citation ->
            val targets = citation.select("a[href]").mapNotNull { link ->
                val target = link.hrefFragmentTarget()?.let(bibliographyTargets::get)
                target?.let { it to link.text().trim() }
            }
            if (targets.isEmpty()) return@forEach

            targets.forEachIndexed { index, (target, label) ->
                if (index > 0) {
                    citation.before(TextNode(" "))
                }
                citation.before(
                    Element("sup").appendChild(
                        Element("a")
                            .attr("href", "#$target")
                            .text(label.ifBlank { target.removePrefix("fn") }),
                    ),
                )
            }
            citation.remove()
        }
    }

    private fun normalizeArxivFootnoteMarks(content: Element) {
        content.select(".ltx_role_footnotemark").forEach { mark ->
            mark.select(".ltx_note_outer").remove()
            if (mark.needsLeadingSpaceBeforeInlineFootnoteMark()) {
                mark.before(TextNode(" "))
            }
        }
    }

    private fun Element.needsLeadingSpaceBeforeInlineFootnoteMark(): Boolean {
        val previous = previousSibling() ?: return false
        val previousText = when (previous) {
            is TextNode -> previous.getWholeText()
            is Element -> previous.text()
            else -> ""
        }
        val previousChar = previousText.lastOrNull { !it.isWhitespace() } ?: return false
        return previousChar.isLetterOrDigit()
    }
}
