package com.prof18.klead.internal.dom

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

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

internal fun Document.cloneDocument(): Document = clone()

internal fun parseFragment(html: String, baseUri: String): List<Node> = Jsoup.parseBodyFragment(html, baseUri)
    .body()
    .childNodes()
    .toList()
