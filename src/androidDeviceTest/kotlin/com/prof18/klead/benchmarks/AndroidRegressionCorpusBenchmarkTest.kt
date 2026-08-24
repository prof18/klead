package com.prof18.klead.benchmarks

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.prof18.klead.Klead
import com.prof18.klead.KleadOptions
import com.prof18.klead.KleadOutput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class AndroidRegressionCorpusBenchmarkTest {
    @Test
    fun benchmarkRegressionCorpus() {
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val platform = arguments.getString(PLATFORM_ARGUMENT) ?: "android"
            val target = arguments.getString(TARGET_ARGUMENT).orEmpty()
            val sampleCount = arguments.getString(SAMPLE_COUNT_ARGUMENT)?.toIntOrNull() ?: DEFAULT_SAMPLE_COUNT
            require(sampleCount in 1..MAX_SAMPLE_COUNT) {
                "$SAMPLE_COUNT_ARGUMENT must be between 1 and $MAX_SAMPLE_COUNT"
            }

            val testApk = InstrumentationRegistry.getInstrumentation().context.packageCodePath
            val fixtureNames = ZipFile(testApk).use { archive ->
                archive.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("$INPUT_DIRECTORY/") && it.endsWith(HTML_SUFFIX) }
                    .map { it.substringAfterLast('/') }
                    .filterNot { it.startsWith("harness--") }
                    .sorted()
                    .toList()
            }
            assertTrue("Expected Android benchmark fixtures", fixtureNames.isNotEmpty())

            val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
            parseCorpus(fixtureNames, options)
            val samples = List(sampleCount) { parseCorpus(fixtureNames, options) }
            val corpusSamples = samples.map { sample -> sample.sumOf(FixtureTiming::elapsedMillis) }.sorted()
            val fixtureMedians = fixtureNames.indices.map { fixtureIndex ->
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
                "TIMING_REGRESSION_CORPUS platform=$platform target=$target fixtures=${fixtureNames.size} " +
                    "median=${corpusMedian}ms samples=$corpusSamples",
            )

            arguments.getString(MAX_MEDIAN_ARGUMENT)?.toLongOrNull()?.let { maximumMedian ->
                assertTrue(
                    "Android regression corpus median ${corpusMedian}ms exceeded ${maximumMedian}ms budget",
                    corpusMedian <= maximumMedian,
                )
            }
        }
    }

    private suspend fun parseCorpus(fixtureNames: List<String>, options: KleadOptions): List<FixtureTiming> =
        fixtureNames.map { nameWithExtension ->
            val inputPath = "$INPUT_DIRECTORY/$nameWithExtension"
            val html = requireNotNull(javaClass.classLoader?.getResourceAsStream(inputPath)) {
                "Missing Android benchmark fixture: $inputPath"
            }
                .bufferedReader()
                .use { it.readText() }
            val name = nameWithExtension.removeSuffix(HTML_SUFFIX)
            val startNanos = SystemClock.elapsedRealtimeNanos()
            val result = Klead.parseHtml(html, sourceUrl(name, html), options)
            val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startNanos) / NANOS_PER_MILLISECOND
            assertTrue("$name produced empty HTML", result.content.requireHtml().isNotBlank())
            assertTrue("$name produced empty Markdown", result.content.requireMarkdown().isNotBlank())
            FixtureTiming(name, elapsedMillis)
        }

    private fun sourceUrl(name: String, html: String): String {
        val frontmatter = html.substringAfter("<!--", missingDelimiterValue = "")
            .substringBefore("-->", missingDelimiterValue = "")
        return frontmatter.substringAfter(URL_MARKER, missingDelimiterValue = "")
            .substringBefore('"', missingDelimiterValue = "")
            .ifBlank { "https://${name.removePrefix("general--")}" }
    }

    private fun List<Long>.median(): Long = get(size / 2)

    private data class FixtureTiming(val name: String, val elapsedMillis: Long)

    private companion object {
        const val URL_MARKER = "\"url\":\""
        const val DEFAULT_SAMPLE_COUNT = 3
        const val MAX_SAMPLE_COUNT = 15
        const val SLOW_REPORT_LIMIT = 12
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html"
        const val HTML_SUFFIX = ".html"
        const val PLATFORM_ARGUMENT = "KLEAD_BENCHMARK_PLATFORM"
        const val TARGET_ARGUMENT = "KLEAD_BENCHMARK_TARGET"
        const val SAMPLE_COUNT_ARGUMENT = "KLEAD_BENCHMARK_SAMPLES"
        const val MAX_MEDIAN_ARGUMENT = "KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS"
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
