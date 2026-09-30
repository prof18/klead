package com.prof18.klead.internal.extractors.site

import com.prof18.klead.fixtures.CommonTestResources
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XProfileTest {
    @Test
    fun `captured software factories article excludes engagement and replies`() {
        assertCapturedArticle("x-building-software-factories", "David")
    }

    @Test
    fun `captured ReaderFlow response without schema excludes engagement and replies`() {
        assertCapturedArticle("x-building-software-factories-no-schema", "David (@dzhng)")
    }

    private fun assertCapturedArticle(fixture: String, author: String) {
        val result = parseHtmlForTest(
            html = CommonTestResources.read("fixtures/regressions/input-html/$fixture.html"),
            url = "https://x.com/dzhng/status/2090252351533973768",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()
        assertEquals("Building software factories (with no slop)", result.metadata.title)
        assertEquals(author, result.metadata.author)
        assertEquals(listOf("x"), result.debug["extractorIds"])
        assertTrue(markdown.startsWith("![Article cover image]"), markdown)
        assertTrue(markdown.contains("\nThe amount of code being written today"), markdown)
        assertTrue(markdown.trimEnd().endsWith("I'm still trying to figure it out like everyone else."), markdown)
        assertTrue(markdown.contains("https://github.com/dzhng/skills"), markdown)
        assertFalse(markdown.contains("best practical ai coding guide"), markdown)
        assertFalse(markdown.lines().any { it.trim() in setOf("49", "83", "852", "2.3K") }, markdown)
        assertEquals(8, Regex("<img\\s").findAll(html).count())
        assertFalse(html.contains("data-engagement-action"), html)
        assertFalse(html.contains("<button"), html)
    }

    @Test
    fun `logged out article keeps prose and real lists without timeline indentation or controls`() {
        val result = parseHtmlForTest(
            html = """
                <main><ul><li><article>
                  <img src="https://example.com/avatar.jpg" alt="Author avatar">
                  <div itemscope itemtype="https://schema.org/Article">
                    <img itemprop="image" src="https://example.com/cover.jpg?name=medium" alt="Article cover">
                    <h1 itemprop="headline">A software factory</h1>
                    <span itemprop="author publisher"><span itemprop="name">David</span></span>
                    <div data-engagement-action="reply"><a href="/example/status/123">49</a></div>
                    <button>852</button>
                    <div class="x-article-body">
                      <style>.x-article-body { padding: 20px; }</style>
                      <p>The article explains how to build software with reliable verification.</p>
                      <ul><li>Keep invariants<ul><li>Check the outputs</li></ul></li><li>Keep decisions</li></ul>
                      <figure><img src="https://example.com/diagram.jpg" alt="Diagram"><figcaption>Factory diagram</figcaption></figure>
                      <p>Read the <a href="//example.com/guide">guide</a> for the next steps.</p>
                    </div>
                    <button>2.3K</button>
                  </div>
                </article></li><li><article><p>Reply content that should be excluded.</p></article></li></ul></main>
            """.trimIndent(),
            url = "https://x.com/example/status/123",
            options = testOptions(debug = true),
        )

        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()
        assertEquals(listOf("x"), result.debug["extractorIds"])
        assertEquals("A software factory", result.metadata.title)
        assertEquals("David", result.metadata.author)
        assertEquals("X (Twitter)", result.metadata.site)
        assertTrue(markdown.startsWith("![Article cover](https://example.com/cover.jpg?name=large)"), markdown)
        assertTrue(markdown.contains("\nThe article explains"), markdown)
        assertFalse(html.contains("<li><img"), html)
        assertTrue(markdown.contains("- Keep invariants\n\t- Check the outputs\n- Keep decisions"), markdown)
        assertTrue(markdown.contains("[guide](https://example.com/guide)"), markdown)
        assertTrue(html.contains("<figcaption>Factory diagram</figcaption>"), html)
        listOf("Reply content", "Author avatar", "49", "852", "2.3K", "padding", "<style").forEach { clutter ->
            assertFalse(markdown.contains(clutter), markdown)
            assertFalse(html.contains(clutter), html)
        }
    }

    @Test
    fun `logged out article markers remain scoped to X domains`() {
        val result = parseHtmlForTest(
            html = """
                <article itemscope itemtype="https://schema.org/Article">
                  <h1 itemprop="headline">Another publisher</h1>
                  <div class="x-article-body"><p>A substantive article paragraph remains readable on this other site.</p></div>
                  <p>This publisher includes additional prose outside that class which must also remain.</p>
                </article>
            """.trimIndent(),
            url = "https://example.com/article",
            options = testOptions(debug = true),
        )

        assertFalse((result.debug["extractorIds"] as? List<*>)?.contains("x") == true)
        assertTrue(result.content.requireMarkdown().contains("additional prose outside that class"))
    }
}
