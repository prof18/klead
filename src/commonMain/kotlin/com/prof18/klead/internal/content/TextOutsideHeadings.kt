package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.internal.StringUtil
import com.fleeksoft.ksoup.nodes.CDataNode
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.NodeFilter
import com.fleeksoft.ksoup.select.NodeTraversor

// What text() returns for a copy of this element with every h1-h6 below it removed, without copying or
// changing the tree. It follows Ksoup's own text accumulation: whitespace is normalised except under
// whitespace-preserving tags, a space goes before block elements and line breaks, and after a block
// element that is followed by text or an inline element. Removed headings are skipped as if absent,
// both as content and as the "next sibling" that decides that trailing space. A heading that is this
// element itself stays, as removing a copy's root from its document did.
internal fun Element.textOutsideHeadings(): String {
    val root = this
    val accum = StringBuilder()
    NodeTraversor.filter(
        object : NodeFilter {
            override fun head(node: Node, depth: Int): NodeFilter.FilterResult {
                if (node !== root && node.isHeading()) return NodeFilter.FilterResult.SKIP_ENTIRELY
                when (node) {
                    is TextNode -> appendText(accum, node, root)

                    is Element -> {
                        val startsBlock = node.isBlock() || node.nameIs("br")
                        if (accum.isNotEmpty() && startsBlock && !accum.endsWithSpace()) accum.append(' ')
                    }
                }
                return NodeFilter.FilterResult.CONTINUE
            }

            override fun tail(node: Node?, depth: Int): NodeFilter.FilterResult {
                if (node is Element) {
                    // The copy's root had no siblings.
                    val next = if (node === root) null else node.nextSiblingOutsideHeadings()
                    val nextIsText = next is TextNode || (next is Element && next.tag().isInline())
                    if (!node.tag().isInline() && nextIsText && !accum.endsWithSpace()) accum.append(' ')
                }
                return NodeFilter.FilterResult.CONTINUE
            }
        },
        root,
    )
    return accum.toString().trim()
}

private fun appendText(accum: StringBuilder, textNode: TextNode, root: Element) {
    val text = textNode.getWholeText()
    if (textNode is CDataNode || textNode.preservesWhitespaceWithin(root)) {
        accum.append(text)
    } else {
        StringUtil.appendNormalisedWhitespace(accum, text, accum.endsWithSpace())
    }
}

// Ksoup looks at the text's parent and five more ancestors; the copy's ancestors stopped at its root.
private fun TextNode.preservesWhitespaceWithin(root: Element): Boolean {
    var element = parent() as? Element
    var level = 0
    while (element != null && level < PRESERVE_WHITESPACE_LEVELS) {
        if (element.tag().preserveWhitespace()) return true
        if (element === root) return false
        element = element.parent()
        level++
    }
    return false
}

private fun Node.nextSiblingOutsideHeadings(): Node? {
    var next = nextSibling()
    while (next != null && next.isHeading()) next = next.nextSibling()
    return next
}

private fun Node.isHeading(): Boolean = this is Element && normalName() in HEADING_TAGS

private fun StringBuilder.endsWithSpace(): Boolean = isNotEmpty() && this[length - 1] == ' '

private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private const val PRESERVE_WHITESPACE_LEVELS = 6

// Exactly what text() returns, without the root's next-sibling lookup that Ksoup's traversal makes on the
// way out. That lookup reindexes the root's parent whenever a sibling was removed before it, so measuring
// each of many siblings while removing some of them was quadratic. Children are read by index, and the
// sibling that decides a trailing space comes from the traversal's own position.
internal fun Element.textWithoutParentReindex(): String {
    val accum = StringBuilder()
    val elements = ArrayList<Element>()
    val nextIndexes = ArrayList<Int>()
    elements.add(this)
    nextIndexes.add(0)
    while (elements.isNotEmpty()) {
        val last = elements.lastIndex
        val element = elements[last]
        val index = nextIndexes[last]
        if (index < element.childNodeSize()) {
            nextIndexes[last] = index + 1
            when (val child = element.childNode(index)) {
                is TextNode -> appendTextAsKsoup(accum, child)

                is Element -> {
                    val startsBlock = child.isBlock() || child.nameIs("br")
                    if (accum.isNotEmpty() && startsBlock && !accum.endsWithSpace()) accum.append(' ')
                    elements.add(child)
                    nextIndexes.add(0)
                }

                else -> Unit
            }
        } else {
            elements.removeAt(last)
            nextIndexes.removeAt(last)
            // The root's own trailing space would be trimmed, so its siblings are never asked for.
            if (elements.isNotEmpty()) {
                val parent = elements[elements.lastIndex]
                val nextIndex = nextIndexes[nextIndexes.lastIndex]
                val next = if (nextIndex < parent.childNodeSize()) parent.childNode(nextIndex) else null
                val nextIsText = next is TextNode || (next is Element && next.tag().isInline())
                if (!element.tag().isInline() && nextIsText && !accum.endsWithSpace()) accum.append(' ')
            }
        }
    }
    return accum.toString().trim()
}

// Ksoup's rule: the text's parent and five more ancestors decide whether whitespace is kept.
private fun appendTextAsKsoup(accum: StringBuilder, textNode: TextNode) {
    val text = textNode.getWholeText()
    var element = textNode.parent() as? Element
    var level = 0
    var preserve = false
    while (element != null && level < PRESERVE_WHITESPACE_LEVELS) {
        if (element.tag().preserveWhitespace()) {
            preserve = true
            break
        }
        element = element.parent()
        level++
    }
    if (textNode is CDataNode || preserve) {
        accum.append(text)
    } else {
        StringUtil.appendNormalisedWhitespace(accum, text, accum.endsWithSpace())
    }
}
