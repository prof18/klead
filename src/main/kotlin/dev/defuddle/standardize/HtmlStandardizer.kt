package dev.defuddle.standardize

import dev.defuddle.dom.replaceWithChildren
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

object HtmlStandardizer {
    fun apply(
        content: Element,
        title: String?,
    ) {
        normalizeCallouts(content)
        normalizeHeadings(content, title)
        normalizeCodeBlocks(content)
        normalizeImages(content)
        normalizeFootnotes(content)
        normalizeTables(content)
        removeEmptyWrappers(content)
    }

    private fun normalizeHeadings(
        content: Element,
        title: String?,
    ) {
        val firstHeading = content.selectFirst("h1, h2")
        if (title != null && firstHeading?.text()?.trim().equals(title.trim(), ignoreCase = true)) {
            firstHeading?.remove()
        }
        content.select("h1, h2, h3, h4, h5, h6").forEach { heading ->
            heading.select("a[href^=#].anchor, a[href^=#].permalink, a[href^=#][aria-hidden=true]").remove()
        }
    }

    private fun normalizeCodeBlocks(content: Element) {
        content.select("pre").forEach { pre ->
            pre.select(".lineno, .line-number, .line-numbers-rows, [aria-hidden=true]").remove()
            val code = pre.selectFirst("code") ?: Element("code").also { code ->
                code.text(pre.text())
                pre.empty()
                pre.appendChild(code)
            }
            val language = languageFrom(pre) ?: languageFrom(code)
            if (language != null) {
                code.attr("data-lang", language)
                code.addClass("language-$language")
            }
        }
        content.select("code > pre").forEach { pre ->
            pre.parent()?.replaceWith(pre)
        }
    }

    private fun normalizeImages(content: Element) {
        content.select("img").forEach { image ->
            val replacement = firstAttr(image, "data-src", "data-original", "data-lazy-src", "data-url")
            if (replacement != null && isPlaceholderImage(image.attr("src"))) {
                image.attr("src", replacement)
            }
            firstAttr(image, "data-srcset", "data-lazy-srcset")?.let { image.attr("srcset", it) }
        }
    }

    private fun normalizeCallouts(content: Element) {
        content.select("blockquote").forEach { blockquote ->
            val firstParagraph = blockquote.selectFirst("p") ?: return@forEach
            val marker = CALLOUT_MARKER.matchEntire(firstParagraph.text().trim()) ?: return@forEach
            val type = marker.groupValues[1].lowercase()
            firstParagraph.remove()
            blockquote.tagName("div")
            blockquote.addClass("callout")
            blockquote.attr("data-callout", type)

            val existingChildren = blockquote.childNodes().toList()
            blockquote.empty()
            blockquote.appendElement("div")
                .addClass("callout-title")
                .appendElement("div")
                .addClass("callout-title-inner")
                .text(type.replaceFirstChar { it.uppercase() })
            val body = blockquote.appendElement("div").addClass("callout-content")
            existingChildren.forEach { body.appendChild(it) }
        }
    }

    private fun normalizeFootnotes(content: Element) {
        content.select("ol.footnotes, ol[id*=footnote], ol[id*=fn]").forEach { list ->
            if (list.parent()?.hasAttr("data-footnotes") == true) return@forEach
            val section = Element("section").attr("data-footnotes", "true").addClass("footnotes")
            list.before(section)
            section.appendChild(list)
        }
    }

    private fun normalizeTables(content: Element) {
        content.select("table").forEach { table ->
            val cells = table.select("td, th")
            if (table.hasClass("layout") || cells.size == 1) {
                val nodes = cells.firstOrNull()?.childNodes()?.toList().orEmpty()
                nodes.forEach { table.before(it) }
                table.remove()
            }
        }
    }

    private fun removeEmptyWrappers(content: Element) {
        content.select("span, div").toList().asReversed().forEach { element ->
            if (element.children().isEmpty() && element.text().isBlank()) {
                element.remove()
            } else if (element.tagName() == "span" && element.attributes().isEmpty()) {
                element.replaceWithChildren()
            }
        }
        content.childNodes().filterIsInstance<TextNode>().forEach { text ->
            if (text.text().isBlank()) text.remove()
        }
    }

    private fun languageFrom(element: Element): String? {
        val attrs = listOf(element.className(), element.attr("data-lang"), element.attr("data-language"))
        for (attr in attrs) {
            LANGUAGE_REGEX.find(attr)?.let { return it.groupValues[1].lowercase() }
            attr.takeIf { it.isNotBlank() && it.length <= 24 && it.all { char -> char.isLetterOrDigit() || char in "+#_-" } }
                ?.let { return it.lowercase() }
        }
        return null
    }

    private fun firstAttr(
        element: Element,
        vararg names: String,
    ): String? =
        names.firstNotNullOfOrNull { name -> element.attr(name).trim().ifBlank { null } }

    private fun isPlaceholderImage(src: String): Boolean =
        src.isBlank() ||
            src.startsWith("data:image/svg", ignoreCase = true) ||
            src.startsWith("data:image/gif", ignoreCase = true)

    private val LANGUAGE_REGEX = Regex("""(?:^|\s)language-([A-Za-z0-9_+#-]+)(?:\s|$)""")
    private val CALLOUT_MARKER = Regex("""\[!(\w+)]""")
}
