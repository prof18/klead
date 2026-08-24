package com.prof18.klead.fixtures

import com.prof18.klead.KleadOptions
import com.prof18.klead.KleadOutput
import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

class RegressionCorpusBenchmarkTest {
    @Test
    fun benchmarkRealWorldRegressionCorpusWhenRequested() = runTest(timeout = 15.minutes) {
        if (commonTestEnvironment(RUN_BENCHMARK_ENV) != "true") return@runTest

        val platform = requireNotNull(commonTestEnvironment(PLATFORM_ENV)) {
            "$PLATFORM_ENV must identify the benchmark platform"
        }
        val target = commonTestEnvironment(TARGET_ENV).orEmpty()
        val sampleCount = commonTestEnvironment(SAMPLE_COUNT_ENV)?.toIntOrNull() ?: DEFAULT_SAMPLE_COUNT
        require(sampleCount in 1..MAX_SAMPLE_COUNT) {
            "$SAMPLE_COUNT_ENV must be between 1 and $MAX_SAMPLE_COUNT"
        }

        val fixtures = CommonTestResources.paths
            .filter { it.startsWith(INPUT_DIRECTORY) && it.endsWith(HTML_SUFFIX) }
            .filterNot { it.substringAfterLast('/').startsWith("harness--") }
            .sorted()
        assertTrue(fixtures.isNotEmpty(), "Expected real-world regression fixtures")

        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
        parseCorpus(fixtures, dispatcher, options)
        val samples = List(sampleCount) { parseCorpus(fixtures, dispatcher, options) }

        val corpusSamples = samples.map { sample -> sample.sumOf(FixtureTiming::elapsedMillis) }.sorted()
        val fixtureMedians = fixtures.indices.map { fixtureIndex ->
            val timings = samples.map { it[fixtureIndex].elapsedMillis }.sorted()
            FixtureTiming(samples.first()[fixtureIndex].name, timings.median())
        }
        fixtureMedians.sortedByDescending(FixtureTiming::elapsedMillis)
            .take(SLOW_REPORT_LIMIT)
            .forEach { report ->
                println(
                    "TIMING_REGRESSION_PAGE platform=$platform target=$target " +
                        "median=${report.elapsedMillis}ms name=${report.name}",
                )
            }

        val corpusMedian = corpusSamples.median()
        println(
            "TIMING_REGRESSION_CORPUS platform=$platform target=$target fixtures=${fixtures.size} " +
                "median=${corpusMedian}ms samples=$corpusSamples",
        )

        commonTestEnvironment(MAX_MEDIAN_ENV)?.toLongOrNull()?.let { maximumMedian ->
            assertTrue(
                corpusMedian <= maximumMedian,
                "$platform regression corpus median ${corpusMedian}ms exceeded ${maximumMedian}ms budget",
            )
        }
    }

    private suspend fun parseCorpus(
        fixtures: List<String>,
        dispatcher: CoroutineDispatcher,
        options: KleadOptions,
    ): List<FixtureTiming> = fixtures.map { path ->
        val html = CommonTestResources.read(path)
        val name = path.substringAfterLast('/').removeSuffix(HTML_SUFFIX)
        val mark = TimeSource.Monotonic.markNow()
        val result = KleadParser.parseHtml(
            html = html,
            url = FixtureLoader.extractUrl(name, html),
            options = options,
            parserDispatcher = dispatcher,
        )
        val elapsedMillis = mark.elapsedNow().inWholeMilliseconds
        assertTrue(result.content.requireHtml().isNotBlank(), "$name produced empty HTML")
        assertTrue(result.content.requireMarkdown().isNotBlank(), "$name produced empty Markdown")
        FixtureTiming(name, elapsedMillis)
    }

    private fun List<Long>.median(): Long = get(size / 2)

    private data class FixtureTiming(val name: String, val elapsedMillis: Long)

    private companion object {
        const val DEFAULT_SAMPLE_COUNT = 3
        const val MAX_SAMPLE_COUNT = 15
        const val SLOW_REPORT_LIMIT = 12
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html/"
        const val HTML_SUFFIX = ".html"
        const val RUN_BENCHMARK_ENV = "KLEAD_RUN_REGRESSION_BENCHMARK"
        const val PLATFORM_ENV = "KLEAD_BENCHMARK_PLATFORM"
        const val TARGET_ENV = "KLEAD_BENCHMARK_TARGET"
        const val SAMPLE_COUNT_ENV = "KLEAD_BENCHMARK_SAMPLES"
        const val MAX_MEDIAN_ENV = "KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS"
    }
}
