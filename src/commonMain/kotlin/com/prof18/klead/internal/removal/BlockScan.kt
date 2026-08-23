package com.prof18.klead.internal.removal

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.fleeksoft.ksoup.select.Elements
import com.fleeksoft.ksoup.select.NodeTraversor
import com.fleeksoft.ksoup.select.NodeVisitor

// Marks the elements of a subtree whose own subtrees are too large to be the compact chrome
// (footers, bylines, tag lists, promos) the removal predicates target. The text cap sits above
// every predicate length guard (max 7000, skeleton blocks), so for guard-carrying predicates
// skipping capped blocks is provably equivalent; the handful of pattern-first predicates could
// only diverge on multi-kilobyte blocks that fully read as UI chrome, which we deliberately
// never remove. The element cap bounds work on adversarially nested markup. Everything is
// computed in one bottom-up pass, so the per-candidate check is a map lookup. Compute once per
// removal pass and discard: counts reflect the tree at computation time.
internal class ChromeBlockCaps private constructor(private val exceeded: java.util.IdentityHashMap<Element, Boolean>) {
    fun exceeds(element: Element): Boolean = exceeded[element] ?: false

    private class Frame {
        var elements = 0
        var nonWhitespaceChars = 0
    }

    companion object {
        private const val MAX_TEXT_CHARS = 8_192
        private const val MAX_ELEMENTS = 1_500

        fun compute(root: Element): ChromeBlockCaps {
            val exceeded = java.util.IdentityHashMap<Element, Boolean>()
            val frames = ArrayDeque<Frame>()
            NodeTraversor.traverse(
                object : NodeVisitor {
                    override fun head(node: Node, depth: Int) {
                        when (node) {
                            is Element -> frames.addLast(Frame())

                            is TextNode -> frames.lastOrNull()?.let { frame ->
                                frame.nonWhitespaceChars += node.getWholeText().count { !it.isWhitespace() }
                            }

                            else -> Unit
                        }
                    }

                    override fun tail(node: Node, depth: Int) {
                        if (node !is Element) return
                        val frame = frames.removeLast()
                        frame.elements++
                        exceeded[node] = frame.elements > MAX_ELEMENTS || frame.nonWhitespaceChars > MAX_TEXT_CHARS
                        frames.lastOrNull()?.let { parent ->
                            parent.elements += frame.elements
                            parent.nonWhitespaceChars += frame.nonWhitespaceChars
                        }
                    }
                },
                root,
            )
            return ChromeBlockCaps(exceeded)
        }
    }
}

// Caches the expensive per-element lookups (subtree text, hint haystacks, link lists) shared by
// the clutter predicates, so a predicate chain walks the subtree once instead of once per
// predicate. Scans must not outlive DOM mutations: create one per candidate element, evaluate,
// then discard before anything is removed or moved.
internal class BlockScan(val element: Element) {
    val text: String by lazy(LazyThreadSafetyMode.NONE) { element.text() }
    val trimmedText: String by lazy(LazyThreadSafetyMode.NONE) { text.trim() }
    val collapsedText: String by lazy(LazyThreadSafetyMode.NONE) { trimmedText.collapseWhitespace() }
    val haystack: String by lazy(LazyThreadSafetyMode.NONE) { elementHintHaystack(element) }

    // Matches the historical `select("*")` haystack join, which includes the element itself.
    val subtreeHints: String by lazy(LazyThreadSafetyMode.NONE) {
        element.select("*").joinToString(" ") { elementHintHaystack(it) }
    }
    val selfAndSubtreeHints: String by lazy(LazyThreadSafetyMode.NONE) { "$haystack $subtreeHints" }
    val hrefLinks: Elements by lazy(LazyThreadSafetyMode.NONE) { element.select("a[href]") }
}
