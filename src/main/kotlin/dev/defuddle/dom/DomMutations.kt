package dev.defuddle.dom

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

fun Element.removeSafely() {
    if (parent() != null) {
        remove()
    }
}

fun Element.unwrapSafely() {
    replaceWithChildren()
}

fun Element.replaceWithChildren() {
    if (parent() == null) return
    val nodes = childNodes().toList()
    for (node in nodes) {
        before(node)
    }
    remove()
}

fun Element.transferChildrenTo(target: Element) {
    val nodes = childNodes().toList()
    for (node in nodes) {
        target.appendChild(node)
    }
}

fun Element.replaceChildrenWith(source: Element) {
    childNodes().toList().forEach(Node::remove)
    source.childNodes().forEach { appendChild(it.clone()) }
}

fun Document.cloneDocument(): Document = clone()

fun parseFragment(
    html: String,
    baseUri: String,
): List<Node> =
    Jsoup.parseBodyFragment(html, baseUri)
        .body()
        .childNodes()
        .toList()
