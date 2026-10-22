package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node

// Normalizers and renderers walk the DOM recursively. On Kotlin/Native a pathologically nested page
// overflows the small stack of a background thread, which kills the host app instead of throwing.
// Real articles stay far below this depth, so anything deeper is flattened to its leaf nodes.
internal const val MAX_DOM_DEPTH = 64

/** Flattens subtrees nested deeper than [maxDepth] and returns how many were flattened. */
internal fun Element.limitDepth(maxDepth: Int = MAX_DOM_DEPTH): Int {
    val pending = ArrayDeque<Pair<Element, Int>>()
    val flattened = HashSet<Element>()
    pending.addLast(this to 0)
    while (pending.isNotEmpty()) {
        val (element, depth) = pending.removeLast()
        if (depth >= maxDepth) {
            val target = element.outermostTableOrListPart(root = this)
            if (flattened.add(target)) target.flattenToLeaves()
        } else {
            element.children().forEach { child -> pending.addLast(child to depth + 1) }
        }
    }
    return flattened.size
}

// Table and list renderers only read rows, cells and items, so flattening a row or item under a surviving
// table or list would drop its text. The whole table or list structure becomes one plain block instead.
private fun Element.outermostTableOrListPart(root: Element): Element {
    var target = this
    while (true) {
        val parent = target.parent() ?: return target
        if (parent === root || parent.normalName() !in TABLE_AND_LIST_PARTS) return target
        target = parent
    }
}

private fun Element.flattenToLeaves() {
    val leaves = mutableListOf<Node>()
    val pending = ArrayDeque<Node>()
    childNodes().asReversed().forEach(pending::addLast)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        when {
            node !is Element -> leaves.add(node)

            node.childNodeSize() > 0 -> node.childNodes().asReversed().forEach(pending::addLast)

            // Empty wrappers carry no content and would only multiply sibling scans downstream.
            node.tag().isEmpty() || node.normalName() in EMBED_TAGS -> leaves.add(node)
        }
    }
    empty()
    appendChildren(leaves)
    // List and table renderers only read their item and cell children, so bare leaves would be dropped.
    if (normalName() in STRUCTURAL_CONTAINERS) tagName("div")
}

private val TABLE_AND_LIST_PARTS = setOf(
    "ul", "ol", "dl", "menu", "li", "dt", "dd",
    "table", "caption", "thead", "tbody", "tfoot", "tr", "td", "th", "colgroup",
)

private val STRUCTURAL_CONTAINERS =
    setOf("ul", "ol", "dl", "menu", "table", "thead", "tbody", "tfoot", "tr", "colgroup")

private val EMBED_TAGS = setOf("iframe", "video", "audio", "object", "canvas", "svg", "math")
