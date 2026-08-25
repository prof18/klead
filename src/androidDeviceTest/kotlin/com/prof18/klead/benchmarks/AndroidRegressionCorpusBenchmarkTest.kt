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

            val coreFixtureNames = readResource(CORE_MANIFEST_PATH)
                .lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith('#') }
                .toList()
                .also { entries ->
                    require(entries.size == entries.toSet().size) { "$CORE_MANIFEST_PATH contains duplicate fixtures" }
                }
                .toSet()
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
            val fixtures = fixtureNames.map { nameWithExtension ->
                val html = readResource("$INPUT_DIRECTORY/$nameWithExtension")
                val name = nameWithExtension.removeSuffix(HTML_SUFFIX)
                FixtureInput(
                    name = name,
                    html = html,
                    inputBytes = html.toByteArray(Charsets.UTF_8).size.toLong(),
                    isCore = name in coreFixtureNames,
                )
            }
            val missingCoreFixtures = coreFixtureNames - fixtures.map(FixtureInput::name).toSet()
            assertTrue("$CORE_MANIFEST_PATH must not be empty", coreFixtureNames.isNotEmpty())
            assertTrue(
                "$CORE_MANIFEST_PATH references missing fixtures: ${missingCoreFixtures.sorted()}",
                missingCoreFixtures.isEmpty(),
            )

            val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
            parseCorpus(fixtures, options)
            val samples = List(sampleCount) { parseCorpus(fixtures, options) }
            val fullMetrics = samples.metrics { true }
            val coreMetrics = samples.metrics(FixtureTiming::isCore)

            fullMetrics.fixtureMedians.sortedByDescending(FixtureTiming::elapsedMicros)
                .take(SLOW_REPORT_LIMIT)
                .forEach { report ->
                    println(
                        "TIMING_REGRESSION_PAGE platform=$platform target=$target " +
                            "median=${report.elapsedMicros.microsToMillis()}ms medianMicros=${report.elapsedMicros} " +
                            "inputBytes=${report.inputBytes} core=${report.isCore} name=${report.name}",
                    )
                }

            println(fullMetrics.reportLine("TIMING_REGRESSION_CORPUS", platform, target))
            println(coreMetrics.reportLine("TIMING_REGRESSION_CORE", platform, target))

            assertBudget(
                arguments.getString(CORE_MAX_MEDIAN_ARGUMENT),
                coreMetrics.totalMedianMicros,
            ) { maximum ->
                "Android core total median ${coreMetrics.totalMedianMicros.microsToMillis()}ms exceeded ${maximum}ms budget"
            }
            assertBudget(
                arguments.getString(CORE_MAX_P95_ARGUMENT),
                coreMetrics.p95ArticleMicros,
            ) { maximum ->
                "Android core p95 article ${coreMetrics.p95ArticleMicros.microsToMillis()}ms exceeded ${maximum}ms budget"
            }
            assertBudget(
                arguments.getString(CORE_MAX_WORST_ARGUMENT),
                coreMetrics.worstArticle.elapsedMicros,
            ) { maximum ->
                "Android core worst article ${coreMetrics.worstArticle.elapsedMicros.microsToMillis()}ms " +
                    "exceeded ${maximum}ms budget"
            }
        }
    }

    private suspend fun parseCorpus(fixtures: List<FixtureInput>, options: KleadOptions): List<FixtureTiming> =
        fixtures.map { fixture ->
            val startNanos = SystemClock.elapsedRealtimeNanos()
            val result = Klead.parseHtml(fixture.html, sourceUrl(fixture.name, fixture.html), options)
            val elapsedMicros = (SystemClock.elapsedRealtimeNanos() - startNanos) / NANOS_PER_MICROSECOND
            assertTrue("${fixture.name} produced empty HTML", result.content.requireHtml().isNotBlank())
            assertTrue("${fixture.name} produced empty Markdown", result.content.requireMarkdown().isNotBlank())
            FixtureTiming(
                name = fixture.name,
                elapsedMicros = elapsedMicros,
                inputBytes = fixture.inputBytes,
                isCore = fixture.isCore,
            )
        }

    private fun readResource(path: String): String = requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
        "Missing Android benchmark resource: $path"
    }.bufferedReader().use { it.readText() }

    private fun sourceUrl(name: String, html: String): String {
        val frontmatter = html.substringAfter("<!--", missingDelimiterValue = "")
            .substringBefore("-->", missingDelimiterValue = "")
        return frontmatter.substringAfter(URL_MARKER, missingDelimiterValue = "")
            .substringBefore('"', missingDelimiterValue = "")
            .ifBlank { "https://${name.removePrefix("general--")}" }
    }

    private fun List<List<FixtureTiming>>.metrics(include: (FixtureTiming) -> Boolean): CorpusMetrics {
        val includedIndexes = first().indices.filter { index -> include(first()[index]) }
        require(includedIndexes.isNotEmpty()) { "Expected benchmark fixtures for cohort" }
        val totalSamplesMicros = map { sample ->
            includedIndexes.sumOf { index -> sample[index].elapsedMicros }
        }.sorted()
        val fixtureMedians = includedIndexes.map { fixtureIndex ->
            val fixture = first()[fixtureIndex]
            fixture.copy(elapsedMicros = map { it[fixtureIndex].elapsedMicros }.sorted().median())
        }
        val sortedFixtureMedians = fixtureMedians.sortedBy(FixtureTiming::elapsedMicros)
        val totalMedianMicros = totalSamplesMicros.median()
        val inputBytes = fixtureMedians.sumOf(FixtureTiming::inputBytes)
        return CorpusMetrics(
            fixtureMedians = fixtureMedians,
            totalSamplesMicros = totalSamplesMicros,
            totalMedianMicros = totalMedianMicros,
            inputBytes = inputBytes,
            meanArticleMicros = totalMedianMicros / fixtureMedians.size,
            p50ArticleMicros = sortedFixtureMedians.map(FixtureTiming::elapsedMicros).median(),
            p95ArticleMicros = sortedFixtureMedians.map(FixtureTiming::elapsedMicros).percentile(95),
            worstArticle = sortedFixtureMedians.last(),
            throughputBytesPerSecond = inputBytes * MICROS_PER_SECOND / totalMedianMicros.coerceAtLeast(1),
        )
    }

    private fun CorpusMetrics.reportLine(prefix: String, platform: String, target: String): String =
        "$prefix platform=$platform target=$target fixtures=${fixtureMedians.size} " +
            "median=${totalMedianMicros.microsToMillis()}ms " +
            "samples=${totalSamplesMicros.map { it.microsToMillis() }} " +
            "inputBytes=$inputBytes meanArticle=${meanArticleMicros}us " +
            "p50Article=${p50ArticleMicros}us p95Article=${p95ArticleMicros}us " +
            "maxArticle=${worstArticle.elapsedMicros}us slowest=${worstArticle.name} " +
            "throughput=${throughputBytesPerSecond}Bps"

    private fun assertBudget(maximumMillisValue: String?, actualMicros: Long, message: (Long) -> String) {
        maximumMillisValue?.toLongOrNull()?.let { maximumMillis ->
            assertTrue(message(maximumMillis), actualMicros <= maximumMillis * MICROS_PER_MILLISECOND)
        }
    }

    private fun List<Long>.median(): Long = get(size / 2)

    private fun List<Long>.percentile(percent: Int): Long {
        require(percent in 1..100)
        val index = ((size * percent + 99) / 100 - 1).coerceIn(indices)
        return get(index)
    }

    private fun Long.microsToMillis(): Long = (this + MICROS_PER_MILLISECOND / 2) / MICROS_PER_MILLISECOND

    private data class FixtureInput(val name: String, val html: String, val inputBytes: Long, val isCore: Boolean)

    private data class FixtureTiming(
        val name: String,
        val elapsedMicros: Long,
        val inputBytes: Long,
        val isCore: Boolean,
    )

    private data class CorpusMetrics(
        val fixtureMedians: List<FixtureTiming>,
        val totalSamplesMicros: List<Long>,
        val totalMedianMicros: Long,
        val inputBytes: Long,
        val meanArticleMicros: Long,
        val p50ArticleMicros: Long,
        val p95ArticleMicros: Long,
        val worstArticle: FixtureTiming,
        val throughputBytesPerSecond: Long,
    )

    private companion object {
        const val URL_MARKER = "\"url\":\""
        const val DEFAULT_SAMPLE_COUNT = 5
        const val MAX_SAMPLE_COUNT = 15
        const val SLOW_REPORT_LIMIT = 12
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html"
        const val CORE_MANIFEST_PATH = "fixtures/regressions/performance-core.txt"
        const val HTML_SUFFIX = ".html"
        const val PLATFORM_ARGUMENT = "KLEAD_BENCHMARK_PLATFORM"
        const val TARGET_ARGUMENT = "KLEAD_BENCHMARK_TARGET"
        const val SAMPLE_COUNT_ARGUMENT = "KLEAD_BENCHMARK_SAMPLES"
        const val CORE_MAX_MEDIAN_ARGUMENT = "KLEAD_REGRESSION_CORE_MAX_MEDIAN_MS"
        const val CORE_MAX_P95_ARGUMENT = "KLEAD_REGRESSION_CORE_MAX_P95_ARTICLE_MS"
        const val CORE_MAX_WORST_ARGUMENT = "KLEAD_REGRESSION_CORE_MAX_WORST_ARTICLE_MS"
        const val NANOS_PER_MICROSECOND = 1_000L
        const val MICROS_PER_MILLISECOND = 1_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
