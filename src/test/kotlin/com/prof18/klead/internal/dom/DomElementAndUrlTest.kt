package com.prof18.klead.internal.dom

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomElementAndUrlTest {
    @Test
    fun `element helpers expose stable jsoup values`() {
        val document = Jsoup.parse(
            """
            <html>
              <body>
                <article id="Main" class="story lead">
                  Hello <strong>there</strong>
                  <p>Nested text</p>
                </article>
              </body>
            </html>
            """.trimIndent(),
            "https://example.com/posts/story",
        )
        val article = document.selectFirst("article") ?: error("missing article")

        assertEquals("article", article.tagLower())
        assertEquals("story lead", article.classNameSafe())
        assertEquals("Main", article.idSafe())
        assertEquals("Hello there Nested text", article.textContentLike())
        assertEquals("Hello", article.ownTextContentLike())
        assertEquals("Hello <strong>there</strong>\n<p>Nested text</p>", article.innerHtmlStable())
        assertTrue(article.outerHtmlStable().startsWith("""<article id="Main""""))
        assertEquals(listOf("strong", "p"), article.childrenElements().map { it.tagLower() })
        assertEquals(listOf("strong", "p"), article.descendants().map { it.tagLower() })
    }

    @Test
    fun `url helpers resolve safe relative urls`() {
        val document = Jsoup.parse(
            """<a href="next.html">Next</a><img src="/image.png">""",
            "https://example.com/articles/current/index.html",
        )
        val link = document.selectFirst("a") ?: error("missing link")
        val image = document.selectFirst("img") ?: error("missing image")

        assertEquals("https://example.com/articles/current/next.html", link.absUrlOrEmpty("href"))
        assertEquals("https://example.com/image.png", image.absUrlOrEmpty("src"))
        assertEquals("https://example.com/assets/photo.jpg", resolveUrl("https://example.com/a/b", "/assets/photo.jpg"))
        assertEquals(
            "https://example.com/a/relative/photo.jpg",
            resolveUrl("https://example.com/a/b", "relative/photo.jpg"),
        )
    }

    @Test
    fun `url helpers reject dangerous urls`() {
        assertTrue(isDangerousUrl("javascript:alert(1)"))
        assertTrue(isDangerousUrl(" data:text/html,<script>alert(1)</script>"))
        assertFalse(isDangerousUrl("data:image/png;base64,AAAA"))
        assertFalse(isDangerousUrl("https://example.com"))
        assertEquals("", resolveUrl("https://example.com", "javascript:alert(1)"))
    }
}
