package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.isAttachedTo
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal object HtmlFootnoteNormalizer {
    fun normalizeFootnotes(content: Element) {
        normalizeDataDefinitionFootnotes(content)
        normalizeInlineFootnoteSpans(content)
        normalizeInlineFootnoteContainers(content)
        normalizeParagraphFootnoteDefinitions(content)
        normalizeNamedAnchorFootnotes(content)
        normalizeSidenoteFootnotes(content)
        normalizeOrgModeFootdefs(content)
        normalizeSubstackFootnotes(content)
        normalizeWikidotFootnotes(content)
        normalizeDhammatalksFootnotes(content)
        normalizeFootnoteDefinitionBlocks(content)
        normalizeAsideFootnoteLists(content)
        normalizeReferenceDivFootnotes(content)
        normalizeReferenceFootnoteLists(content)
        normalizeFootnoteLists(content)
        normalizeLooseFootnoteSections(content)
        normalizeTrailingLooseFootnoteDefinitions(content)
        removeFootnoteDividers(content)
    }

    private fun normalizeDataDefinitionFootnotes(content: Element) {
        val references = content.select("[data-definition]").toList()
        if (references.isEmpty()) return
        val section = lazyFootnoteSection(content)
        references.forEachIndexed { index, reference ->
            val targetId = reference.attr("data-definition").trim()
            val target = content.select("[id]").firstOrNull { it.id() == targetId } ?: return@forEachIndexed
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

    private fun normalizeInlineFootnoteSpans(content: Element) {
        val footnotes = content.select(".inline-footnote")
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

    private fun normalizeInlineFootnoteContainers(content: Element) {
        val footnotes = content.select(".footnote-container")
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

    private fun normalizeFootnoteDefinitionBlocks(content: Element) {
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

    private fun normalizeSidenoteFootnotes(content: Element) {
        content.select("label.footref[for]").forEach { label ->
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

        content.select("sup.footnote-reference + span.sidenote").remove()
    }

    private fun normalizeOrgModeFootdefs(content: Element) {
        val definitions = content.select(".footdef").toList()
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

    private fun normalizeSubstackFootnotes(content: Element) {
        val definitions = content.select(".footnote").filter { definition ->
            definition.selectFirst(
                ".footnote-number[id], [data-component-name=FootnoteToDOM] .footnote-number[id]",
            ) != null &&
                definition.selectFirst(".footnote-content") != null
        }
        if (definitions.isEmpty()) return

        val section = lazyFootnoteSection(content)
        definitions.forEachIndexed { index, definition ->
            val marker = definition.selectFirst(".footnote-number[id]")
            val body = definition.selectFirst(".footnote-content") ?: return@forEachIndexed
            val id = marker?.id()?.ifBlank { "footnote-${index + 1}" } ?: "footnote-${index + 1}"

            content.select("a[href]").forEach { link ->
                if (link.hrefFragmentTarget() == id) {
                    link.normalizeFootnoteReferenceLink(id)
                }
            }

            val item = Element("li").attr("id", id)
            body.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
            definition.remove()
        }
    }

    private fun normalizeWikidotFootnotes(content: Element) {
        val definitions = content.select(".footnotes-footer .footnote-footer[id]").toList()
        if (definitions.isEmpty()) return

        val definitionIds = definitions.map { it.id() }.toSet()
        content.select("sup.footnoteref a, a.footnoteref").forEach { link ->
            val target = link.wikidotFootnoteTarget()
            if (target != null && target in definitionIds) {
                link.normalizeFootnoteReferenceLink(target)
            }
        }

        val section = lazyFootnoteSection(content)
        definitions.forEach { definition ->
            val item = Element("li").attr("id", definition.id())
            val clone = definition.clone()
            clone.select("> a:first-child").remove()
            clone.removeLeadingFootnoteDefinitionPunctuation()
            clone.childNodes().toList().forEach { item.appendChild(it) }
            section.appendChild(item)
        }
        content.select(".footnotes-footer").remove()
    }

    private fun normalizeDhammatalksFootnotes(content: Element) {
        val notes = content.select("div.note").filter { note ->
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
        val cleaned = first.wholeText.trimStart().removePrefix(".").trimStart()
        if (cleaned.isBlank()) {
            first.remove()
        } else {
            first.text(cleaned)
        }
    }

    private fun Element.normalizeFootnoteReferenceLink(targetId: String) {
        attr("href", "#$targetId")
        if (parent()?.normalName() == "sup") return

        val replacement = Element("sup")
        replacement.appendChild(clone())
        replaceWith(replacement)
    }

    private fun Element.replaceWithFootnoteReference(targetId: String) {
        val normalizedLink = clone()
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
        first.text(FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN.replaceFirst(first.wholeText, ""))
    }

    private fun Element.removeTerminalPeriodAfterTrailingLink() {
        val nodes = childNodes()
        val lastTextIndex = nodes.indexOfLast { node ->
            node !is TextNode || node.wholeText.isNotBlank()
        }
        val lastText = nodes.getOrNull(lastTextIndex) as? TextNode ?: return
        if (!TRAILING_LINK_PERIOD_TEXT_PATTERN.matches(lastText.wholeText)) return

        val previousElement = nodes
            .take(lastTextIndex)
            .lastOrNull { node ->
                when (node) {
                    is TextNode -> node.wholeText.isNotBlank()
                    is Element -> true
                    else -> false
                }
            } as? Element
        if (previousElement?.normalName() != "a") return

        lastText.text(lastText.wholeText.replace(Regex("""\.\s*$"""), ""))
    }

    private fun normalizeParagraphFootnoteDefinitions(content: Element) {
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

    private fun normalizeNamedAnchorFootnotes(content: Element) {
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

    private fun normalizeAsideFootnoteLists(content: Element) {
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

    private fun normalizeReferenceFootnoteLists(content: Element) {
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

    private fun normalizeReferenceDivFootnotes(content: Element) {
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

    private fun normalizeFootnoteLists(content: Element) {
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

    private fun normalizeTrailingLooseFootnoteDefinitions(content: Element) {
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

    private fun normalizeLooseFootnoteSections(content: Element) {
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

    private fun lazyFootnoteSection(content: Element): Element {
        content.selectFirst("section[data-footnotes]")?.let { return it }
        return Element("section")
            .attr("data-footnotes", "true")
            .addClass("footnotes")
            .also { content.appendChild(it) }
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

    private fun removeFootnoteDividers(content: Element) {
        content.select("hr").forEach { divider ->
            val parent = divider.parent()
            val isInsideFootnotes = parent?.hasClass("footnotes") == true ||
                parent?.hasAttr("data-footnotes") == true
            if (isInsideFootnotes) {
                if (divider.isFootnoteSeparatorChrome()) {
                    divider.remove()
                }
                return@forEach
            }

            val next = divider.nextElementSibling()
            val followsFootnoteHint = next == null && divider.previousElementSibling()?.hasFootnoteHint() == true
            val isTrailingBeforeFootnotes = followsFootnoteHint ||
                next?.hasAttr("data-footnotes") == true
            if (isTrailingBeforeFootnotes) {
                divider.remove()
            }
        }
    }

    private fun Element.isFootnoteSeparatorChrome(): Boolean = classNames().any {
        it.contains("separator", ignoreCase = true) ||
            it.contains("separatator", ignoreCase = true)
    }

    private fun Element.hasFootnoteHint(): Boolean {
        val hints = "${id()} ${className()} ${attributes().asList().joinToString(" ") { it.value }}".lowercase()
        return "footnote" in hints || "footnotes" in hints || "ftnt" in hints
    }

    private fun Element.isReferenceFootnoteList(): Boolean {
        if (select("> li [id]").isEmpty()) return false
        val previous = previousElementSibling()
        return previous != null &&
            previous.normalName().matches(HEADING_TAG_PATTERN) &&
            FOOTNOTE_HEADING_PATTERN.matches(previous.text().trim())
    }

    private fun String.isParagraphFootnoteId(): Boolean = matches(PARAGRAPH_FOOTNOTE_ID_PATTERN)

    private fun String.isNamedFootnoteDefinitionId(): Boolean = matches(NAMED_FOOTNOTE_DEFINITION_ID_PATTERN)

    private val FOOTNOTE_HEADING_PATTERN = Regex(
        """(?i)^(notes|footnotes|endnotes|sidenotes|references(?:\s+and\s+notes)?)$""",
    )
    private val FOOTNOTE_NUMBER_PATTERN = Regex("""\d{1,4}""")
    private val FOOTNOTE_NUMBER_PREFIX_PATTERN = Regex("""^(\d{1,4})(?:$|[\].):]|\s)""")
    private val FOOTNOTE_NUMBER_DOT_PREFIX_PATTERN = Regex("""^\s*\d{1,4}\.\s*""")
    private val TRAILING_LINK_PERIOD_TEXT_PATTERN = Regex("""^\s*[)\]]?\.\s*$""")
    private val PARAGRAPH_FOOTNOTE_ID_PATTERN = Regex("""(?i)^(?:ftnt|_ftn)\d+$""")
    private val NAMED_FOOTNOTE_DEFINITION_ID_PATTERN = Regex("""(?i)^(?:Footnote|_ftn)\D*\d+$""")
    private val DHAMMATALKS_NOTE_ID_PATTERN = Regex("""(?i).+note\d{1,4}$""")
    private val FOOTNOTE_MARKER_TAGS = setOf("sup", "strong", "b")
    private val wikidotFootnoteRefIdPattern = Regex("""footnoteref-(\d+)""")
    private val wikidotFootnoteScrollTargetPattern = Regex("""scrollToReference\(['"]([^'"]+)['"]\)""")
}
