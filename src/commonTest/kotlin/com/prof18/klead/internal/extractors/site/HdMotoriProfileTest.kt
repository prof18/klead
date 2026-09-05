package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HdMotoriProfileTest {
    @Test
    fun `keeps article media and introduction while removing publisher controls`() {
        val result = parseHtmlForTest(pageHtml(), "https://www.hdmotori.it/story/")
        val markdown = result.content.requireMarkdown()
        val html = result.content.requireHtml()

        assertEquals("Racing history", result.metadata.title)
        assertEquals("Example Author", result.metadata.author)
        assertTrue(markdown.contains("Introduction to the season"))
        assertTrue(markdown.contains("The opening paragraph explains"))
        assertTrue(markdown.contains("The final paragraph describes"))
        assertTrue(markdown.contains("[source](https://example.com/source)"))
        assertTrue(html.contains("https://example.com/hero.jpg"))
        assertTrue(html.contains("https://example.com/race.jpg"))
        assertTrue(markdown.contains("Race photograph"))
        assertFalse(markdown.contains("Publisher controls"))
        assertFalse(markdown.contains("Fonte preferita su Google"))
        assertFalse(markdown.contains("Related story"))
        assertFalse(html.contains("<svg"))
    }

    @Test
    fun `cleanup is domain scoped and missing article container falls back`() {
        val unrelated = parseHtmlForTest(pageHtml(), "https://example.com/story/")
        assertTrue(unrelated.content.requireMarkdown().contains("Fonte preferita su Google"))

        val fallback = parseHtmlForTest(
            pageHtml().replace("article_content", "story-body"),
            "https://hdmotori.it/story/",
        )
        assertTrue(fallback.content.requireMarkdown().contains("The final paragraph describes"))
    }

    private fun pageHtml() = """
        <html><head><title>Racing history</title><meta name="author" content="Example Author"></head>
        <body><main><article>
          <section class="article_head"><h1>Racing history</h1><p>Introduction to the season</p></section>
          <section class="article_content">
            <div class="hero_image"><img src="https://example.com/hero.jpg">
              <div>Publisher controls <svg viewBox="0 0 24 24"><path d="M0 0h24v24H0z"/></svg></div>
            </div>
            <div><p>Publisher controls</p><section class="share_buttons"><svg viewBox="0 0 24 24"></svg></section></div>
            <div class="post_content">
              <div class="discover_button">Fonte preferita su Google</div>
              <p>The opening paragraph explains the history of the racing season with enough detail to establish the main article and its context.</p>
              <figure><img src="https://example.com/race.jpg"><figcaption>Race photograph</figcaption></figure>
              <p>The final paragraph describes the championship and links to a <a href="https://example.com/source">source</a> with more information about the drivers.</p>
            </div>
          </section>
          <section><p>Related story</p><svg viewBox="0 0 24 24"></svg></section>
        </article></main></body></html>
        """.trimIndent()
}
