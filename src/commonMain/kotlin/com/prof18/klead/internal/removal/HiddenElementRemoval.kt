package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.QueryParser
import com.prof18.klead.RemovalRecord
import com.prof18.klead.internal.dom.isAttachedTo
import com.prof18.klead.internal.dom.removeAllBatched

internal object HiddenElementRemoval {
    fun apply(content: Element, debug: MutableList<RemovalRecord>, checkCancelled: () -> Unit = {}) {
        // Removing a hidden block re-exposes the same heading to the next one; its answer cannot change
        // within this pass, so each heading is scanned once instead of once per hidden sibling.
        val checkedHeadings = HashSet<Element>()
        // Removed in one batch at the end; until then, scheduled elements count as already gone.
        val scheduled = ScheduledRemovals()
        // Each hidden element asks about all of its ancestors; their hints cannot change in this pass
        // (removals are batched), so each ancestor's haystack is built once.
        val ancestorHints = HashMap<Element, AncestorHints>()
        for (element in content.descendantsSnapshot()) {
            checkCancelled()
            if (!element.isAttachedTo(content) || scheduled.containsSelfOrAncestor(element)) continue
            val reason = removableHiddenReason(element, ancestorHints) ?: continue
            element.orphanedHeadingBeforeHiddenBlock(checkedHeadings, scheduled, ancestorHints)?.let { heading ->
                recordRemoval(heading, debug, "removeHiddenElements", null, "orphan heading before hidden block")
                scheduled.add(heading)
            }
            recordRemoval(element, debug, "removeHiddenElements", hiddenSelector(element), reason)
            scheduled.add(element)
        }
        removeAllBatched(scheduled.elements)
    }

    // Elements waiting for the batched removal. Each remembers the nearest earlier sibling that is not
    // scheduled, so "previous sibling as if removed" jumps over whole runs instead of walking them.
    private class ScheduledRemovals {
        private val previousKept = HashMap<Element, Element?>()
        val elements: Set<Element> get() = previousKept.keys

        fun add(element: Element) {
            previousKept[element] = previousKeptSibling(element)
        }

        fun containsSelfOrAncestor(element: Element): Boolean =
            element in previousKept || element.parents().any { it in previousKept }

        fun previousKeptSibling(element: Element): Element? {
            var previous = element.previousElementSibling()
            while (previous != null && previous in previousKept) previous = previousKept[previous]
            return previous
        }
    }

    private class AncestorHints(val isFixed: Boolean, val isCallout: Boolean, val isFootnote: Boolean)

    private fun MutableMap<Element, AncestorHints>.of(element: Element): AncestorHints = getOrPut(element) {
        val hints = partialHaystack(element)
        AncestorHints(
            // Floating notifications are UI, even when their names contain "alert".
            isFixed = element.hasClass("fixed") ||
                "position:fixed" in element.attr("style").lowercase().replace(" ", ""),
            isCallout = "callout" in hints || "admonition" in hints || "alert" in hints,
            isFootnote = "footnote" in hints ||
                "footnotes" in hints ||
                "fnref" in hints ||
                "ftnt" in hints ||
                "fna-content" in hints ||
                "data-definition" in hints,
        )
    }

    private fun removableHiddenReason(element: Element, ancestorHints: MutableMap<Element, AncestorHints>): String? {
        val reason = hiddenReason(element) ?: return null
        val isProtectedHidden = element.isRenderableAriaHiddenSvg() ||
            element.isMathWrapper() ||
            element.isWithinCalloutLike(ancestorHints) ||
            element.isWithinFootnoteLike(ancestorHints)

        return reason.takeUnless { isProtectedHidden }
    }

    private fun Element.orphanedHeadingBeforeHiddenBlock(
        checkedHeadings: MutableSet<Element>,
        scheduled: ScheduledRemovals,
        ancestorHints: MutableMap<Element, AncestorHints>,
    ): Element? {
        val heading = scheduled.previousKeptSibling(this)
            ?.takeIf { it.normalName().matches(HEADING_TAG_PATTERN) && checkedHeadings.add(it) }
            ?: return null

        var sibling = nextElementSibling()
        var scanned = 0
        while (sibling != null) {
            if (++scanned > MAX_FOLLOWING_SIBLING_SCAN) return null
            if (sibling.normalName().matches(HEADING_TAG_PATTERN)) {
                return if (sibling.headingLevel() <= heading.headingLevel()) heading else null
            }
            if (removableHiddenReason(sibling, ancestorHints) != null || !sibling.hasSubstantiveContent()) {
                sibling = sibling.nextElementSibling()
                continue
            }
            return null
        }
        return heading
    }

