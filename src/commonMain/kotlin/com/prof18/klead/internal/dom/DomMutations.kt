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

// Removes many elements with one rebuild per parent. Element.remove() shifts and reindexes the parent's
// children each time, so removing thousands of siblings one by one is quadratic.
internal fun removeAllBatched(elements: Collection<Element>) {
    for ((parent, removed) in elements.groupBy { it.parent() }) {
        if (parent == null) continue
        val removedSet = removed.toHashSet()
        val kept = parent.childNodes().filterNot { it in removedSet }
        parent.empty()
        parent.appendChildren(kept)
    }
}

// Collects removals, replacements and unwraps of many child nodes and applies each parent's share with a single
// rebuild of its child list. Ksoup reindexes a parent's children (and falls back to a linear search in remove())
// after every single removal or insert, so editing thousands of siblings one at a time is quadratic.
// Edits to a parent's children become visible when that parent is flushed; callers that inspect a parent's
// children or siblings must flush it first or ask isRemoved().
internal class ChildListBatch {
    private class Edit(val replacement: List<Node>, val unwrapped: Element?)

    private val pending = LinkedHashMap<Element, MutableMap<Node, Edit>>()

    fun remove(node: Node) = edit(node, Edit(emptyList(), null))

    fun replace(node: Node, replacement: Node) = edit(node, Edit(listOf(replacement), null))

    fun replace(node: Node, replacement: List<Node>) = edit(node, Edit(replacement, null))

    fun unwrap(element: Element) {
        flush(element)
        edit(element, Edit(element.childNodes().toList(), element))
    }

    fun isRemoved(node: Node): Boolean {
        val parent = node.parent() as? Element ?: return true
        return pending[parent]?.get(node)?.replacement?.isEmpty() == true
    }

    fun flush(parent: Element) {
        val edits = pending.remove(parent) ?: return
        val rebuilt = ArrayList<Node>(parent.childNodeSize() + edits.size)
        for (child in parent.childNodes()) {
            val edit = edits[child]
            if (edit == null) {
                rebuilt.add(child)
            } else {
                // Detach the unwrapped element's children in one go so appending them does not
                // remove them from it one by one.
                edit.unwrapped?.empty()
                rebuilt.addAll(edit.replacement)
            }
        }
        parent.empty()
        parent.appendChildren(rebuilt)
    }

    fun flushAll() {
        while (pending.isNotEmpty()) flush(pending.keys.first())
    }

    private fun edit(node: Node, edit: Edit) {
        val parent = node.parent() as? Element ?: return
        pending.getOrPut(parent) { HashMap() }[node] = edit
    }
}

internal fun Element.unwrapSafely() {
    replaceWithChildren()
}

internal fun Element.replaceWithChildren() {
    val parent = parent() ?: return
    // One bulk insert: before() per child shifts and reindexes the parent's children each time.
    parent.insertChildren(siblingIndex(), childNodes().toList())
    remove()
}

internal fun Element.transferChildrenTo(target: Element) {
    // Ksoup bulk-moves the complete child list without repeatedly shifting source siblings.
    target.appendChildren(childNodes().toList())
}

internal fun Element.appendChildNodesFrom(source: Element) {
    source.childNodes().forEach { appendChild(it.deepCopy()) }
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

// Ksoup's Node.clone() is quadratic in subtree size; these copy iteratively with the same result.
internal fun Element.deepCopy(): Element = cloneElement()

internal fun Node.deepCopy(): Node = (this as? Element)?.cloneElement() ?: clone()

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
