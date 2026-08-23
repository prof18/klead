package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains

class PublicApiSmokeTest {
    @Test
    fun `public parser and selector extractor work on every target`() = runTest {
        val extractor = object : Extractor {
            override val id: String = "public-api-smoke"
            override val domains: Set<String> = setOf("example.com")
            override val contentSelectors: List<String> = listOf("article.story")
            override val postContentRemoveSelectors: List<String> = listOf(".related")
        }

        val result = Klead.parseHtml(
            html = """
                <html><body>
                  <article class="story"><p>Portable public API content.</p><aside class="related">Related</aside></article>
                </body></html>
            """.trimIndent(),
            url = "https://www.example.com/story",
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
                customExtractors = listOf(extractor),
            ),
        )

        assertContains(result.content.requireMarkdown(), "Portable public API content.")
    }
}
