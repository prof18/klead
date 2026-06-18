package dev.defuddle.dom

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class DomMutationsTest {
    @Test
    fun `removeSafely removes attached element and ignores detached element`() {
        val document = Jsoup.parse("""<article><aside>Clutter</aside><p>Keep</p></article>""")
        val aside = document.selectFirst("aside") ?: error("missing aside")

        aside.removeSafely()
        Element("div").removeSafely()

        assertNull(document.selectFirst("aside"))
        assertEquals("Keep", document.selectFirst("article")?.text())
    }

    @Test
    fun `unwrapSafely preserves child order`() {
        val document = Jsoup.parse("""<article>Before <span id="wrap">one <em>two</em> three</span> after</article>""")
        val wrapper = document.selectFirst("#wrap") ?: error("missing wrapper")

        wrapper.unwrapSafely()

        assertEquals("""Before one <em>two</em> three after""", document.selectFirst("article")?.innerHtmlStable())
    }

    @Test
    fun `replaceWithChildren preserves text and element nodes`() {
        val document = Jsoup.parse("""<article>A <div id="replace">one <strong>two</strong> three</div> B</article>""")
        val wrapper = document.selectFirst("#replace") ?: error("missing wrapper")

        wrapper.replaceWithChildren()

        assertEquals("""A one <strong>two</strong> three B""", document.selectFirst("article")?.innerHtmlStable())
    }

    @Test
    fun `transferChildrenTo moves children in order`() {
        val document = Jsoup.parse(
            """<article><div id="source">one <em>two</em></div><div id="target">zero </div></article>""",
        )
        val source = document.selectFirst("#source") ?: error("missing source")
        val target = document.selectFirst("#target") ?: error("missing target")

        source.transferChildrenTo(target)

        assertEquals("", source.innerHtmlStable())
        assertEquals("""zero one <em>two</em>""", target.innerHtmlStable())
    }

    @Test
    fun `replaceChildrenWith uses cloned source children`() {
        val document = Jsoup.parse(
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
        val document = Jsoup.parse("""<article><p>Original</p></article>""", "https://example.com/base/")

        val clone = document.cloneDocument()
        clone.selectFirst("p")?.text("Changed")

        assertEquals("Original", document.selectFirst("p")?.text())
        assertEquals("Changed", clone.selectFirst("p")?.text())
        assertEquals(document.baseUri(), clone.baseUri())
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
