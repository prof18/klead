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
}
