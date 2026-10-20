package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import com.prof18.klead.internal.dom.deepCopy

// One converter per publishing format: each rewrites that format's footnote markup into the
// canonical inline <sup><a href="#id"> reference plus a <li id> definition inside the shared
// footnote section.
internal object HtmlFootnoteFormats {
    fun normalizeDataDefinitionFootnotes(content: Element) {
        val references = content.selectDescendants("[data-definition]").toList()
        if (references.isEmpty()) return
        val section = lazyFootnoteSection(content)
        // One id lookup per pass: selecting every [id] again for each reference was quadratic.
        val elementsById = content.selectDescendants("[id]").groupBy { it.id() }
        references.forEachIndexed { index, reference ->
            val targetId = reference.attr("data-definition").trim()
            val target = elementsById[targetId]
                ?.firstOrNull { it.parent() != null && reference.parents().none { parent -> parent === it } }
                ?: return@forEachIndexed
            val number = (index + 1).toString()
            val item = Element("li").attr("id", "fn$number")
            target.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#fn$number").text(number)
            reference.replaceWith(sup)
            target.remove()
        }
    }

    // GNU Texinfo / makeinfo: <div class="footnotes-segment"> holds a heading per footnote —
    // <h5 class="footnote-body-heading"><a id="FOOTn" href="#DOCFn">(n)</a></h5> — followed by
    // the body <p>. Inline markers (<a class="footnote" id="DOCFn" href="#FOOTn">) point at the
    // heading anchor id, so register each footnote under that id.
    fun normalizeTexinfoFootnotes(content: Element) {
        val segments = content.selectDescendants("div.footnotes-segment").toList()
        if (segments.isEmpty()) return
        val section = lazyFootnoteSection(content)
        val markersByTarget = content.linksByFragmentTarget("a.footnote[href^=#]")
        segments.forEach { segment ->
            segment.select("h5.footnote-body-heading").forEach { heading ->
                val id = heading.selectFirst("a[id]")?.id()?.trim().orEmpty()
                if (id.isBlank()) return@forEach
                val item = Element("li").attr("id", id)
                var sibling = heading.nextElementSibling()
                while (sibling != null &&
                    !(sibling.normalName() == "h5" && sibling.hasClass("footnote-body-heading"))
                ) {
                    val next = sibling.nextElementSibling()
                    if (sibling.text().trim().isNotBlank() || sibling.selectFirst("img, br") != null) {
                        item.appendChild(sibling.deepCopy())
                    }
                    sibling = next
                }
                section.appendChild(item)
                markersByTarget[id]?.forEach { marker -> marker.normalizeFootnoteReferenceLink(id) }
            }
            segment.remove()
        }
    }

    // O'Reilly / HTMLBook: definitions are <p data-type="footnote" id="chNNfnK">, each opening
    // with a <sup><a href="#…-marker">K</a></sup> backlink; the rest is the body. Inline markers
    // are <a data-type="noteref" href="chNN.html#chMMfnK">, whose href fragment is the def id.
    fun normalizeOReillyFootnotes(content: Element) {
        val definitions = content.selectDescendants("p[data-type=footnote][id]").toList()
        if (definitions.isEmpty()) return
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val id = definition.id().trim()
            if (id.isBlank()) return@forEach
            val item = Element("li").attr("id", id)
            val clone = definition.deepCopy()
            val marker = clone.children().firstOrNull()
            if (marker?.normalName() == "sup" && marker.selectFirst("a[href*=#]") != null) {
                marker.remove()
                clone.trimLeadingTextWhitespace()
            }
            clone.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
        content.selectDescendants("a[data-type=noteref][href*=#]").forEach { marker ->
            val fragment = marker.attr("href").substringAfterLast("#").trim()
            if (fragment.isNotBlank()) marker.normalizeFootnoteReferenceLink(fragment)
        }
    }

    private fun Element.trimLeadingTextWhitespace() {
        val first = childNodes().firstOrNull() as? TextNode ?: return
        val trimmed = first.getWholeText().trimStart()
        if (trimmed.isBlank()) first.remove() else first.text(trimmed)
    }

    fun normalizeInlineFootnoteSpans(content: Element) {
        val footnotes = content.selectDescendants(".inline-footnote")
            .filter { it.selectFirst(".footnoteContent") != null }
        if (footnotes.isEmpty()) return

        val section = lazyFootnoteSection(content)
        footnotes.forEachIndexed { index, footnote ->
            val body = footnote.selectFirst(".footnoteContent") ?: return@forEachIndexed
            val number = footnote.ownText().trim().ifBlank { (index + 1).toString() }
            val id = "fn$number"
            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$id").text(number)
            footnote.replaceWith(sup)
        }
    }

