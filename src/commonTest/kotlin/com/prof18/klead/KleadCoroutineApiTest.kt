package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KleadCoroutineApiTest {
    @Test
    fun `suspend parser can use direct content extractor`() = runTest {
        val extractor = object : Extractor {
            override val id = "suspend-direct-test"

            override fun matches(context: ExtractorContext): Boolean = context.url.orEmpty().contains("direct.example")

            override fun extract(context: ExtractorContext): ExtractorResult =
                ExtractorResult(contentHtml = "<article><p>Direct suspend content.</p></article>")
        }

        val result = Klead.parseHtml(
            html = "<html><body></body></html>",
            url = "https://direct.example/story/1",
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
                customExtractors = listOf(extractor),
            ),
        )

        assertTrue(result.content.requireMarkdown().contains("Direct suspend content."))
    }

    @Test
    fun `internal parser dispatcher can be injected for tests`() = runTest {
        val result = KleadParser.parseHtml(
            html = "<html><body><p>Generic content should lose.</p></body></html>",
            url = "https://dispatcher.example/article",
            options = KleadOptions(
                outputs = setOf(KleadOutput.MARKDOWN),
                customExtractors = listOf(
                    object : Extractor {
                        override val id = "dispatcher-bridge-test"

                        override fun matches(context: ExtractorContext): Boolean =
                            context.url.orEmpty().contains("dispatcher.example")

                        override fun extract(context: ExtractorContext): ExtractorResult =
                            ExtractorResult(contentHtml = "<article><p>Injected dispatcher content.</p></article>")
                    },
                ),
            ),
            parserDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(result.content.requireMarkdown().contains("Injected dispatcher content."))
    }

    @Test
    fun `extractor matching stops once the parse is cancelled`() = kotlinx.coroutines.runBlocking {
        var parseJob: kotlinx.coroutines.Job? = null
        var laterProbes = 0
        val canceller = object : Extractor {
            override val id = "cancelling-probe"

            override fun matches(context: ExtractorContext): Boolean {
                parseJob?.cancel()
                return false
            }
        }
        val later = object : Extractor {
            override val id = "later-probe"

            override fun matches(context: ExtractorContext): Boolean {
                laterProbes++
                return false
            }
        }

        val job = launch {
            parseJob = coroutineContext[kotlinx.coroutines.Job]
            Klead.parseHtml(
                html = "<html><body><p>Cancelled before extraction.</p></body></html>",
                url = "https://example.com/cancelled",
                options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN), customExtractors = listOf(canceller, later)),
            )
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(0, laterProbes, "probes kept running after cancellation")
    }
}
