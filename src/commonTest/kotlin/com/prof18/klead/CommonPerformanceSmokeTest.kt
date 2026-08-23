package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class CommonPerformanceSmokeTest {
    @Test
    fun printEmbeddedMediumFixtureTimings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN))
        val parse: suspend () -> KleadResult = {
            KleadParser.parseHtml(
                html = COMMON_MEDIUM_FIXTURE,
                url = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array",
                options = options,
                parserDispatcher = dispatcher,
            )
        }
        parse().assertBenchmarkOutput()
        val samples = sampleTimings(parse)

        println("TIMING_COMMON_MEDIUM ${samples.summary()}")
    }

    @Test
    fun printSyntheticFixtureTimings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN))
        val smallHtml = "<article><p>Small article text.</p></article>"
        val longHtml = buildString {
            append("<article>")
            repeat(300) { index ->
                append("<p>Long article paragraph $index with enough words for a benchmark smoke test.</p>")
            }
            append("</article>")
        }
        val parseSmall: suspend () -> KleadResult = {
            KleadParser.parseHtml(smallHtml, "https://example.com/small", options, dispatcher)
        }
        val parseLong: suspend () -> KleadResult = {
            KleadParser.parseHtml(longHtml, "https://example.com/long", options, dispatcher)
        }
        parseSmall().assertBenchmarkOutput()
        parseLong().assertBenchmarkOutput()
        val smallSamples = sampleTimings(parseSmall)
        val longSamples = sampleTimings(parseLong)

        println("TIMING_COMMON_SMALL ${smallSamples.summary()}")
        println("TIMING_COMMON_LONG ${longSamples.summary()}")
        assertTrue(smallSamples.last() < SMALL_MAX_MILLIS, "small samples exceeded threshold: $smallSamples")
        assertTrue(longSamples.last() < LONG_MAX_MILLIS, "long samples exceeded threshold: $longSamples")
    }

    private suspend fun sampleTimings(block: suspend () -> KleadResult): List<Long> = buildList {
        repeat(SAMPLE_COUNT) {
            val mark = TimeSource.Monotonic.markNow()
            val result = block()
            val elapsedMillis = mark.elapsedNow().inWholeMilliseconds
            result.assertBenchmarkOutput()
            add(elapsedMillis)
        }
    }.sorted()

    private fun KleadResult.assertBenchmarkOutput() {
        assertTrue(content.html?.isNotBlank() == true, "benchmark HTML output was empty")
        assertTrue(content.markdown?.isNotBlank() == true, "benchmark Markdown output was empty")
    }

    private fun List<Long>.summary(): String =
        "min=${first()}ms median=${get(size / 2)}ms max=${last()}ms samples=$this"

    private companion object {
        const val SAMPLE_COUNT = 11
        const val SMALL_MAX_MILLIS = 1_000
        const val LONG_MAX_MILLIS = 5_000
    }
}
