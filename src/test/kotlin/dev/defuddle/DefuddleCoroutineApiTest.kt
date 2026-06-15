package dev.defuddle

import dev.defuddle.extractors.DefuddleHttpClient
import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.jsoup.nodes.Document
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefuddleCoroutineApiTest {
    @Test
    fun `async parser uses configured dispatcher and suspend network extractor`() = runTest {
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "defuddle-test-dispatcher")
        }.asCoroutineDispatcher()
        val calls = mutableListOf<String>()
        var extractorThread = ""
        var clientThread = ""

        try {
            val client = object : DefuddleHttpClient {
                override suspend fun get(url: String): String {
                    clientThread = Thread.currentThread().name
                    calls += url
                    return "<article><p>Fetched async transcript.</p></article>"
                }
            }
            val extractor = object : Extractor {
                override val name = "async-network-test"

                override fun canExtract(
                    document: Document,
                    url: String,
                ): Boolean = url.contains("network.example")

                override suspend fun extract(
                    document: Document,
                    url: String,
                    context: ExtractorContext,
                ): ExtractorResult {
                    extractorThread = Thread.currentThread().name
                    return ExtractorResult(
                        contentHtml = context.httpClient?.get("$url/transcript").orEmpty(),
                    )
                }
            }

            val result = Defuddle.parseHtmlAsync(
                html = "<html><body></body></html>",
                url = "https://network.example/watch/1",
                options = DefuddleOptions(
                    httpClient = client,
                    extractors = listOf(extractor),
                    parseDispatcher = dispatcher,
                ),
            )

            assertEquals(listOf("https://network.example/watch/1/transcript"), calls)
            assertEquals("async-network-test", result.extractor)
            assertTrue(result.contentMarkdown.contains("Fetched async transcript."))
            assertTrue(extractorThread.contains("defuddle-test-dispatcher"), extractorThread)
            assertTrue(clientThread.contains("defuddle-test-dispatcher"), clientThread)
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `sync parser bridges suspend extractor for compatibility`() {
        val result = Defuddle.parseHtml(
            html = "<html><body><p>Generic content should lose.</p></body></html>",
            url = "https://sync.example/article",
            options = DefuddleOptions(
                extractors = listOf(
                    object : Extractor {
                        override val name = "sync-bridge-test"

                        override fun canExtract(
                            document: Document,
                            url: String,
                        ): Boolean = url.contains("sync.example")

                        override suspend fun extract(
                            document: Document,
                            url: String,
                            context: ExtractorContext,
                        ): ExtractorResult =
                            ExtractorResult(contentHtml = "<article><p>Suspend extractor content.</p></article>")
                    },
                ),
            ),
        )

        assertEquals("sync-bridge-test", result.extractor)
        assertTrue(result.contentMarkdown.contains("Suspend extractor content."))
    }
}
