package com.prof18.klead.internal.standardize

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlImageNormalizerTest {
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
}
