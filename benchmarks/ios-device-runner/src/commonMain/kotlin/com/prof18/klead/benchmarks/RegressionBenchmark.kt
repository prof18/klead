package com.prof18.klead.benchmarks

import com.prof18.klead.Klead
import com.prof18.klead.KleadOptions
import com.prof18.klead.KleadOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.TimeSource

internal data class BenchmarkFixture(
    val name: String,
    val html: String,
    val inputBytes: Long,
    val isCore: Boolean,
)

internal data class RegressionBenchmarkConfig(
    val platform: String,
    val target: String,
    val sampleCount: Int,
    val maximumCoreMedianMillis: Long?,
    val maximumCoreP95ArticleMillis: Long?,
    val maximumCoreWorstArticleMillis: Long?,
) {
    init {
        require(platform.isNotBlank()) { "$PLATFORM_ENV must identify the benchmark platform" }
        require(sampleCount in 1..MAX_SAMPLE_COUNT) {
            "$SAMPLE_COUNT_ENV must be between 1 and $MAX_SAMPLE_COUNT"
        }
    }

    companion object {
        fun fromEnvironment(environment: (String) -> String?): RegressionBenchmarkConfig = RegressionBenchmarkConfig(
            platform = environment(PLATFORM_ENV).orEmpty(),
            target = environment(TARGET_ENV).orEmpty(),
            sampleCount = environment(SAMPLE_COUNT_ENV)?.toIntOrNull() ?: DEFAULT_SAMPLE_COUNT,
            maximumCoreMedianMillis = environment(CORE_MAX_MEDIAN_ENV)?.toLongOrNull(),
            maximumCoreP95ArticleMillis = environment(CORE_MAX_P95_ENV)?.toLongOrNull(),
            maximumCoreWorstArticleMillis = environment(CORE_MAX_WORST_ENV)?.toLongOrNull(),
        )
    }
}

internal class RegressionBenchmark(
    private val config: RegressionBenchmarkConfig,
    private val fixtures: List<BenchmarkFixture>,
) {
    suspend fun run(): BenchmarkRunResult {
        require(fixtures.isNotEmpty()) { "Expected real-world regression fixtures" }
        require(fixtures.any(BenchmarkFixture::isCore)) { "Expected performance-core fixtures" }

        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN), debug = true)
        parseCorpus(options)
        val samples = List(config.sampleCount) { parseCorpus(options) }
        val fullMetrics = samples.metrics { true }
        val coreMetrics = samples.metrics(FixtureTiming::isCore)
        val lines = buildList {
            fullMetrics.fixtureMedians.sortedByDescending(FixtureTiming::elapsedMicros)
                .take(SLOW_REPORT_LIMIT)
                .forEach { report ->
                    add(
                        "TIMING_REGRESSION_PAGE platform=${config.platform} target=${config.target} " +
                            "median=${report.elapsedMicros.microsToMillis()}ms medianMicros=${report.elapsedMicros} " +
                            "inputBytes=${report.inputBytes} core=${report.isCore} name=${report.name}",
                    )
                }
            add(fullMetrics.reportLine("TIMING_REGRESSION_CORPUS"))
            add(coreMetrics.reportLine("TIMING_REGRESSION_CORE"))
        }

        val budgetFailure = budgetFailure(
            actualMicros = coreMetrics.totalMedianMicros,
            maximumMillis = config.maximumCoreMedianMillis,
            label = "core total median",
        ) ?: budgetFailure(
                actualMicros = coreMetrics.p95ArticleMicros,
                maximumMillis = config.maximumCoreP95ArticleMillis,
                label = "core p95 article",
            ) ?: budgetFailure(
                actualMicros = coreMetrics.worstArticle.elapsedMicros,
                maximumMillis = config.maximumCoreWorstArticleMillis,
                label = "core worst article",
            )
        return BenchmarkRunResult(
            output = lines.joinToString(separator = "\n"),
            error = budgetFailure,
        )
    }

    private suspend fun parseCorpus(options: KleadOptions): List<FixtureTiming> = fixtures.map { fixture ->
        val mark = TimeSource.Monotonic.markNow()
        val result = Klead.parseHtml(
            html = fixture.html,
            url = sourceUrl(fixture.name, fixture.html),
            options = options,
        )
        val elapsedMicros = mark.elapsedNow().inWholeMicroseconds
        require(result.content.requireHtml().isNotBlank()) { "${fixture.name} produced empty HTML" }
        require(result.content.requireMarkdown().isNotBlank()) { "${fixture.name} produced empty Markdown" }
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

    private fun CorpusMetrics.reportLine(prefix: String): String =
        "$prefix platform=${config.platform} target=${config.target} fixtures=${fixtureMedians.size} " +
            "median=${totalMedianMicros.microsToMillis()}ms " +
            "samples=${totalSamplesMicros.map { it.microsToMillis() }} " +
            "inputBytes=$inputBytes meanArticle=${meanArticleMicros}us " +
            "p50Article=${p50ArticleMicros}us p95Article=${p95ArticleMicros}us " +
            "maxArticle=${worstArticle.elapsedMicros}us slowest=${worstArticle.name} " +
            "throughput=${throughputBytesPerSecond}Bps"

    private fun budgetFailure(actualMicros: Long, maximumMillis: Long?, label: String): String? =
        maximumMillis?.takeIf { actualMicros > it * MICROS_PER_MILLISECOND }?.let {
            "${config.platform} $label ${actualMicros.microsToMillis()}ms exceeded ${it}ms budget"
        }
}

