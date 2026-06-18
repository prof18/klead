package dev.defuddle

import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefuddleCoroutineApiTest {
    @Test
    fun `async parser can use direct content extractor`() = runTest {
        val extractor = object : Extractor {
            override val id = "async-direct-test"

            override fun matches(context: ExtractorContext): Boolean =
                context.url.orEmpty().contains("direct.example")

            override fun extract(context: ExtractorContext): ExtractorResult =
                ExtractorResult(contentHtml = "<article><p>Direct async content.</p></article>")
        }

        val result = Defuddle.parseHtmlAsync(
            html = "<html><body></body></html>",
            url = "https://direct.example/story/1",
            options = DefuddleOptions(
                extractors = listOf(extractor),
            ),
        )

        assertEquals("async-direct-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Direct async content."))
    }

    @Test
    fun `sync parser uses direct content extractor`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Generic content should lose.</p></body></html>",
            url = "https://sync.example/article",
            options = DefuddleOptions(
                extractors = listOf(
                    object : Extractor {
                        override val id = "sync-bridge-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("sync.example")

                        override fun extract(context: ExtractorContext): ExtractorResult =
                            ExtractorResult(contentHtml = "<article><p>Direct extractor content.</p></article>")
                    },
                ),
            ),
        )

        assertEquals("sync-bridge-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Direct extractor content."))
    }
}
