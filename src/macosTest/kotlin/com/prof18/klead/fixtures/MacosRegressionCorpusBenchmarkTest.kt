package com.prof18.klead.fixtures

import com.prof18.klead.KleadOptions
import com.prof18.klead.KleadOutput
import com.prof18.klead.internal.KleadParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

@OptIn(ExperimentalForeignApi::class)
class MacosRegressionCorpusBenchmarkTest {
    @Test
    fun benchmarkRealWorldRegressionCorpusWhenRequested() = runTest(timeout = 10.minutes) {
        if (environment(RUN_BENCHMARK_ENV) != "true") return@runTest

        val fixtures = CommonTestResources.paths
            .filter { it.startsWith(INPUT_DIRECTORY) && it.endsWith(HTML_SUFFIX) }
            .filterNot { it.substringAfterLast('/').startsWith("harness--") }
            .sorted()
        assertTrue(fixtures.isNotEmpty(), "Expected real-world regression fixtures")

        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
        val samples = List(SAMPLE_COUNT) {
            fixtures.map { path ->
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
        }

        val corpusSamples = samples.map { sample -> sample.sumOf(FixtureTiming::elapsedMillis) }.sorted()
        val fixtureMedians = fixtures.indices.map { fixtureIndex ->
            val timings = samples.map { it[fixtureIndex].elapsedMillis }.sorted()
            FixtureTiming(samples.first()[fixtureIndex].name, timings.median())
        }
        fixtureMedians.sortedByDescending(FixtureTiming::elapsedMillis)
            .take(SLOW_REPORT_LIMIT)
            .forEach { report ->
                println("TIMING_MACOS_REGRESSION_PAGE median=${report.elapsedMillis}ms name=${report.name}")
            }

        val corpusMedian = corpusSamples.median()
        val target = environment(NATIVE_TARGET_ENV).orEmpty()
        println(
            "TIMING_MACOS_REGRESSION_CORPUS target=$target fixtures=${fixtures.size} " +
                "median=${corpusMedian}ms samples=$corpusSamples",
        )

        val maximumMedian = environment(MAX_MEDIAN_ENV)?.toLongOrNull() ?: DEFAULT_MAX_MEDIAN_MILLIS
        assertTrue(
            corpusMedian <= maximumMedian,
            "regression corpus median ${corpusMedian}ms exceeded ${maximumMedian}ms budget",
        )
    }

    private fun List<Long>.median(): Long = get(size / 2)

    private fun environment(name: String): String? = getenv(name)?.toKString()

    private data class FixtureTiming(val name: String, val elapsedMillis: Long)

    private companion object {
        const val SAMPLE_COUNT = 3
        const val SLOW_REPORT_LIMIT = 12
        const val DEFAULT_MAX_MEDIAN_MILLIS = 4_000L
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html/"
        const val HTML_SUFFIX = ".html"
        const val RUN_BENCHMARK_ENV = "KLEAD_RUN_REGRESSION_BENCHMARK"
        const val MAX_MEDIAN_ENV = "KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS"
        const val NATIVE_TARGET_ENV = "KLEAD_NATIVE_TARGET"
    }
}