internal data class BenchmarkRunResult(val output: String, val error: String?)

internal fun sourceUrl(fixtureName: String, html: String): String {
    val frontmatter = html.substringAfter("<!--", missingDelimiterValue = "")
        .substringBefore("-->", missingDelimiterValue = "")
        .trim()
    val parsedUrl = runCatching {
        Json.parseToJsonElement(frontmatter)
            .jsonObject["url"]
            ?.jsonPrimitive
            ?.content
            ?.takeIf(String::isNotBlank)
    }.getOrNull()
    return parsedUrl ?: "https://${fixtureName.replace(Regex("""^[a-z]+--"""), "")}"
}

private fun List<Long>.median(): Long = get(size / 2)

private fun List<Long>.percentile(percent: Int): Long {
    require(percent in 1..100)
    val index = ((size * percent + 99) / 100 - 1).coerceIn(indices)
    return get(index)
}

private fun Long.microsToMillis(): Long = (this + MICROS_PER_MILLISECOND / 2) / MICROS_PER_MILLISECOND

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

internal const val INPUT_DIRECTORY = "fixtures/regressions/input-html/"
internal const val CORE_MANIFEST_PATH = "fixtures/regressions/performance-core.txt"
internal const val HTML_SUFFIX = ".html"

private const val DEFAULT_SAMPLE_COUNT = 5
private const val MAX_SAMPLE_COUNT = 15
private const val SLOW_REPORT_LIMIT = 12
private const val PLATFORM_ENV = "KLEAD_BENCHMARK_PLATFORM"
private const val TARGET_ENV = "KLEAD_BENCHMARK_TARGET"
private const val SAMPLE_COUNT_ENV = "KLEAD_BENCHMARK_SAMPLES"
private const val CORE_MAX_MEDIAN_ENV = "KLEAD_REGRESSION_CORE_MAX_MEDIAN_MS"
private const val CORE_MAX_P95_ENV = "KLEAD_REGRESSION_CORE_MAX_P95_ARTICLE_MS"
private const val CORE_MAX_WORST_ENV = "KLEAD_REGRESSION_CORE_MAX_WORST_ARTICLE_MS"
private const val MICROS_PER_MILLISECOND = 1_000L
private const val MICROS_PER_SECOND = 1_000_000L
