package com.prof18.klead.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FixtureHarnessTest {
    @Test
    fun `loader discovers upstream html fixtures`() {
        val cases = FixtureLoader.loadAll()

        assertEquals(190, cases.size)
        assertEquals("author-contact-block", cases.first().name)
        assertTrue(cases.all { it.rawHtml.isNotBlank() })
        assertTrue(cases.all { it.expectedMarkdown != null })
    }

    @Test
    fun `loader extracts source url from frontmatter comment`() {
        val url = FixtureLoader.extractUrl(
            fixtureName = "custom",
            html = """<!-- {"url":"https://example.com/article?x=1"} --><article></article>""",
        )

        assertEquals("https://example.com/article?x=1", url)
    }

    @Test
    fun `loader falls back to filename derived url when frontmatter is missing`() {
        val url = FixtureLoader.extractUrl(
            fixtureName = "general--example.com-path-to-article",
            html = "<html></html>",
        )

        assertEquals("https://example.com-path-to-article", url)
    }

    @Test
    fun `loader handles malformed frontmatter safely`() {
        val url = FixtureLoader.extractUrl(
            fixtureName = "broken-frontmatter",
            html = """<!-- {"url":  --><html></html>""",
        )

        assertEquals("https://broken-frontmatter", url)
    }

    @Test
    fun `expected loader parses json preamble and markdown body`() {
        val expected = ExpectedResultLoader.load("general--stephango.com-buy-wisely")

        assertNotNull(expected)
        assertEquals("Buy wisely", expected.metadata["title"])
        assertEquals("Steph Ango", expected.metadata["author"])
        assertEquals("Steph Ango", expected.metadata["site"])
        assertEquals("2023-09-30T00:00:00+00:00", expected.metadata["published"])
        assertTrue(expected.markdownBody.startsWith("Whenever I buy things"))
        assertFalse(expected.markdownBody.startsWith("```json"))
    }

    @Test
    fun `expected loader supports files without preamble`() {
        val expected = ExpectedResultLoader.parse("Plain body\n\nSecond paragraph")

        assertEquals(emptyMap(), expected.metadata)
        assertEquals("Plain body\n\nSecond paragraph", expected.markdownBody)
    }

    @Test
    fun `normalization keeps whitespace differences explicit`() {
        val normalized = MarkdownNormalizer.minimal("Line one\r\nLine two  \n\n")

        assertEquals("Line one\nLine two", normalized)
    }
}
