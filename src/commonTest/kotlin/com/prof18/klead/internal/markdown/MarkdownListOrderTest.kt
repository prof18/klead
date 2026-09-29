package com.prof18.klead.internal.markdown

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownListOrderTest {
    @Test
    fun `text after a nested list stays after it`() {
        val document = Ksoup.parse(
            "<article><ul><li>Before<ul><li>Nested</li></ul>After</li></ul></article>",
            "https://example.com/",
        )
        val article = requireNotNull(document.selectFirst("article"))

        assertEquals(
            "- Before\n\t- Nested\n\n  After\n",
            KleadMarkdownWriter.write(article, "https://example.com/"),
        )
    }

    @Test
    fun `ordered item keeps alternating text and nested lists in source order`() {
        val document = Ksoup.parse(
            "<article><ol start='3'><li>Before<ul><li>First</li></ul>Between<ol><li>Second</li></ol>After</li></ol></article>",
            "https://example.com/",
        )
        val article = requireNotNull(document.selectFirst("article"))

        assertEquals(
            "3. Before\n\t- First\n\n   Between\n\t1. Second\n\n   After\n",
            KleadMarkdownWriter.write(article, "https://example.com/"),
        )
    }
}
