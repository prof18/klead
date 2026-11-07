package com.prof18.klead.internal.extractors.site

import com.prof18.klead.parseHtmlForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrawlixComicProfileTest {
    @Test
    fun `comic page image leads the blog post on Grawlix pages`() {
        val markdown = parseHtmlForTest(pageHtml(GRAWLIX_GENERATOR), "https://comic.example/comic/12")
            .content.requireMarkdown()

        assertTrue(markdown.startsWith("![page](https://comic.example/assets/comics/12.jpg)"))
        assertTrue(markdown.contains("The author writes a long note under the comic page"))
        assertFalse(markdown.contains("sidebar.png"))
    }

    @Test
    fun `comic already inside the content is not duplicated`() {
        val html = pageHtml(GRAWLIX_GENERATOR).replace(
            "<div itemprop=\"articleBody\">",
            "<div itemprop=\"articleBody\"><p><img src=\"/assets/comics/12.jpg\" alt=\"page\"></p>",
        )
        val markdown = parseHtmlForTest(html, "https://comic.example/comic/12").content.requireMarkdown()

        assertEquals(1, Regex("""assets/comics/12\.jpg""").findAll(markdown).count())
    }

    @Test
    fun `pages from other generators are unaffected`() {
        val markdown = parseHtmlForTest(pageHtml("WordPress 6.8"), "https://comic.example/comic/12")
            .content.requireMarkdown()

        assertFalse(markdown.contains("assets/comics/12.jpg"))
    }

    private fun pageHtml(generator: String) = """
        <html><head>
          <meta name="generator" content="$generator">
          <title>Example comic | Page 12</title>
        </head><body>
          <main>
            <section>
              <div id="comic-stage">
                <article itemscope itemtype="http://schema.org/CreativeWork" id="comic">
                  <figure id="page"><img itemprop="image" src="/assets/comics/12.jpg" alt="page"></figure>
                </article>
              </div>
              <div id="sidebar"><img src="/themes/sidebar.png" alt="Follow"></div>
            </section>
            <section>
              <article itemscope itemtype="https://schema.org/BlogPosting" id="blog-post">
                <h2 itemprop="headline">Page 12</h2>
                <div itemprop="articleBody">
                  <p>The author writes a long note under the comic page, with enough words to read as the main text of the page for the parser.</p>
                  <p>A second paragraph keeps going with more commentary about the story, the characters and what comes next in the chapter.</p>
                </div>
              </article>
            </section>
          </main>
        </body></html>
        """.trimIndent()

    private companion object {
        const val GRAWLIX_GENERATOR = "The Grawlix CMS — the CMS for comics"
    }
}
