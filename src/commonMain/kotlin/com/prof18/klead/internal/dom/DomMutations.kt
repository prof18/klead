package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.CDataNode
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

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
    // Ksoup bulk-moves the complete child list without repeatedly shifting source siblings.
    target.appendChildren(childNodes().toList())
}

internal fun Element.appendChildNodesFrom(source: Element) {
    source.childNodes().forEach { appendChild(it.clone()) }
}

internal fun Element.replaceChildrenWith(source: Element) {
    childNodes().toList().forEach(Node::remove)
    appendChildNodesFrom(source)
}

// Ksoup 0.2.6 deep-clones through a MutableList queue with removeAt(0), making wide
// trees quadratic. Shallow-copy each node with a stack instead; retries need an
// independent tree, but do not need to shift the remaining queue for every node.
internal fun Document.cloneDocument(): Document {
    val copy = shallowClone()
    copy.attributes().userData("ksoup.childEls", null)
    copyChildrenTo(copy)
    return copy
}

internal fun Element.cloneElement(): Element {
    if (this is Document) return cloneDocument()
    val copy = copyElement(baseUri())
    // Like Node.clone(), retain independent owner-document settings for serialization
    // and fragment parsing without copying the rest of the source document.
    ownerDocument()?.shallowClone()?.also { document ->
        document.attributes().userData("ksoup.childEls", null)
        document.appendChild(copy)
    }
    copyChildrenTo(copy)
    return copy
}

private fun Element.copyElement(baseUri: String? = null): Element {
    val attributes = attributes().clone()
    attributes.userData("ksoup.childEls", null)
    return Element(tag(), baseUri, attributes)
}

private fun Element.copyChildrenTo(copy: Element) {
    val pending = ArrayDeque<Pair<Element, Element>>()
    pending.addLast(this to copy)
    while (pending.isNotEmpty()) {
        val (source, target) = pending.removeLast()
        for (child in source.childNodes()) {
            val childCopy = if (child is Element) {
                // Copy the stored base URI with the attributes instead of walking up the
                // source ancestors once per node. Child-element caches belong to the old tree.
                child.copyElement()
            } else {
                child.copyLeaf()
            }
            target.appendChild(childCopy)
            if (child is Element && childCopy is Element) {
                pending.addLast(child to childCopy)
            }
        }
    }
}

private fun Node.copyLeaf(): Node {
    // Node.shallowClone() copies an owner document even for every text node. These
    // leaves will immediately belong to the copied tree, which already has its settings.
    val copy = when (this) {
        is CDataNode -> CDataNode(getWholeText())
        is TextNode -> TextNode(getWholeText())
        is DataNode -> DataNode(getWholeData())
        is Comment -> Comment(getData())
        else -> return shallowClone()
    }
    if (hasAttributes()) {
        val sourceAttributes = attributes()
        copy.attributes().addAll(sourceAttributes)
        if (sourceAttributes.hasUserData()) {
            copy.attributes().userData().putAll(sourceAttributes.userData())
        }
    }
    return copy
}

internal fun parseFragment(html: String, baseUri: String): List<Node> = Ksoup.parseBodyFragment(html, baseUri)
    .body()
    .childNodes()
    .toList()
