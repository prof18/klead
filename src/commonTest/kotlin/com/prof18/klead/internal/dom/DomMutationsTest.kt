package com.prof18.klead.internal.dom

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.CDataNode
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class DomMutationsTest {
    @Test
    fun `removeSafely removes attached element and ignores detached element`() {
        val document = Ksoup.parse("""<article><aside>Clutter</aside><p>Keep</p></article>""")
        val aside = document.selectFirst("aside") ?: error("missing aside")

        aside.removeSafely()
        Element("div").removeSafely()

        assertNull(document.selectFirst("aside"))
        assertEquals("Keep", document.selectFirst("article")?.text())
    }

    @Test
    fun `unwrapSafely preserves child order`() {
        val document = Ksoup.parse("""<article>Before <span id="wrap">one <em>two</em> three</span> after</article>""")
        val wrapper = document.selectFirst("#wrap") ?: error("missing wrapper")

        wrapper.unwrapSafely()

        assertEquals("""Before one <em>two</em> three after""", document.selectFirst("article")?.innerHtmlStable())
    }

    @Test
    fun `replaceWithChildren preserves text and element nodes`() {
        val document = Ksoup.parse("""<article>A <div id="replace">one <strong>two</strong> three</div> B</article>""")
        val wrapper = document.selectFirst("#replace") ?: error("missing wrapper")

        wrapper.replaceWithChildren()

        assertEquals("""A one <strong>two</strong> three B""", document.selectFirst("article")?.innerHtmlStable())
    }

    @Test
    fun `transferChildrenTo moves children in order and invalidates cached children`() {
        val document = Ksoup.parse(
            """<article><div id="source">one <em>two</em><!--three--></div><div id="target"><b>zero</b> </div></article>""",
        )
        val source = document.selectFirst("#source") ?: error("missing source")
        val target = document.selectFirst("#target") ?: error("missing target")
        val movedNodes = source.childNodes().toList()
        val originalTargetSize = target.childNodeSize()
        source.children()
        target.children()

        source.transferChildrenTo(target)

        assertEquals("", source.innerHtmlStable())
        assertEquals(0, source.childrenSize())
        assertEquals("""<b>zero</b> one <em>two</em><!--three-->""", target.innerHtmlStable())
        assertEquals(listOf("b", "em"), target.children().map(Element::normalName))
        movedNodes.forEachIndexed { index, node ->
            assertSame(node, target.childNode(originalTargetSize + index))
            assertSame(target, node.parent())
            assertEquals(originalTargetSize + index, node.siblingIndex())
        }
        movedNodes[1].remove()
        assertEquals("""<b>zero</b> one <!--three-->""", target.innerHtmlStable())
    }

    @Test
    fun `replaceChildrenWith uses cloned source children`() {
        val document = Ksoup.parse(
            """<article><div id="source">one <em>two</em></div><div id="target"><p>old</p></div></article>""",
        )
        val source = document.selectFirst("#source") ?: error("missing source")
        val target = document.selectFirst("#target") ?: error("missing target")

        target.replaceChildrenWith(source)

        assertEquals("""one <em>two</em>""", source.innerHtmlStable())
        assertEquals("""one <em>two</em>""", target.innerHtmlStable())
        assertNotSame(source.selectFirst("em"), target.selectFirst("em"))
    }

    @Test
    fun `cloneDocument returns independent document copy`() {
        val document = Ksoup.parse("""<article><p>Original</p></article>""", "https://example.com/base/")

        val clone = document.cloneDocument()
        clone.selectFirst("p")?.text("Changed")

        assertEquals("Original", document.selectFirst("p")?.text())
        assertEquals("Changed", clone.selectFirst("p")?.text())
        assertEquals(document.baseUri(), clone.baseUri())
    }

    @Test
    fun `cloneDocument preserves mixed nodes and independent attributes and settings`() {
        val document = Ksoup.parse(
            """
            <!doctype html><html><head><script>{"value":1}</script></head>
                <body><!--comment--><article id="original">Before <b>bold</b> after<img src="image.png"></article></body>
            </html>
            """.trimIndent(),
            "https://example.com/base/",
        )
        document.outputSettings().prettyPrint(false)
        val originalArticle = document.selectFirst("article") ?: error("missing article")
        originalArticle.children() // Prime Ksoup's element-child cache before copying.

        val copy = document.cloneDocument()

        assertEquals(document.outerHtml(), copy.outerHtml())
        assertNotSame(originalArticle.child(0), copy.selectFirst("article")?.child(0))
        assertEquals("https://example.com/base/image.png", copy.selectFirst("img")?.absUrl("src"))
        copy.selectFirst("article")?.attr("id", "changed")
        copy.outputSettings().prettyPrint(true)
        assertEquals("original", document.selectFirst("article")?.id())
        assertEquals(false, document.outputSettings().prettyPrint())
    }

    @Test
    fun `cloneElement matches ordinary clone and retains independent DOM settings and base URIs`() {
        val document = Ksoup.parse(
            """<article id="original">Before <b>bold</b><!--comment--><script>{"value":1}</script>
                <svg viewBox="0 0 10 10"><path d="M0 0 L10 10"></path></svg><img src="image.png"></article>""",
            "https://example.com/base/",
        )
        document.outputSettings().prettyPrint(false)
        val source = document.selectFirst("article") ?: error("missing article")
        document.children()
        source.selectFirst("b")?.setBaseUri("https://example.com/override/")
        source.children() // Prime the cache on the copied root as well as descendants.
        source.selectFirst("svg")?.children()

        val copy = source.cloneElement()

        assertEquals(source.clone().outerHtml(), copy.outerHtml())
        assertEquals(source.baseUri(), copy.baseUri())
        assertEquals("https://example.com/override/", copy.selectFirst("b")?.baseUri())
        assertEquals("https://example.com/base/image.png", copy.selectFirst("img")?.absUrl("src"))
        assertNotSame(source.ownerDocument(), copy.ownerDocument())
        assertSame(copy, copy.ownerDocument()?.child(0))
        assertSame(copy, copy.child(0).parent())
        copy.child(0).text("Changed")
        copy.attr("id", "changed")
        copy.ownerDocument()?.outputSettings()?.prettyPrint(true)
        assertEquals("bold", source.child(0).text())
        assertEquals("original", source.id())
        assertEquals(false, document.outputSettings().prettyPrint())
    }

    @Test
    fun `cloneElement copies wide detached trees in order with independent child caches`() {
        val source = Element("article").apply { setBaseUri("https://example.com/base/") }
        repeat(5_000) { index ->
            source.appendElement("p").attr("data-index", index.toString()).text("Paragraph $index")
        }
        source.children()

        val copy = source.cloneElement()

        assertNull(copy.parent())
        assertEquals(source.outerHtml(), copy.outerHtml())
        assertEquals(5_000, copy.childrenSize())
        assertEquals("https://example.com/base/", copy.child(4_999).baseUri())
        copy.child(4_999).attr("data-index", "changed")
        assertEquals("4999", source.child(4_999).attr("data-index"))
    }

    @Test
    fun `cloneElement preserves CDATA and independently copies attributed leaves`() {
        val source = Element("article")
        val text = TextNode("Before ").also { it.attr("data-test", "original") }
        text.attributes().userData("test", "original")
        source.appendChild(text)
        source.appendChild(Comment("comment").also { it.attr("data-test", "comment") })
        source.appendChild(CDataNode("<tag>&value").also { it.attr("data-test", "cdata") })
        source.appendElement("script").appendChild(DataNode("{\"value\":1}").also { it.attr("data-test", "data") })

        val copy = source.cloneElement()

        assertEquals(source.outerHtml(), copy.outerHtml())
        assertEquals("#cdata", copy.childNode(2).nodeName())
        assertEquals("cdata", copy.childNode(2).attr("data-test"))
        assertEquals("data", copy.child(0).childNode(0).attr("data-test"))
        copy.childNode(0).attr("data-test", "changed")
        copy.childNode(0).attributes().userData("test", "changed")
        assertEquals("original", text.attr("data-test"))
        assertEquals("original", text.attributes().userData("test"))
    }

    @Test
    fun `parseFragment handles malformed html`() {
        val nodes = parseFragment("""Before <p>Open <strong>bold</p> After""", "https://example.com")

        assertEquals(
            listOf("Before", "p", "strong"),
            nodes.map {
                when (it) {
                    is TextNode -> it.text().trim()
                    is Element -> it.tagLower()
                    else -> it.nodeName()
                }
            }.filter { it.isNotBlank() },
        )
        assertEquals("https://example.com", nodes.first().baseUri())
    }
}
