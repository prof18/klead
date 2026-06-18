package dev.defuddle

import dev.defuddle.extractors.Extractor
import dev.defuddle.extractors.ExtractorContext
import dev.defuddle.extractors.ExtractorResult
import dev.defuddle.internal.DefuddleParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DefuddleCoroutineApiTest {
    @Test
    fun `suspend parser can use direct content extractor`() = runTest {
        val extractor = object : Extractor {
            override val id = "suspend-direct-test"

            override fun matches(context: ExtractorContext): Boolean = context.url.orEmpty().contains("direct.example")

            override fun extract(context: ExtractorContext): ExtractorResult =
                ExtractorResult(contentHtml = "<article><p>Direct suspend content.</p></article>")
        }

        val result = Defuddle.parseHtml(
            html = "<html><body></body></html>",
            url = "https://direct.example/story/1",
            options = DefuddleOptions(
                outputs = setOf(DefuddleOutput.MARKDOWN),
                customExtractors = listOf(extractor),
            ),
        )

        assertTrue(result.content.requireMarkdown().contains("Direct suspend content."))
    }

    @Test
    fun `internal parser dispatcher can be injected for tests`() = runTest {
        val result = DefuddleParser.parseHtml(
            html = "<html><body><p>Generic content should lose.</p></body></html>",
            url = "https://dispatcher.example/article",
            options = DefuddleOptions(
                outputs = setOf(DefuddleOutput.MARKDOWN),
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
