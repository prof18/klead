package com.prof18.klead.internal.content

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.prof18.klead.internal.dom.cloneElement
import kotlin.test.Test
import kotlin.test.assertEquals

// The body word count used to copy the content, drop its headings and read text(); retry decisions depend
// on that count, so the read-only traversal must give the same text.
class TextOutsideHeadingsTest {
    @Test
    fun `matches the copy without headings on assorted shapes`() {
        listOf(
            "<article><p>One two</p><h2>Skipped heading</h2><p>three</p></article>",
            "<article>foo<h2>x</h2>bar</article>",
            "<article><span>left</span><h3>gone</h3><span>right</span> tail</article>",
            "<article><div><h1>Outer <h2>inner</h2> rest</h1>after</div>text</article>",
            "<article><p>a<br>b</p><div>block</div>inline <b>bold</b><h4>h</h4><i>it</i></article>",
            "<article><pre>  keep   spacing\n  here </pre><h2>t</h2><pre>more   </pre></article>",
            "<article><ul><li>one</li><li><h5>x</h5>two</li></ul>   spaced    text  </article>",
            "<article><table><tr><td>cell</td><td><h6>h</h6></td></tr></table>after</article>",
            "<article><p>no headings here</p> at all</article>",
            "<article></article>",
        ).forEach { html -> assertSameAsCopy(Ksoup.parse(html).selectFirst("article")!!, html) }
    }

    @Test
    fun `a heading root keeps its own text`() {
        val html = "<div><h2>Root heading <h3>nested</h3> text</h2><p>sibling</p></div>"
        assertSameAsCopy(Ksoup.parse(html).selectFirst("h2")!!, html)
    }

    @Test
    fun `a root inside a pre does not inherit its whitespace handling`() {
        val html = "<pre><span>  spaced   words  <h2>x</h2> end </span></pre>"
        assertSameAsCopy(Ksoup.parse(html).selectFirst("span")!!, html)
    }

    @Test
    fun `text without parent reindex matches text`() {
        val document = Ksoup.parse(
            "<article><p>a<br>b <b>bold</b></p><div>block</div>inline<pre>  keep  </pre>" +
                "<ul><li>one</li><li>two</li></ul>tail<span> x </span><table><tr><td>c</td></tr></table></article>",
        )
        document.getAllElements().forEach { element ->
            assertEquals(element.text(), element.textWithoutParentReindex(), element.outerHtml())
        }
    }

    private fun assertSameAsCopy(root: Element, html: String) {
        val before = root.outerHtml()
        val copy = root.cloneElement()
        copy.select("h1, h2, h3, h4, h5, h6").remove()

        assertEquals(copy.text(), root.textOutsideHeadings(), html)
        assertEquals(before, root.outerHtml(), "the tree changed for $html")
    }
}
