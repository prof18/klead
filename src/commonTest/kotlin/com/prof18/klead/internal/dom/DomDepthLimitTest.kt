package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomDepthLimitTest {
    @Test
    fun `flattens elements below the limit and keeps leaves in document order`() {
        val document = Ksoup.parse(
            "<div><div><div><p>one <b>two</b></p><img src=\"a.jpg\"><span>three</span></div></div></div>",
        )
        document.outputSettings().prettyPrint(false)
        val body = document.body()

        body.limitDepth(maxDepth = 2)

        assertEquals("<div><div>one two<img src=\"a.jpg\">three</div></div>", body.html())
    }

    @Test
    fun `turns a flattened list or table container into a plain block`() {
        val document = Ksoup.parse("<div><ul><li>one</li><li>two</li></ul></div>")
        document.outputSettings().prettyPrint(false)
        val body = document.body()

        body.limitDepth(maxDepth = 2)

        assertEquals("<div><div>onetwo</div></div>", body.html())
    }

    @Test
    fun `leaves trees within the limit untouched`() {
        val html = "<article><h1>Title</h1><ul><li><a href=\"/x\"><em>link</em></a></li></ul></article>"
        val body = Ksoup.parse(html).body()
        val before = body.html()

        body.limitDepth(maxDepth = 6)

        assertEquals(before, body.html())
    }

    @Test
    fun `caps pathological nesting without losing text`() {
        val depth = 10_000
        val html = buildString {
            repeat(depth) { append("<div>") }
            append("deep text")
            repeat(depth) { append("</div>") }
        }
        val body = Ksoup.parse(html).body()

        body.limitDepth()

        // Ksoup stops nesting at 512 levels and appends the rest as empty siblings, which must go too.
        assertTrue(body.maxDepth() <= MAX_DOM_DEPTH)
        assertTrue(body.getAllElements().size <= MAX_DOM_DEPTH + 1)
        assertEquals("deep text", body.text())
    }

    private fun Element.maxDepth(): Int {
        var max = 0
        val pending = ArrayDeque<Pair<Element, Int>>()
        pending.addLast(this to 0)
        while (pending.isNotEmpty()) {
            val (element, depth) = pending.removeLast()
            max = maxOf(max, depth)
            element.children().forEach { pending.addLast(it to depth + 1) }
        }
        return max
    }
}