    private fun hiddenReason(element: Element): String? {
        // An element without attributes has no hidden markers at all; skipping early avoids the
        // style/class string work below for the vast majority of elements.
        if (element.attributesSize() == 0) return null
        val classes = if (element.hasAttr("class")) element.classNames() else emptySet()
        if (element.hasResponsiveDisplayVisualFallback(classes)) return null

        if (element.hasAttr("hidden")) return "hidden attribute"
        if (element.attr("aria-hidden").equals("true", ignoreCase = true)) return "aria-hidden"
        if (element.hasAttr("style")) {
            val style = element.attr("style").lowercase().replace(" ", "")
            if ("display:none" in style) return "display:none"
            if ("visibility:hidden" in style) return "visibility:hidden"
            if (OPACITY_ZERO_STYLE_PATTERN.containsMatchIn(style)) return "opacity:0"
        }
        if (classes.any { it.isHiddenClass() }) return "hidden class"
        return null
    }

    // `mfp-hide` is Magnific Popup's always-`display:none` class for inline popup sources such as
    // login or newsletter modals that themes park inside the article.
    private fun String.isHiddenClass(): Boolean = this == "hidden" ||
        this == "invisible" ||
        this == "mfp-hide" ||
        endsWith(":hidden") ||
        endsWith(":invisible")

    private fun Element.hasResponsiveDisplayVisualFallback(classes: Set<String>): Boolean = "hidden" in classes &&
        classes.any { RESPONSIVE_DISPLAY_CLASS_PATTERN.matches(it) } &&
        select("img, picture, button, input, form").isEmpty() &&
        select("svg").any { it.hasRenderableSvgContent() }

    private fun Element.isRenderableAriaHiddenSvg(): Boolean = normalName() == "svg" &&
        attr("aria-hidden").equals("true", ignoreCase = true) &&
        hasRenderableSvgContent()

    private fun Element.hasRenderableSvgContent(): Boolean = text().isNotBlank() ||
        select("circle, ellipse, line, path, polygon, polyline, rect, text").size > 1

    private fun Element.isMathWrapper(): Boolean {
        val className = className().lowercase()
        return tagName().equals("math", ignoreCase = true) ||
            selectFirst(MATH_CONTENT_EVALUATOR) != null ||
            "math" in className ||
            "katex" in className ||
            "mathjax" in className
    }

    private fun Element.isWithinCalloutLike(ancestorHints: MutableMap<Element, AncestorHints>): Boolean {
        var hasCalloutHint = false
        for (ancestor in generateSequence(this) { it.parent() }) {
            val hints = ancestorHints.of(ancestor)
            if (hints.isFixed) return false
            hasCalloutHint = hasCalloutHint || hints.isCallout
        }
        return hasCalloutHint
    }

    private fun Element.isWithinFootnoteLike(ancestorHints: MutableMap<Element, AncestorHints>): Boolean =
        generateSequence(this) { it.parent() }.any { ancestorHints.of(it).isFootnote }

    private fun hiddenSelector(element: Element): String = when {
        element.id().isNotBlank() -> "#${element.id()}"
        element.className().isNotBlank() -> "${element.tagName()}.${element.classNames().joinToString(".")}"
        else -> element.tagName()
    }

    private fun Element.headingLevel(): Int = normalName().removePrefix("h").toIntOrNull() ?: Int.MAX_VALUE

    private fun partialHaystack(element: Element): String = elementHintHaystack(element)

    private val OPACITY_ZERO_STYLE_PATTERN = Regex("""(?:^|;)opacity:0(?:\.0+)?(?:;|$)""")

    // Checked for every hidden element and its following siblings; parse the selector once.
    private val MATH_CONTENT_EVALUATOR = QueryParser.parse("math, annotation[encoding*=tex]")

    private val HEADING_TAG_PATTERN = Regex("""h[1-6]""")
    private val RESPONSIVE_DISPLAY_CLASS_PATTERN = Regex(
        """(?:sm|md|lg|xl|2xl):(?:block|inline|inline-block|flex|inline-flex|grid|table|contents)""",
    )
}
