package com.prof18.klead.internal.extractors.site

import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.standardize.NewsletterLayoutNormalizer

/** Applied only after a newsletter profile has selected its editorial content. */
internal object NewsletterDomNormalizer {
    fun normalize(content: Element) {
        NewsletterLayoutNormalizer.flattenPresentationTables(content)
        (listOf(content) + content.select("*")).distinct().forEach { element ->
            if (element.normalName() in VECTOR_ELEMENTS ||
                element.parents().any { it.normalName() in VECTOR_ELEMENTS }
            ) {
                return@forEach
            }
            normalizeStyle(element)
            if (element.normalName() == "font") element.removeAttr("size")
            LAYOUT_ATTRIBUTES.forEach(element::removeAttr)
            if (element.normalName() !in MEDIA_ELEMENTS) {
                element.removeAttr("width")
                element.removeAttr("height")
            }
        }
    }

    private fun normalizeStyle(element: Element) {
        val semanticStyles = element.attr("style").split(';').mapNotNull { declaration ->
            val property = declaration.substringBefore(':').trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().lowercase().removeSuffix("!important").trim()
            when {
                property == "font-weight" && value in FONT_WEIGHTS -> "$property:$value"
                property == "font-style" && value in FONT_STYLES -> "$property:$value"
                property == "text-decoration" && value in TEXT_DECORATIONS -> "$property:$value"
                else -> null
            }
        }
        element.removeAttr("style")
        if (semanticStyles.isNotEmpty()) element.attr("style", semanticStyles.joinToString(";"))
    }

    private val VECTOR_ELEMENTS = setOf("svg", "math")
    private val MEDIA_ELEMENTS = setOf("img", "video", "svg", "canvas")
    private val LAYOUT_ATTRIBUTES = listOf(
        "bgcolor",
        "color",
        "face",
        "align",
        "valign",
        "border",
        "cellpadding",
        "cellspacing",
    )
    private val FONT_WEIGHTS = setOf("bold", "bolder", "600", "700", "800", "900")
    private val FONT_STYLES = setOf("italic", "oblique")
    private val TEXT_DECORATIONS = setOf("underline", "line-through")
}
