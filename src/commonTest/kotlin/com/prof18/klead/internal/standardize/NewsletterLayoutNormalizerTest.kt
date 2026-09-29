package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewsletterLayoutNormalizerTest {
    private fun article(html: String): Element = Ksoup.parse("<article>$html</article>").selectFirst("article")!!

    @Test
    fun `flattens nested presentation tables preserving order and attributes`() {
        val content = article(
            """
            <table role="presentation" id="outer"><tbody id="body"><tr id="row1" dir="rtl"><td colspan="2" id="c1" lang="it">First</td>
            <td><table role="presentation"><tr><td>Second</td></tr><tr><td>Third</td></tr></table></td></tr>
            <tr><td>Last</td></tr></tbody></table>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals("First Second Third Last", content.text())
        assertTrue(content.select("table, tr, td, tbody").isEmpty())
        assertFalse(content.outerHtml().contains("role="))
        assertEquals("div", content.selectFirst("#outer")!!.normalName())
        assertEquals("div", content.selectFirst("#body")!!.normalName())
        assertEquals("rtl", content.selectFirst("#row1")!!.attr("dir"))
        val cell = content.selectFirst("#c1")!!
        assertEquals("it", cell.attr("lang"))
        assertFalse(cell.hasAttr("colspan"))
    }

    @Test
    fun `flattens a presentation root table`() {
        val content = Ksoup.parse("<table role=\"presentation\"><tr><td>A</td><td>B</td></tr></table>")
            .selectFirst("table")!!

        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals("div", content.normalName())
        assertEquals("A B", content.text())
    }

    @Test
    fun `keeps captions and mixed children of flattened tables`() {
        val content = article(
            """<table role="presentation"><caption>Cap</caption><tr><td>Cell</td></tr></table>""",
        )

        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals("Cap Cell", content.text())
        assertTrue(content.select("caption").isEmpty())
    }

    @Test
    fun `preserves ordinary data tables including spans and fragment ids`() {
        val content = article(
            """
            <table role="presentation"><tr id="row"><td><a href="#row">jump</a>
            <table><caption>Scores</caption><tr><th colspan="2">Team</th></tr><tr><td rowspan="2">A</td><td>1</td></tr></table>
            </td></tr></table>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals(1, content.select("table").size)
        assertEquals("2", content.selectFirst("th")!!.attr("colspan"))
        assertEquals("2", content.selectFirst("td[rowspan]")!!.attr("rowspan"))
        assertNotNull(content.selectFirst("caption"))
        assertNotNull(content.selectFirst("div#row"))
        assertEquals("#row", content.selectFirst("a")!!.attr("href"))
    }

    @Test
    fun `does not flatten presentation tables inside protected subtrees`() {
        val content = article(
            """
            <pre><table role="presentation"><tr><td>code</td></tr></table></pre>
            <table><tr><td><table role="presentation"><tr><td>nested</td></tr></table></td></tr></table>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals(2, content.select("table[role=presentation]").size)
        assertFalse(NewsletterLayoutNormalizer.hasEligiblePresentationTable(content))
    }

    @Test
    fun `protected root is left alone`() {
        val root = Ksoup.parse(
            "<table><tr><td><table role=\"presentation\"><tr><td>x</td></tr></table></td></tr></table>",
        )
            .selectFirst("table")!!

        assertFalse(NewsletterLayoutNormalizer.hasEligiblePresentationTable(root))
        NewsletterLayoutNormalizer.normalize(root)

        assertEquals(2, root.select("table").size)
    }

    @Test
    fun `skips presentation tables containing col structures`() {
        val content = article(
            """<table role="presentation"><colgroup><col width="50%"></colgroup><tr><td>x</td></tr></table>""",
        )

        assertFalse(NewsletterLayoutNormalizer.hasEligiblePresentationTable(content))
        NewsletterLayoutNormalizer.flattenPresentationTables(content, preserveContentMarkup = true)

        assertEquals(1, content.select("table").size)
        assertEquals(1, content.select("colgroup col").size)
    }

    @Test
    fun `normalize leaves col tables untouched next to an eligible table`() {
        val colTable = """<table role="presentation" border="1" cellpadding="3"><colgroup><col width="50%"></colgroup><tr><td style="padding:2px">x</td></tr></table>"""
        val content = article("""<table role="presentation"><tr><td>y</td></tr></table>$colTable""")

        NewsletterLayoutNormalizer.normalize(content)

        assertEquals(1, content.select("table").size)
        val table = content.selectFirst("table")!!
        assertEquals("1", table.attr("border"))
        assertEquals("3", table.attr("cellpadding"))
        assertEquals("50%", table.selectFirst("col")!!.attr("width"))
        assertEquals("padding:2px", table.selectFirst("td")!!.attr("style"))
    }

    @Test
    fun `activation requires an eligible presentation table`() {
        assertFalse(NewsletterLayoutNormalizer.hasEligiblePresentationTable(article("<p>Hello</p>")))
        assertFalse(
            NewsletterLayoutNormalizer.hasEligiblePresentationTable(article("<table><tr><td>x</td></tr></table>")),
        )
        assertTrue(
            NewsletterLayoutNormalizer.hasEligiblePresentationTable(
                article("<table role=\"presentation\"><tr><td>x</td></tr></table>"),
            ),
        )
    }

    @Test
    fun `removes layout properties and legacy attributes but keeps unknown and semantic styles`() {
        val content = article(
            """
            <div style="MARGIN: 0; Padding-Left: 4px; border-top: 1px solid; box-shadow: 0 0 1px #000; width: 600px; max-width: 100%; height: 10px;
              white-space: pre-wrap; display: block; visibility: visible; font: italic 12px/1 serif; text-decoration: underline; --border-x: 3px; z-index: 2"
              align="center" valign="top" border="1" cellpadding="3" cellspacing="0" width="600" height="20" id="keep" class="k" dir="rtl" lang="it">
            <p>Text</p></div>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.normalize(content)

        val div = content.selectFirst("div")!!
        assertEquals(
            "white-space: pre-wrap; display: block; visibility: visible; font: italic 12px/1 serif; " +
                "text-decoration: underline; --border-x: 3px; z-index: 2",
            div.attr("style"),
        )
        listOf("align", "valign", "border", "cellpadding", "cellspacing", "width", "height").forEach {
            assertFalse(div.hasAttr(it), it)
        }
        assertEquals("keep", div.id())
        assertEquals("k", div.className())
        assertEquals("rtl", div.attr("dir"))
        assertEquals("it", div.attr("lang"))
    }

    @Test
    fun `removes the style attribute when nothing remains and keeps untouched strings verbatim`() {
        val content = article("""<p style="margin:0;padding:0">a</p><p style="Color : red;  z-index:1">b</p>""")

        NewsletterLayoutNormalizer.normalize(content)

        val paragraphs = content.select("p")
        assertFalse(paragraphs[0].hasAttr("style"))
        assertEquals("Color : red;  z-index:1", paragraphs[1].attr("style"))
    }

    @Test
    fun `handles comments quoted delimiters and important in declarations`() {
        val content = article(
            """<p style="mar/**/gin: 0 !important; content: 'a;margin:0'; /* x */ padding /* c */: 3px !IMPORTANT; background: url(a;b.png)">t</p>""",
        )

        NewsletterLayoutNormalizer.normalize(content)

        assertEquals(
            "mar/**/gin: 0 !important; content: 'a;margin:0'; background: url(a;b.png)",
            content.selectFirst("p")!!.attr("style"),
        )
    }

    @Test
    fun `protects media sizing but removes their spacing`() {
        val content = article(
            """
            <img src="a.jpg" srcset="a.jpg 1x, b.jpg 2x" alt="Alt" width="640" height="480" style="width:640px;height:480px;margin:4px;border:1px solid">
            <video width="10" height="10" style="max-width:100%"></video>
            <iframe width="560" height="315" src="https://example.com/e"></iframe>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.normalize(content)

        val image = content.selectFirst("img")!!
        assertEquals("640", image.attr("width"))
        assertEquals("480", image.attr("height"))
        assertEquals("width:640px;height:480px", image.attr("style").replace(" ", "").replace(";;", ";"))
        assertEquals("a.jpg", image.attr("src"))
        assertEquals("a.jpg 1x, b.jpg 2x", image.attr("srcset"))
        assertEquals("Alt", image.attr("alt"))
        assertEquals("max-width:100%", content.selectFirst("video")!!.attr("style"))
        assertEquals("560", content.selectFirst("iframe")!!.attr("width"))
    }

    @Test
    fun `leaves pre code and vector subtrees untouched`() {
        val html = """<pre style="margin:0" width="9"><code style="padding:1px">x</code></pre>""" +
            """<svg style="margin:0" width="10"><path style="border:1px"/></svg>""" +
            """<math style="margin:0"><mi style="padding:0">x</mi></math>"""
        val content = article(html)
        val before = content.html()

        NewsletterLayoutNormalizer.normalize(content)

        assertEquals(before, content.html())
    }

    @Test
    fun `leaves real data tables untouched`() {
        val html = """<table border="1" cellpadding="3" style="width:100%;border:1px solid"><tr><th align="left" style="padding:2px">A</th></tr></table>"""
        val content = article(html)
        val before = content.html()

        NewsletterLayoutNormalizer.normalize(content)

        assertEquals(before, content.html())
    }

    @Test
    fun `normalization is idempotent and keeps links and text order`() {
        val content = article(
            """
            <table role="presentation" style="width:600px"><tr><td style="padding:10px" align="center">
            <a href="https://example.com/x" style="margin:0;color:red">Read</a> <b>more</b></td></tr></table>
            """.trimIndent(),
        )

        NewsletterLayoutNormalizer.normalize(content)
        val once = content.outerHtml()
        NewsletterLayoutNormalizer.normalize(content)

        assertEquals(once, content.outerHtml())
        assertEquals("Read more", content.text())
        assertEquals("https://example.com/x", content.selectFirst("a")!!.attr("href"))
        assertEquals("color:red", content.selectFirst("a")!!.attr("style"))
    }

    @Test
    fun `default flattening keeps the profile mechanics`() {
        val content = article(
            """<table role="presentation"><thead><tr><th>H</th></tr></thead><tbody><tr id="r"><td>C</td></tr></tbody></table>""",
        )

        NewsletterLayoutNormalizer.flattenPresentationTables(content)

        assertEquals("H C", content.text())
        assertTrue(content.select("table, thead, tbody, tr, th, td").isEmpty())
        assertTrue(content.select("#r").isEmpty())
    }
}
