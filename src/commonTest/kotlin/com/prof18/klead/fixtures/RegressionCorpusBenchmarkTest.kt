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

        val coreFixtureNames = loadCoreFixtureNames()
        val fixtures = CommonTestResources.paths
            .filter { it.startsWith(INPUT_DIRECTORY) && it.endsWith(HTML_SUFFIX) }
            .filterNot { it.substringAfterLast('/').startsWith("harness--") }
            .sorted()
            .map { path ->
                val html = CommonTestResources.read(path)
                val name = path.substringAfterLast('/').removeSuffix(HTML_SUFFIX)
                FixtureInput(
                    name = name,
                    html = html,
                    inputBytes = html.encodeToByteArray().size.toLong(),
                    isCore = name in coreFixtureNames,
                )
            }
        assertTrue(fixtures.isNotEmpty(), "Expected real-world regression fixtures")
        assertCoreFixtureCoverage(fixtures.map(FixtureInput::name).toSet(), coreFixtureNames)

        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
        parseCorpus(fixtures, dispatcher, options)
        val samples = List(sampleCount) { parseCorpus(fixtures, dispatcher, options) }

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

        assertBudget(platform, coreMetrics.totalMedianMicros, CORE_MAX_MEDIAN_ENV, "core total median")
        assertBudget(platform, coreMetrics.p95ArticleMicros, CORE_MAX_P95_ENV, "core p95 article")
        assertBudget(platform, coreMetrics.worstArticle.elapsedMicros, CORE_MAX_WORST_ENV, "core worst article")
    }

    private suspend fun parseCorpus(
        fixtures: List<FixtureInput>,
        dispatcher: CoroutineDispatcher,
        options: KleadOptions,
    ): List<FixtureTiming> = fixtures.map { fixture ->
        val mark = TimeSource.Monotonic.markNow()
        val result = KleadParser.parseHtml(
            html = fixture.html,
            url = FixtureLoader.extractUrl(fixture.name, fixture.html),
            options = options,
            parserDispatcher = dispatcher,
        )
        val elapsedMicros = mark.elapsedNow().inWholeMicroseconds
        assertTrue(result.content.requireHtml().isNotBlank(), "${fixture.name} produced empty HTML")
        assertTrue(result.content.requireMarkdown().isNotBlank(), "${fixture.name} produced empty Markdown")
        FixtureTiming(
            name = fixture.name,
            elapsedMicros = elapsedMicros,
            inputBytes = fixture.inputBytes,
            isCore = fixture.isCore,
        )
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

    private fun assertBudget(platform: String, actualMicros: Long, environmentName: String, label: String) {
        commonTestEnvironment(environmentName)?.toLongOrNull()?.let { maximumMillis ->
            assertTrue(
                actualMicros <= maximumMillis * MICROS_PER_MILLISECOND,
                "$platform $label ${actualMicros.microsToMillis()}ms exceeded ${maximumMillis}ms budget",
            )
        }
    }

    private fun loadCoreFixtureNames(): Set<String> {
        val entries = CommonTestResources.read(CORE_MANIFEST_PATH)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .toList()
        require(entries.size == entries.toSet().size) { "$CORE_MANIFEST_PATH contains duplicate fixtures" }
        return entries.toSet()
    }

    private fun assertCoreFixtureCoverage(availableFixtureNames: Set<String>, coreFixtureNames: Set<String>) {
        val missing = coreFixtureNames - availableFixtureNames
        assertTrue(coreFixtureNames.isNotEmpty(), "$CORE_MANIFEST_PATH must not be empty")
        assertTrue(missing.isEmpty(), "$CORE_MANIFEST_PATH references missing fixtures: ${missing.sorted()}")
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
        const val DEFAULT_SAMPLE_COUNT = 5
        const val MAX_SAMPLE_COUNT = 15
        const val SLOW_REPORT_LIMIT = 12
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html/"
        const val CORE_MANIFEST_PATH = "fixtures/regressions/performance-core.txt"
        const val HTML_SUFFIX = ".html"
        const val RUN_BENCHMARK_ENV = "KLEAD_RUN_REGRESSION_BENCHMARK"
        const val PLATFORM_ENV = "KLEAD_BENCHMARK_PLATFORM"
        const val TARGET_ENV = "KLEAD_BENCHMARK_TARGET"
        const val SAMPLE_COUNT_ENV = "KLEAD_BENCHMARK_SAMPLES"
        const val CORE_MAX_MEDIAN_ENV = "KLEAD_REGRESSION_CORE_MAX_MEDIAN_MS"
        const val CORE_MAX_P95_ENV = "KLEAD_REGRESSION_CORE_MAX_P95_ARTICLE_MS"
        const val CORE_MAX_WORST_ENV = "KLEAD_REGRESSION_CORE_MAX_WORST_ARTICLE_MS"
        const val MICROS_PER_MILLISECOND = 1_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
