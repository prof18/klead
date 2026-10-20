package com.prof18.klead.internal

import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.prof18.klead.internal.dom.deepCopy
import com.prof18.klead.internal.dom.isDangerousUrl
import com.prof18.klead.internal.dom.parseFragment

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
            // Copies are inserted in one go: before() per node reindexed the siblings every time, and
            // Ksoup's clone() is quadratic on big subtrees, unlike the iterative cloneElement().
            val copies = segment.childNodes().map { node -> node.deepCopy() }
            template.parent()?.insertChildren(template.siblingIndex(), copies)
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

    private fun Node.isReactBoundaryStart(): Boolean = this is Comment && getData().trim() == "$?"

    private fun Node.isReactBoundaryEnd(): Boolean = this is Comment && getData().trim() == "/$"

    private fun promoteNoscriptImages(document: Document) {
        for (noscript in document.select("noscript").toList()) {
            val fragmentNodes = parseFragment(noscript.html(), document.baseUri())
            val fragmentRoot = Element("fragment")
            fragmentNodes.forEach { fragmentRoot.appendChild(it) }
            val promotedImages = fragmentRoot.select("img[src]").filterNot { image ->
                isDangerousUrl(image.attr("src"))
            }
            // Inserted in one go, tracking what would be the noscript's previous sibling after each insert.
            val promoted = mutableListOf<Node>()
            var previous = noscript.previousElementSibling()
            for (image in promotedImages) {
                val altCaption = noscript.nextJsAltCaptionForPromotedImage(image, previous)
                val copy = image.deepCopy()
                promoted.add(copy)
                previous = copy
                if (altCaption != null) {
                    val caption = Element("span").text(altCaption)
                    promoted.add(caption)
                    previous = caption
                }
            }
            if (promoted.isNotEmpty()) noscript.parent()?.insertChildren(noscript.siblingIndex(), promoted)
        }
    }

    private fun Element.nextJsAltCaptionForPromotedImage(image: Element, previous: Element?): String? {
        if (!image.hasAttr("data-nimg")) return null
        if (parents().any { it.normalName() == "figure" && it.selectFirst("figcaption") != null }) return null

        val placeholder = previous
            ?.takeIf { it.normalName() == "img" && it.hasAttr("data-nimg") }
            ?: return null

        return placeholder.attr("alt").trim().ifBlank { image.attr("alt").trim() }.ifBlank { null }
    }

    private val REACT_STREAM_SEGMENT_CALL = Regex(
        """\${'$'}RC\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""",
    )
}
