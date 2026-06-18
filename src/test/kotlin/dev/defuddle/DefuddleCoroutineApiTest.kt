package dev.defuddle

import dev.defuddle.extractors.DefuddleHttpClient
import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefuddleCoroutineApiTest {
    @Test
    fun `async parser uses suspend network extractor`() = runTest {
        val calls = mutableListOf<String>()

        val client = object : DefuddleHttpClient {
            override suspend fun get(url: String): String {
                calls += url
                return "<article><p>Fetched async transcript.</p></article>"
            }
        }
        val extractor = object : Extractor {
            override val id = "async-network-test"

            override fun matches(context: ExtractorContext): Boolean =
                context.url.orEmpty().contains("network.example")

            override suspend fun extract(context: ExtractorContext): ExtractorResult {
                return ExtractorResult(
                    contentHtml = context.httpClient?.get("${context.url}/transcript").orEmpty(),
                )
            }
        }

        val result = Defuddle.parseHtmlAsync(
            html = "<html><body></body></html>",
            url = "https://network.example/watch/1",
            options = DefuddleOptions(
                httpClient = client,
                extractors = listOf(extractor),
            ),
        )

        assertEquals(listOf("https://network.example/watch/1/transcript"), calls)
        assertEquals("async-network-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Fetched async transcript."))
    }

    @Test
    fun `sync parser bridges suspend extractor for compatibility`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Generic content should lose.</p></body></html>",
            url = "https://sync.example/article",
            options = DefuddleOptions(
                extractors = listOf(
                    object : Extractor {
                        override val id = "sync-bridge-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("sync.example")

                        override suspend fun extract(context: ExtractorContext): ExtractorResult =
                            ExtractorResult(contentHtml = "<article><p>Suspend extractor content.</p></article>")
                    },
                ),
            ),
        )

        assertEquals("sync-bridge-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Suspend extractor content."))
    }
}