    fun normalizeInlineFootnoteContainers(content: Element) {
        val footnotes = content.selectDescendants(".footnote-container")
            .filter { it.selectFirst(".footnote") != null }
        if (footnotes.isEmpty()) return

        val section = lazyFootnoteSection(content)
        footnotes.forEachIndexed { index, footnote ->
            val body = footnote.selectFirst(".footnote") ?: return@forEachIndexed
            val number = footnote.selectFirst("label.footnote-number[for]")
                ?.attr("for")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: footnote.selectFirst("input.margin-toggle[id]")
                    ?.id()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: (index + 1).toString()
            val id = "fn$number"
            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)

            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$id").text(number)
            footnote.replaceWith(sup)
        }
    }

    fun normalizeSidenoteFootnotes(content: Element) {
        content.selectDescendants("label.footref[for]").forEach { label ->
            val target = label.attr("for").trim()
            if (target.isBlank()) return@forEach
            var sibling = label.nextElementSibling()
            if (sibling?.normalName() == "input" && sibling.id() == target) {
                val next = sibling.nextElementSibling()
                sibling.remove()
                sibling = next
            }
            if (sibling?.hasClass("sidenote") == true) {
                sibling.remove()
            }
            val sup = Element("sup")
            sup.appendElement("a").attr("href", "#$target").text(label.text().trim())
            label.replaceWith(sup)
        }

        content.selectDescendants("sup.footnote-reference + span.sidenote").forEach { it.remove() }
    }

    fun normalizeOrgModeFootdefs(content: Element) {
        val definitions = content.selectDescendants(".footdef").toList()
        if (definitions.isEmpty()) return
        val containers = definitions.mapNotNull { it.parent() }.distinct()
        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val marker = definition.selectFirst(".footnum[id], a[id]") ?: return@forEach
            val body = definition.selectFirst("[role=doc-footnote], .footpara") ?: definition
            val item = Element("li").attr("id", marker.id())
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
        containers.forEach { container ->
            if (
                container.select(".footdef").isEmpty() &&
                FOOTNOTE_HEADING_PATTERN.containsMatchIn(container.text().trim())
            ) {
                container.remove()
            }
        }
    }

    fun normalizeSubstackFootnotes(content: Element) {
        val definitions = content.selectDescendants(".footnote").filter { definition ->
            definition.selectFirst(
                ".footnote-number[id], [data-component-name=FootnoteToDOM] .footnote-number[id]",
            ) != null &&
                definition.selectFirst(".footnote-content") != null
        }
        if (definitions.isEmpty()) return

        val section = lazyFootnoteSection(content)
        val linksByTarget = content.linksByFragmentTarget("a[href]")
        definitions.forEachIndexed { index, definition ->
            val marker = definition.selectFirst(".footnote-number[id]")
            val body = definition.selectFirst(".footnote-content") ?: return@forEachIndexed
            val id = marker?.id()?.ifBlank { "footnote-${index + 1}" } ?: "footnote-${index + 1}"

            linksByTarget[id]?.forEach { link -> link.normalizeFootnoteReferenceLink(id) }

            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
    }

    fun normalizeWikidotFootnotes(content: Element) {
        val definitions = content.selectDescendants(".footnotes-footer .footnote-footer[id]").toList()
        if (definitions.isEmpty()) return

        val definitionIds = definitions.map { it.id() }.toSet()
        content.selectDescendants("sup.footnoteref a, a.footnoteref").forEach { link ->
            val target = link.wikidotFootnoteTarget()
            if (target != null && target in definitionIds) {
                link.normalizeFootnoteReferenceLink(target)
            }
        }

        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val item = Element("li").attr("id", definition.id())
            val clone = definition.deepCopy()
            clone.select("> a:first-child").remove()
            clone.removeLeadingFootnoteDefinitionPunctuation()
            clone.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
        }
        content.selectDescendants(".footnotes-footer").forEach { it.remove() }
    }

    fun normalizeDhammatalksFootnotes(content: Element) {
        val notes = content.selectDescendants("div.note").filter { note ->
            note.select("> p[id]").any { it.id().matches(DHAMMATALKS_NOTE_ID_PATTERN) }
        }
        if (notes.isEmpty()) return

        notes.forEach { note ->
            val definitionIds = note.dhammatalksDefinitionIds()
            if (definitionIds.isEmpty()) return@forEach

            content.normalizeDhammatalksReferences(definitionIds)
            note.moveDhammatalksDefinitionsTo(lazyFootnoteSection(content), definitionIds)
        }
    }

    private fun Element.dhammatalksDefinitionIds(): Set<String> = select("> p[id]")
        .filter { it.id().matches(DHAMMATALKS_NOTE_ID_PATTERN) }
        .map { it.id() }
        .toSet()

    private fun Element.normalizeDhammatalksReferences(definitionIds: Set<String>) {
        select("span.fn a[href], a[href]").forEach { link ->
            val target = link.hrefFragmentTarget()
            if (target != null && target in definitionIds) {
                link.replaceWithFootnoteReference(target)
            }
        }
    }

    private fun Element.moveDhammatalksDefinitionsTo(section: Element, definitionIds: Set<String>) {
        val children = children().toList()
        var index = 0
        while (index < children.size) {
            val definition = children[index]
            if (definition.id() in definitionIds) {
                index = appendDhammatalksDefinition(children, index, definitionIds, section)
            } else {
                index++
            }
        }
        remove()
    }

    private fun appendDhammatalksDefinition(
        children: List<Element>,
        startIndex: Int,
        definitionIds: Set<String>,
        section: Element,
    ): Int {
        val definition = children[startIndex]
        val item = Element("li").attr("id", definition.id())
        definition.removeLeadingNumberMarker()

        var index = startIndex
        while (index < children.size) {
            val current = children[index]
            if (current !== definition && current.id() in definitionIds) break
            current.appendToDhammatalksDefinition(definition, item)
            index++
        }
        section.appendChild(item)
        return index
    }

    private fun Element.appendToDhammatalksDefinition(definition: Element, item: Element) {
        if (hasClass("notetitle")) return
        if (this !== definition) {
            removeTerminalPeriodAfterTrailingLink()
        }
        remove()
        item.appendChild(this)
    }

    private fun Element.wikidotFootnoteTarget(): String? {
        wikidotFootnoteScrollTargetPattern.find(attr("onclick"))?.let { match ->
            return match.groupValues[1]
        }
        wikidotFootnoteRefIdPattern.matchEntire(id())?.let { match ->
            return "footnote-${match.groupValues[1]}"
        }
        val text = text().trim()
        return text.takeIf { it.matches(FOOTNOTE_NUMBER_PATTERN) }?.let { "footnote-$it" }
    }

    private fun Element.removeLeadingFootnoteDefinitionPunctuation() {
        val first = childNodes().firstOrNull() as? TextNode ?: return
        val cleaned = first.getWholeText().trimStart().removePrefix(".").trimStart()
        if (cleaned.isBlank()) {
            first.remove()
        } else {
            first.text(cleaned)
        }
    }

    // Built once per pass: re-selecting every link for each footnote was quadratic.
    private fun Element.linksByFragmentTarget(selector: String): Map<String, List<Element>> =
        select(selector).filter { it.hrefFragmentTarget() != null }.groupBy { it.hrefFragmentTarget().orEmpty() }

    private fun Element.normalizeFootnoteReferenceLink(targetId: String) {
        // A link already wrapped in a <sup> by an earlier footnote with the same id was replaced by its clone.
        if (parent() == null) return
        attr("href", "#$targetId")
        if (parent()?.normalName() == "sup") return

        val replacement = Element("sup")
        replacement.appendChild(deepCopy())
        replaceWith(replacement)
    }

    private fun Element.replaceWithFootnoteReference(targetId: String) {
        val normalizedLink = deepCopy()
            .attr("href", "#$targetId")
        val replacement = Element("sup")
            .appendChild(normalizedLink)
        val wrapper = parent()?.takeIf { parent ->
            parent.normalName() == "span" &&
                parent.hasClass("fn") &&
                parent.children().size == 1
        }
        if (wrapper != null) {
            wrapper.replaceWith(replacement)
        } else {
            replaceWith(replacement)
        }
    }

    private fun Element.removeLeadingNumberMarker() {
        val first = childNodes().firstOrNull() as? TextNode ?: return
        first.text(FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN.replaceFirst(first.getWholeText(), ""))
    }

    private fun Element.removeTerminalPeriodAfterTrailingLink() {
        val nodes = childNodes()
        val lastTextIndex = nodes.indexOfLast { node ->
            node !is TextNode || node.getWholeText().isNotBlank()
        }
        val lastText = nodes.getOrNull(lastTextIndex) as? TextNode ?: return
        if (!TRAILING_LINK_PERIOD_TEXT_PATTERN.matches(lastText.getWholeText())) return

        val previousElement = nodes
            .take(lastTextIndex)
            .lastOrNull { node ->
                when (node) {
                    is TextNode -> node.getWholeText().isNotBlank()
                    is Element -> true
                    else -> false
                }
            } as? Element
        if (previousElement?.normalName() != "a") return

        lastText.text(lastText.getWholeText().replace(TRAILING_PERIOD_PATTERN, ""))
    }

    private val FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN = Regex("""^\s*\d{1,4}\.\s*""")
    private val TRAILING_LINK_PERIOD_TEXT_PATTERN = Regex("""^\s*[)\]]?\.\s*$""")
    private val TRAILING_PERIOD_PATTERN = Regex("""\.\s*$""")
    private val DHAMMATALKS_NOTE_ID_PATTERN = Regex("""(?i).+note\d{1,4}$""")
    private val wikidotFootnoteRefIdPattern = Regex("""footnoteref-(\d+)""")
    private val wikidotFootnoteScrollTargetPattern = Regex("""scrollToReference\(['"]([^'"]+)['"]\)""")
}
