package com.prof18.klead.internal.removal

import com.prof18.klead.RemovalRecord
import org.jsoup.nodes.Element

internal object HiddenElementRemoval {
    fun apply(content: Element, debug: MutableList<RemovalRecord>) {
        for (element in content.descendantsSnapshot()) {
            if (!element.isAttachedTo(content)) continue
            val reason = removableHiddenReason(element) ?: continue
            element.orphanedHeadingBeforeHiddenBlock()?.let { heading ->
                recordAndRemove(heading, debug, "removeHiddenElements", null, "orphan heading before hidden block")
            }
            recordAndRemove(element, debug, "removeHiddenElements", hiddenSelector(element), reason)
        }
    }

    private fun removableHiddenReason(element: Element): String? {
        val reason = hiddenReason(element) ?: return null
        val isProtectedHidden = element.isRenderableAriaHiddenSvg() ||
            element.isMathWrapper() ||
            element.isWithinCalloutLike() ||
            element.isWithinFootnoteLike()

        return reason.takeUnless { isProtectedHidden }
    }

    private fun Element.orphanedHeadingBeforeHiddenBlock(): Element? {
        val heading = previousElementSibling()
            ?.takeIf { it.normalName().matches(HEADING_TAG_PATTERN) }
            ?: return null

        var sibling = nextElementSibling()
        while (sibling != null) {
            if (sibling.normalName().matches(HEADING_TAG_PATTERN)) {
                return if (sibling.headingLevel() <= heading.headingLevel()) heading else null
            }
            if (removableHiddenReason(sibling) != null || !sibling.hasSubstantiveContent()) {
                sibling = sibling.nextElementSibling()
                continue
            }
            return null
        }
        return heading
    }

    private fun hiddenReason(element: Element): String? {
        val style = element.attr("style").lowercase().replace(" ", "")
        val classes = element.classNames()
        if (element.hasResponsiveDisplayVisualFallback(classes)) return null

        return when {
            element.hasAttr("hidden") -> "hidden attribute"
            element.attr("aria-hidden").equals("true", ignoreCase = true) -> "aria-hidden"
            "display:none" in style -> "display:none"
            "visibility:hidden" in style -> "visibility:hidden"
            OPACITY_ZERO_STYLE_PATTERN.containsMatchIn(style) -> "opacity:0"
            classes.any { it.isHiddenClass() } -> "hidden class"
            else -> null
        }
    }

    private fun String.isHiddenClass(): Boolean = this == "hidden" ||
        this == "invisible" ||
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
            selectFirst("math, annotation[encoding*=tex]") != null ||
            "math" in className ||
            "katex" in className ||
            "mathjax" in className
    }

    private fun Element.isWithinCalloutLike(): Boolean = generateSequence(this) { it.parent() }
        .any {
            val hints = partialHaystack(it)
            "callout" in hints || "admonition" in hints || "alert" in hints
        }

    private fun Element.isWithinFootnoteLike(): Boolean = generateSequence(this) { it.parent() }
        .any {
            val hints = partialHaystack(it)
            "footnote" in hints ||
                "footnotes" in hints ||
                "fnref" in hints ||
                "ftnt" in hints ||
                "fna-content" in hints ||
                "data-definition" in hints
        }

    private fun hiddenSelector(element: Element): String = when {
        element.id().isNotBlank() -> "#${element.id()}"
        element.className().isNotBlank() -> "${element.tagName()}.${element.classNames().joinToString(".")}"
        else -> element.tagName()
    }

    private fun Element.hasSubstantiveContent(): Boolean = text().trim().isNotBlank() ||
        select("img, picture, figure, table, pre, code, math, p, h1, h2, h3, h4, h5, h6").isNotEmpty()

    private fun Element.headingLevel(): Int = normalName().removePrefix("h").toIntOrNull() ?: Int.MAX_VALUE

    private fun Element.isAttachedTo(root: Element): Boolean = this === root || parents().any { it === root }

    private fun partialHaystack(element: Element): String = elementHintHaystack(element)

    private val OPACITY_ZERO_STYLE_PATTERN = Regex("""(?:^|;)opacity:0(?:\.0+)?(?:;|$)""")
    private val HEADING_TAG_PATTERN = Regex("""h[1-6]""")
    private val RESPONSIVE_DISPLAY_CLASS_PATTERN = Regex(
        """(?:sm|md|lg|xl|2xl):(?:block|inline|inline-block|flex|inline-flex|grid|table|contents)""",
    )
}
