package com.prof18.klead.internal.standardize

import com.prof18.klead.internal.dom.textTrimmedOrNull
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

// Converts callout-like markup — Obsidian-style [!type] blockquotes, Bootstrap alerts, and
// admonition blocks — into the uniform .callout/data-callout structure the writer understands.
internal object HtmlCalloutNormalizer {
    fun normalizeCallouts(content: Element) {
        normalizeStructuredCallouts(content)
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

    private fun normalizeStructuredCallouts(content: Element) {
        content.select(".alert").forEach { alert ->
            if (alert.hasAttr("data-callout")) return@forEach
            val type = alert.classNames()
                .firstOrNull { it.startsWith("alert-") && it != "alert" }
                ?.removePrefix("alert-")
                ?.lowercase()
                ?: "note"
            val titleElement = alert.selectFirst(".alert-heading, .alert-title")
            val title = titleElement?.textTrimmedOrNull() ?: type.replaceFirstChar { it.uppercase() }
            val bodyNodes = alert.childNodes().filterNot { it === titleElement }.toList()
            alert.rebuildCallout(type, title, bodyNodes)
        }

        content.select(".admonition").forEach { admonition ->
            if (admonition.hasAttr("data-callout")) return@forEach
            val type = admonition.classNames()
                .firstOrNull { it !in ADMONITION_CLASS_BLACKLIST }
                ?.lowercase()
                ?: "note"
            val titleElement = admonition.selectFirst(".admonition-title, .details-summary")
            val title = titleElement?.ownText()?.trim()?.ifBlank {
                titleElement.text().trim()
            }?.ifBlank { null } ?: type.replaceFirstChar { it.uppercase() }
            val contentElement = admonition.selectFirst(".admonition-content, .details-content")
            val bodyNodes = contentElement?.childNodes()?.toList()
                ?: admonition.childNodes().filterNot { it === titleElement }.toList()
            admonition.rebuildCallout(type, title, bodyNodes)
        }

        content.select(".callout").forEach { callout ->
            if (!callout.hasAttr("data-callout")) {
                val type = callout.classNames()
                    .firstOrNull { it.startsWith("callout-") && it != "callout" }
                    ?.removePrefix("callout-")
                    ?.lowercase()
                if (type != null) callout.attr("data-callout", type)
            }
        }
    }

    private fun Element.rebuildCallout(type: String, title: String, bodyNodes: List<Node>) {
        bodyNodes.forEach(Node::remove)
        empty()
        tagName("div")
        addClass("callout")
        attr("data-callout", type)
        appendElement("div")
            .addClass("callout-title")
            .appendElement("div")
            .addClass("callout-title-inner")
            .text(title)
        val body = appendElement("div").addClass("callout-content")
        bodyNodes.forEach { body.appendChild(it) }
    }

    private val ADMONITION_CLASS_BLACKLIST = setOf(
        "admonition",
        "details",
        "open",
    )

    private val CALLOUT_MARKER = Regex("""\[!(\w+)]""")
}
