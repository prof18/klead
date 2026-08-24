package com.prof18.klead

import com.prof18.klead.extractors.Extractor
import com.prof18.klead.extractors.ExtractorContext
import com.prof18.klead.extractors.ExtractorResult
import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
