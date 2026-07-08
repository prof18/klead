package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.isAttachedTo
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

// Collects generically structured footnote definitions — footnote-classed lists, paragraph and
// named-anchor definitions, and loose "Notes" sections delimited by a heading or divider — into
// the shared footnote section, assigning ids and stripping backref chrome.
internal object HtmlFootnoteListNormalizer {
    fun normalizeParagraphFootnoteDefinitions(content: Element) {
        val definitions = content.select("p[id]").filter { it.id().isParagraphFootnoteId() }
        if (definitions.isEmpty()) return
        val blocks = definitions.map { it.parent()?.takeIf { parent -> parent !== content } ?: it }
        val leadingHeading = blocks.firstOrNull()
            ?.previousElementSibling()
            ?.takeIf {
                it.normalName().matches(HEADING_TAG_PATTERN) &&
                    FOOTNOTE_HEADING_PATTERN.matches(it.text().trim())
            }
        val trailingDivider = blocks.lastOrNull()
            ?.nextElementSibling()
            ?.takeIf { it.normalName() == "hr" }
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            definition.removeFootnoteBackrefs()
            val oldParent = definition.parent()
            val item = Element("li").attr("id", definition.id())
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
            removeEmptyAncestors(oldParent, content)
        }
        leadingHeading?.remove()
        trailingDivider?.remove()
    }

    fun normalizeNamedAnchorFootnotes(content: Element) {
        val definitions = content.select("p").filter { paragraph ->
            paragraph.selectFirst("a[name]")?.attr("name")?.isNamedFootnoteDefinitionId() == true
        }
        if (definitions.isEmpty()) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val marker = definition.selectFirst("a[name]") ?: return@forEach
            val id = marker.attr("name")
            marker.remove()
            definition.removeFootnoteBackrefs()
            val oldParent = definition.parent()
            val item = Element("li").attr("id", id)
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
            removeEmptyAncestors(oldParent, content)
        }
    }

    fun normalizeFootnoteDefinitionBlocks(content: Element) {
        val definitions = content.select(".footnote-definition").toList()
        if (definitions.isEmpty()) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val id = definition.id().ifBlank {
                definition.selectFirst(".footnote-definition-label")?.text()?.trim()?.let { "fn$it" }
                    ?: definition.selectFirst("[id]")?.id()
                    ?: ""
            }
            definition.select(".footnote-definition-label").remove()
            definition.removeFootnoteBackrefs()
            val item = Element("li").attr("id", id.ifBlank { "fn${section.childrenSize() + 1}" })
            definition.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
    }

    fun normalizeAsideFootnoteLists(content: Element) {
        content.select("aside ol").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            if (list.select("> li").isEmpty()) return@forEach
            val start = list.attr("start").toIntOrNull() ?: 1
            val section = lazyFootnoteSection(content)
            list.select("> li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    item.attr("id", "fn${start + index}")
                }
                item.removeFootnoteBackrefs()
                section.appendChild(item)
            }
            list.remove()
        }
    }

    fun normalizeReferenceDivFootnotes(content: Element) {
        val references = content.select(".references .reference").filter {
            it.selectFirst(".reference-number[id], [id].reference-number") != null &&
                it.selectFirst(".reference-content") != null
        }
        if (references.isEmpty()) return
        val containers = references.mapNotNull { reference ->
            reference.parents().firstOrNull { it.hasClass("references") }
        }.distinct()
        val section = lazyFootnoteSection(content)
        references.forEachIndexed { index, reference ->
            val marker = reference.selectFirst(".reference-number[id], [id].reference-number")
            val body = reference.selectFirst(".reference-content") ?: return@forEachIndexed
            val item = Element("li").attr("id", marker?.id()?.ifBlank { "ref${index + 1}" } ?: "ref${index + 1}")
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            reference.remove()
        }
        containers.forEach { container ->
            if (container.select(".reference").isEmpty()) {
                container.remove()
            }
        }
    }

    fun normalizeReferenceFootnoteLists(content: Element) {
        content.select("ol").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            if (!list.isReferenceFootnoteList()) return@forEach
            list.select("> li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    val nestedId = item.selectFirst("[id]")?.id().orEmpty()
                    item.attr("id", nestedId.ifBlank { "fn${index + 1}" })
                }
                item.select("a[id]").filter { anchor ->
                    anchor.attr("href").isBlank() && anchor.text().isBlank()
                }.forEach { it.remove() }
                item.removeFootnoteBackrefs()
            }
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    fun normalizeFootnoteLists(content: Element) {
        content.select(
            "ol.footnotes, ol.references, ol[class*=footnote], ol[id*=footnote], ol[id*=fn], " +
                "section[data-footnotes] ol, .footnotes ol",
        ).forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            list.select("li").forEachIndexed { index, item ->
                if (item.id().isBlank()) {
                    val nestedId = item.selectFirst("[id]")?.id().orEmpty()
                    item.attr("id", nestedId.ifBlank { "fn${index + 1}" })
                }
                item.removeFootnoteBackrefs()
            }
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    fun normalizeLooseFootnoteSections(content: Element) {
        for (marker in content.select("hr, h1, h2, h3, h4, h5, h6").toList()) {
            if (!marker.isAttachedTo(content)) continue
            if (!marker.isLooseFootnoteDelimiter()) continue

            val definitions = mutableListOf<Element>()
            var sibling = marker.nextElementSibling()
            while (sibling != null) {
                if (sibling.isLooseFootnoteStart()) {
                    definitions.add(sibling)
                    sibling = sibling.nextElementSibling()
                    while (sibling != null && !sibling.isLooseFootnoteStart() && !sibling.isLooseFootnoteStop()) {
                        definitions.add(sibling)
                        sibling = sibling.nextElementSibling()
                    }
                } else if (sibling.text().trim().isBlank()) {
                    sibling = sibling.nextElementSibling()
                } else {
                    break
                }
            }
            if (definitions.none { it.isLooseFootnoteStart() }) continue

            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            marker.before(section)
            var currentItem: Element? = null
            for (definition in definitions) {
                if (definition.isLooseFootnoteStart()) {
                    val id = "fn${definition.looseFootnoteNumber()}"
                    currentItem = Element("li").attr("id", id)
                    section.appendChild(currentItem)
                    definition.removeLooseFootnoteMarker()
                }
                val target = currentItem ?: continue
                definition.remove()
                target.appendChild(definition)
            }
            marker.remove()
        }
    }

    fun normalizeTrailingLooseFootnoteDefinitions(content: Element) {
        val definitions = content.select("p").filter { it.isLooseFootnoteStart() }
        if (definitions.size < 2) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val id = "fn${definition.looseFootnoteNumber()}"
            val item = Element("li").attr("id", id)
            definition.removeLooseFootnoteMarker()
            definition.remove()
            item.appendChild(definition)
            section.appendChild(item)
        }
    }

    private fun Element.removeFootnoteBackrefs() {
        select(
            "a[href*=fnref], a[href*=ftnt_ref], a[href*=_ftnref], a[class*=backref], a[class*=to-top], " +
                "a[href*=FnAnchor], a[href*=-link], a[aria-label*=Back], a[aria-label*=back], " +
                "a[aria-label*=Jump], a[aria-label*=jump], " +
                ".footnote-backref, .footnote-back-link, .data-footnote-backref",
        ).remove()
        select(".easy-footnote-margin-adjust").remove()
    }

    private fun Element.isLooseFootnoteDelimiter(): Boolean =
        normalName() == "hr" || text().trim().matches(FOOTNOTE_HEADING_PATTERN)

    private fun Element.isLooseFootnoteStop(): Boolean =
        normalName().matches(HEADING_TAG_PATTERN) && !text().trim().matches(FOOTNOTE_HEADING_PATTERN)

    private fun Element.isLooseFootnoteStart(): Boolean = startsWithFootnoteMarker() && looseFootnoteNumber() != null

    private fun Element.startsWithFootnoteMarker(): Boolean {
        val firstElement = children().firstOrNull() ?: return false
        return childNodes()
            .takeWhile { it !== firstElement }
            .all { it !is TextNode || it.wholeText.isBlank() }
    }

    private fun Element.looseFootnoteNumber(): String? {
        val firstElement = children().firstOrNull()
        val markerText = when {
            firstElement?.normalName() == "sup" -> firstElement.text().trim()

            firstElement?.normalName() == "span" -> firstElement.selectFirst("sup")?.text()?.trim()

            firstElement != null && firstElement.normalName() in setOf("strong", "b") -> {
                firstElement.selectFirst("sup")?.text()?.trim()
                    ?: FOOTNOTE_NUMBER_PREFIX_PATTERN.find(firstElement.text().trim())?.groupValues?.get(1)
            }

            else -> null
        }
        return markerText?.normalizeFootnoteNumberText()?.takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }
    }

    private fun Element.removeLooseFootnoteMarker() {
        val marker = children().firstOrNull()?.takeIf { candidate ->
            val isMarkerElement = candidate.normalName() in FOOTNOTE_MARKER_TAGS ||
                (candidate.normalName() == "span" && candidate.selectFirst("sup") != null)
            val hasMarkerText = candidate.text().trim().matches(FOOTNOTE_NUMBER_PATTERN) ||
                candidate.selectFirst("sup") != null
            isMarkerElement && hasMarkerText
        } ?: return
        if (marker.normalName() == "span") {
            marker.selectFirst("sup")
                ?.takeIf {
                    it.text()
                        .trim()
                        .normalizeFootnoteNumberText()
                        .matches(FOOTNOTE_NUMBER_PATTERN)
                }
                ?.remove()
            if (marker.text().trim().isBlank() && marker.select("a, img, code").isEmpty()) {
                marker.remove()
            }
            return
        }
        if (marker.normalName() in setOf("strong", "b")) {
            marker.selectFirst("sup")
                ?.takeIf {
                    it.text()
                        .trim()
                        .normalizeFootnoteNumberText()
                        .matches(FOOTNOTE_NUMBER_PATTERN)
                }
                ?.remove()
            if (marker.text().trim().matches(FOOTNOTE_NUMBER_PATTERN) || marker.text().trim().isBlank()) {
                marker.remove()
            }
            return
        }
        marker.remove()
    }

    private fun String.normalizeFootnoteNumberText(): String = trim().trim('[', ']')

    private fun Element.isReferenceFootnoteList(): Boolean {
        if (select("> li [id]").isEmpty()) return false
        val previous = previousElementSibling()
        return previous != null &&
            previous.normalName().matches(HEADING_TAG_PATTERN) &&
            FOOTNOTE_HEADING_PATTERN.matches(previous.text().trim())
    }

    private fun String.isParagraphFootnoteId(): Boolean = matches(PARAGRAPH_FOOTNOTE_ID_PATTERN)

    private fun String.isNamedFootnoteDefinitionId(): Boolean = matches(NAMED_FOOTNOTE_DEFINITION_ID_PATTERN)

    private val FOOTNOTE_NUMBER_PREFIX_PATTERN = Regex("""^(\d{1,4})(?:$|[\].):]|\s)""")
    private val PARAGRAPH_FOOTNOTE_ID_PATTERN = Regex("""(?i)^(?:ftnt|_ftn)\d+$""")
    private val NAMED_FOOTNOTE_DEFINITION_ID_PATTERN = Regex("""(?i)^(?:Footnote|_ftn)\D*\d+$""")
    private val FOOTNOTE_MARKER_TAGS = setOf("sup", "strong", "b")
}
