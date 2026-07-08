package com.prof18.klead.internal

import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.parseFragment
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

// Fixes applied to the raw document before extraction: promotes real images out of <noscript>
// fallbacks (Next.js lazy images) and replays React streaming-SSR segment moves ($RC calls) so
// suspended content sits where the browser would have placed it.
internal object DocumentPreparation {
    fun prepare(document: Document) {
        promoteNoscriptImages(document)
        hydrateReactStreamedSegments(document)
    }

    private fun hydrateReactStreamedSegments(document: Document) {
        val segmentMoves = document.select("script")
            .flatMap { script ->
                REACT_STREAM_SEGMENT_CALL.findAll(script.data()).map { match ->
                    match.groupValues[1] to match.groupValues[2]
                }
            }

        for ((templateId, segmentId) in segmentMoves) {
            val template = document.getElementById(templateId) ?: continue
            val segment = document.getElementById(segmentId) ?: continue
            removeReactFallbackAfter(template)
            segment.childNodes().toList().forEach { node ->
                template.before(node.clone())
            }
            template.remove()
            segment.remove()
        }
    }

    private fun removeReactFallbackAfter(template: Element) {
        var node = template.nextSibling()
        var nestedBoundaryDepth = 0
        while (node != null) {
            val next = node.nextSibling()
            if (node.isReactBoundaryEnd() && nestedBoundaryDepth == 0) return
            if (node.isReactBoundaryStart()) {
                nestedBoundaryDepth++
            } else if (node.isReactBoundaryEnd()) {
                nestedBoundaryDepth--
            }
            node.remove()
            node = next
        }
    }

    private fun Node.isReactBoundaryStart(): Boolean = this is Comment && data.trim() == "$?"

    private fun Node.isReactBoundaryEnd(): Boolean = this is Comment && data.trim() == "/$"

    private fun promoteNoscriptImages(document: Document) {
        for (noscript in document.select("noscript").toList()) {
            val fragmentNodes = parseFragment(noscript.html(), document.baseUri())
            val fragmentRoot = Element("fragment")
            fragmentNodes.forEach { fragmentRoot.appendChild(it) }
            val promotedImages = fragmentRoot.select("img[src]").filterNot { image ->
                isDangerousUrl(image.attr("src"))
            }
            for (image in promotedImages) {
                val altCaption = noscript.nextJsAltCaptionForPromotedImage(image)
                noscript.before(image.clone())
                if (altCaption != null) {
                    noscript.before(Element("span").text(altCaption))
                }
            }
        }
    }

    private fun Element.nextJsAltCaptionForPromotedImage(image: Element): String? {
        if (!image.hasAttr("data-nimg")) return null
        if (parents().any { it.normalName() == "figure" && it.selectFirst("figcaption") != null }) return null

        val placeholder = previousElementSibling()
            ?.takeIf { it.normalName() == "img" && it.hasAttr("data-nimg") }
            ?: return null

        return placeholder.attr("alt").trim().ifBlank { image.attr("alt").trim() }.ifBlank { null }
    }

    private val REACT_STREAM_SEGMENT_CALL = Regex(
        """\${'$'}RC\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""",
    )
}
