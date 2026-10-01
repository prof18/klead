package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlImageNormalizerTest {
    @Test
    fun `fixed clipped image spans lose their viewport constraining box`() {
        val document = Ksoup.parse(
            """<span style="border: none; display: inline-block; height: 165px; overflow: hidden; width: 610px"><img src="diagram.png" width="610" height="165" alt="Diagram"></span>""",
        )

        HtmlImageNormalizer.normalizeImages(document.body())

        assertEquals("border: none", document.selectFirst("span")!!.attr("style"))
        assertEquals("610", document.selectFirst("img")!!.attr("width"))
        assertEquals("165", document.selectFirst("img")!!.attr("height"))
        assertEquals("Diagram", document.selectFirst("img")!!.attr("alt"))
    }

    @Test
    fun `text boxes and responsive image wrappers keep their layout`() {
        val fixed = "display: inline-block; height: 165px; overflow: hidden; width: 610px"
        val responsive = "display: inline-block; height: auto; overflow: hidden; width: 100%"
        val document = Ksoup.parse(
            """<span style="$fixed"><img src="diagram.png">Article text</span><span style="$responsive"><img src="photo.png"></span>""",
        )

        HtmlImageNormalizer.normalizeImages(document.body())

        assertEquals(fixed, document.select("span")[0].attr("style"))
        assertEquals(responsive, document.select("span")[1].attr("style"))
    }

    @Test
    fun `external placeholder filename is replaced from data-src`() {
        val document = Ksoup.parse(
            """<img src="https://cdn.example/placeholder.PNG?width=80" data-src="https://cdn.example/real.jpg">""",
        )

        HtmlImageNormalizer.normalizeImages(document)

        assertEquals("https://cdn.example/real.jpg", document.selectFirst("img")?.attr("src"))
    }

    @Test
    fun `normal source with data-src remains unchanged`() {
        val document = Ksoup.parse(
            """<img src="https://cdn.example/real.jpg" data-src="https://cdn.example/other.jpg">""",
        )

        HtmlImageNormalizer.normalizeImages(document)

        assertEquals("https://cdn.example/real.jpg", document.selectFirst("img")?.attr("src"))
    }

    @Test
    fun `placeholder text in directory or article filename does not trigger replacement`() {
        val document = Ksoup.parse(
            """
            <div>
              <img src="https://cdn.example/placeholder-assets/hero.jpg" data-src="https://cdn.example/real-directory.jpg">
              <img src="https://cdn.example/article-placeholder.jpg" data-src="https://cdn.example/real-article.jpg">
            </div>
            """.trimIndent(),
        )

        HtmlImageNormalizer.normalizeImages(document)

        assertEquals(
            listOf("https://cdn.example/placeholder-assets/hero.jpg", "https://cdn.example/article-placeholder.jpg"),
            document.select("img").map { it.attr("src") },
        )
    }

    @Test
    fun `placeholder URL without replacement is preserved`() {
        val document = Ksoup.parse("""<img src="https://cdn.example/placeholder.png?width=80">""")

        HtmlImageNormalizer.normalizeImages(document)

        assertEquals("https://cdn.example/placeholder.png?width=80", document.selectFirst("img")?.attr("src"))
    }

    @Test
    fun `responsive photo drops placeholder dimensions while small icon keeps them`() {
        val document = Ksoup.parse(
            """
            <picture>
              <source srcset="https://cdn.example/photo-small.webp 213w, https://cdn.example/photo-large.webp 889w">
              <img src="https://cdn.example/16x9.png" width="16" height="9" alt="Photo">
            </picture>
            <picture>
              <source srcset="https://cdn.example/icon-2x.webp 32w">
              <img src="https://cdn.example/icon.png" width="16" height="16" alt="Icon">
            </picture>
            """.trimIndent(),
        )

        HtmlImageNormalizer.normalizeImages(document)

        val images = document.select("img")
        assertEquals(false, images[0].hasAttr("width"))
        assertEquals(false, images[0].hasAttr("height"))
        assertEquals("16", images[1].attr("width"))
        assertEquals("16", images[1].attr("height"))
    }

    @Test
    fun `ghost bookmark favicon is removed while link text and thumbnail remain`() {
        val content = Ksoup.parse(
            """
            <figure class="kg-card kg-bookmark-card">
              <a class="kg-bookmark-container" href="https://github.com/example/project">
                <div class="kg-bookmark-content">
                  <div class="kg-bookmark-title">Project</div>
                  <div class="kg-bookmark-description">A useful project.</div>
                  <div class="kg-bookmark-metadata">
                    <img class="kg-bookmark-icon" src="https://example.com/logo.svg" alt="">
                    <span class="kg-bookmark-author">GitHub</span>
                  </div>
                </div>
                <div class="kg-bookmark-thumbnail"><img src="https://example.com/preview.png" alt="Preview"></div>
              </a>
            </figure>
            """.trimIndent(),
        ).body()

        HtmlImageNormalizer.normalizeImages(content)

        assertNull(content.selectFirst(".kg-bookmark-icon"))
        assertEquals("https://github.com/example/project", content.selectFirst("a")?.attr("href"))
        assertEquals("https://example.com/preview.png", content.selectFirst("img")?.attr("src"))
        assertTrue(content.text().contains("Project"))
        assertTrue(content.text().contains("A useful project."))
        assertTrue(content.text().contains("GitHub"))
    }

    @Test
    fun `ordinary linked svg and bookmark icon class outside a card are preserved`() {
        val content = Ksoup.parse(
            """
            <p><a href="https://example.com/project"><img src="https://example.com/diagram.svg" alt="Diagram"></a></p>
            <img class="kg-bookmark-icon" src="https://example.com/standalone.svg" alt="Standalone image">
            """.trimIndent(),
        ).body()

        HtmlImageNormalizer.normalizeImages(content)

        assertEquals(
            listOf("https://example.com/diagram.svg", "https://example.com/standalone.svg"),
            content.select("img").map { it.attr("src") },
        )
    }
}
