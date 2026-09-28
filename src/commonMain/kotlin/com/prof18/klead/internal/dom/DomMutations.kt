package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node

internal fun Element.removeSafely() {
    if (parent() != null) {
        remove()
    }
}

internal fun Element.unwrapSafely() {
    replaceWithChildren()
}

internal fun Element.replaceWithChildren() {
    if (parent() == null) return
    val nodes = childNodes().toList()
    for (node in nodes) {
        before(node)
    }
    remove()
}

internal fun Element.transferChildrenTo(target: Element) {
    val nodes = childNodes().toList()
    for (node in nodes) {
        target.appendChild(node)
    }
}

internal fun Element.appendChildNodesFrom(source: Element) {
    source.childNodes().forEach { appendChild(it.clone()) }
}

internal fun Element.replaceChildrenWith(source: Element) {
    childNodes().toList().forEach(Node::remove)
    appendChildNodesFrom(source)
}

// Ksoup 0.2.6 deep-clones through a MutableList queue with removeAt(0), making wide
// documents quadratic. Shallow-copy each node with a stack instead; retries need an
// independent tree, but do not need to shift the remaining queue for every node.
internal fun Document.cloneDocument(): Document {
    val copy = shallowClone()
    val pending = ArrayDeque<Pair<Element, Element>>()
    pending.addLast(this to copy)
    while (pending.isNotEmpty()) {
        val (source, target) = pending.removeLast()
        for (child in source.childNodes()) {
            val childCopy = if (child is Element) {
                // Copy the stored base URI with the attributes instead of walking up the
                // source ancestors once per node. Child-element caches belong to the old tree.
                val attributes = child.attributes().clone()
                attributes.userData("ksoup.childEls", null)
                Element(child.tag(), null, attributes)
            } else {
                child.shallowClone()
            }
            target.appendChild(childCopy)
            if (child is Element && childCopy is Element) {
                pending.addLast(child to childCopy)
            }
        }
    }
    return copy
}

internal fun parseFragment(html: String, baseUri: String): List<Node> = Ksoup.parseBodyFragment(html, baseUri)
    .body()
    .childNodes()
    .toList()
