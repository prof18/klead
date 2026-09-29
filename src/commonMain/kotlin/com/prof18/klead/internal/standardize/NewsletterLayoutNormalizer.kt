package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.nodes.Element

/**
 * Structural and conservative style cleanup for email-style layout.
 *
 * The default flattening mechanics serve the newsletter profiles. [normalize] is the generic fallback for unknown
 * templates: it never deletes content, keeps unknown styling, and leaves code, vector artwork and real data tables alone.
 */
internal object NewsletterLayoutNormalizer {
    /** Whether [content] holds a presentation table that [normalize] would flatten. */
    fun hasEligiblePresentationTable(content: Element): Boolean =
        content.select(PRESENTATION_TABLE).any { isEligible(it, content) }

    /** Generic cleanup for unknown newsletter templates. */
    fun normalize(content: Element) {
        flattenPresentationTables(content, preserveContentMarkup = true)
        (listOf(content) + content.select("*")).distinct().forEach { element ->
            if (isProtected(element, content)) return@forEach
            LAYOUT_ATTRIBUTES.forEach(element::removeAttr)
            val isMedia = element.normalName() in PROTECTED_MEDIA
            if (!isMedia) {
                element.removeAttr("width")
                element.removeAttr("height")
            }
            removeLayoutStyles(element, isMedia)
        }
    }

    fun flattenPresentationTables(content: Element, preserveContentMarkup: Boolean = false) {
        if (preserveContentMarkup) {
            content.select(PRESENTATION_TABLE).toList()
                .filter { isEligible(it, content) }
                .asReversed()
                .forEach(::flattenPreservingMarkup)
        } else {
            flattenForProfiles(content)
        }
    }

    private fun flattenForProfiles(content: Element) {
        content.select(PRESENTATION_TABLE).toList().asReversed().forEach { table ->
            table.children().toList().forEach { child ->
                when (child.normalName()) {
                    "thead", "tbody", "tfoot" -> {
                        child.children().filter { it.normalName() == "tr" }.forEach(::flattenRow)
                        child.unwrap()
                    }

                    "tr" -> flattenRow(child)
                }
            }
            table.tagName("div")
            table.removeAttr("role")
        }
        (listOf(content) + content.select("*")).distinct().forEach { element ->
            if (element.normalName() in VECTOR_ELEMENTS ||
                element.parents().any { it.normalName() in VECTOR_ELEMENTS }
            ) {
                return@forEach
            }
            if (element.normalName() in TABLE_PARTS && element.parents().none { it.normalName() == "table" }) {
                element.tagName("div")
            }
        }
    }

    private fun flattenRow(row: Element) {
        row.children().filter { it.normalName() == "td" || it.normalName() == "th" }.forEach { cell ->
            cell.tagName("div")
            cell.removeAttr("colspan")
            cell.removeAttr("rowspan")
        }
        row.unwrap()
    }

    private fun flattenPreservingMarkup(table: Element) {
        table.children().toList().forEach { child ->
            when (child.normalName()) {
                "thead", "tbody", "tfoot" -> {
                    child.children().filter { it.normalName() == "tr" }.forEach(::convertRow)
                    child.tagName("div")
                }

                "tr" -> convertRow(child)

                "caption" -> child.tagName("div")
            }
        }
        table.tagName("div")
        table.removeAttr("role")
    }

    private fun convertRow(row: Element) {
        row.children().filter { it.normalName() == "td" || it.normalName() == "th" }.forEach { cell ->
            cell.tagName("div")
            cell.removeAttr("colspan")
            cell.removeAttr("rowspan")
        }
        row.tagName("div")
    }

    private fun isEligible(table: Element, content: Element): Boolean = !isProtected(table, content)

    /** Code, vector artwork, real (non-presentation) data tables and `col`/`colgroup` tables, including everything inside them. */
    private fun isProtected(element: Element, content: Element): Boolean {
        var current: Element? = element
        while (current != null) {
            if (current.isProtectedContainer()) return true
            if (current === content) break
            current = current.parent()
        }
        return false
    }

    private fun Element.isProtectedContainer(): Boolean = when (normalName()) {
        "pre", "code", "svg", "math" -> true

        "table" -> !attr("role").trim().equals("presentation", ignoreCase = true) ||
            children().any { it.normalName() == "col" || it.normalName() == "colgroup" }

        else -> false
    }

    private fun removeLayoutStyles(element: Element, isMedia: Boolean) {
        if (!element.hasAttr("style")) return
        val declarations = CssDeclarations.split(element.attr("style"))
        val retained = declarations.filterNot { declaration ->
            val property = CssDeclarations.withoutComments(declaration).substringBefore(':').trim().lowercase()
            property in LAYOUT_PROPERTIES || property.startsWith("border-") ||
                (!isMedia && property in NON_MEDIA_SIZE_PROPERTIES)
        }
        if (retained.size == declarations.size) return
        if (retained.isEmpty()) element.removeAttr("style") else element.attr("style", retained.joinToString("; "))
    }

    private const val PRESENTATION_TABLE = "table[role=presentation]"
    private val TABLE_PARTS = setOf("thead", "tbody", "tfoot", "tr", "td", "th")
    private val VECTOR_ELEMENTS = setOf("svg", "math")
    private val PROTECTED_MEDIA = setOf("img", "picture", "video", "audio", "iframe", "canvas", "svg", "math")
    private val LAYOUT_ATTRIBUTES = listOf("align", "valign", "border", "cellpadding", "cellspacing")
    private val NON_MEDIA_SIZE_PROPERTIES = setOf(
        "width",
        "min-width",
        "max-width",
        "height",
        "min-height",
        "max-height",
    )
    private val LAYOUT_PROPERTIES = setOf(
        "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
        "margin-block", "margin-block-start", "margin-block-end",
        "margin-inline", "margin-inline-start", "margin-inline-end",
        "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
        "padding-block", "padding-block-start", "padding-block-end",
        "padding-inline", "padding-inline-start", "padding-inline-end",
        "border", "box-shadow",
    )
}
