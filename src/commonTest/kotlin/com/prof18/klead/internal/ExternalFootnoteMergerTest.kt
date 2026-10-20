package com.prof18.klead.internal

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalFootnoteMergerTest {
    @Test
    fun `merges an external footnote block once`() {
        val document = Ksoup.parse(
            "<main><p>Body<sup>1</sup></p></main>" +
                "<section class=\"footnotes\"><p><sup>1</sup> The note.</p></section>",
        )
        val content = document.selectFirst("main")!!

        ExternalFootnoteMerger.merge(document, content)

        assertEquals(1, content.select("section.footnotes").size)
        assertEquals(1, Regex("The note").findAll(content.text()).count())
    }

    @Test
    fun `merges only the outermost of nested footnote blocks`() {
        val document = Ksoup.parse(
            "<main><p>Body<sup>1</sup></p></main>" +
                "<section class=\"footnotes\"><div class=\"footnotes-list\">" +
                "<div class=\"footnote\"><p><sup>1</sup> The note.</p></div></div></section>",
        )
        val content = document.selectFirst("main")!!

        ExternalFootnoteMerger.merge(document, content)

        assertEquals(1, Regex("The note").findAll(content.text()).count())
    }

    @Test
    fun `never merges a footnote block that wraps the content`() {
        val document = Ksoup.parse(
            "<div class=\"footnotes-wrapper\"><main><p>Body<sup>1</sup></p></main>" +
                "<p><sup>1</sup> The note.</p></div>",
        )
        val content = document.selectFirst("main")!!

        ExternalFootnoteMerger.merge(document, content)

        assertEquals(1, Regex("Body").findAll(content.text()).count())
    }
}
