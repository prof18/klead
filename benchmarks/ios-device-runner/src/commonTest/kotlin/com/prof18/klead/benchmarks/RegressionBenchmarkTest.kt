package com.prof18.klead.benchmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegressionBenchmarkTest {
    @Test
    fun readsConfigurationFromEnvironment() {
        val values = mapOf(
            "KLEAD_BENCHMARK_PLATFORM" to "ios-device",
            "KLEAD_BENCHMARK_TARGET" to "iphone17,5",
            "KLEAD_BENCHMARK_SAMPLES" to "7",
            "KLEAD_REGRESSION_CORE_MAX_MEDIAN_MS" to "4000",
            "KLEAD_REGRESSION_CORE_MAX_P95_ARTICLE_MS" to "180",
            "KLEAD_REGRESSION_CORE_MAX_WORST_ARTICLE_MS" to "260",
        )

        val config = RegressionBenchmarkConfig.fromEnvironment(values::get)

        assertEquals("ios-device", config.platform)
        assertEquals("iphone17,5", config.target)
        assertEquals(7, config.sampleCount)
        assertEquals(4_000, config.maximumCoreMedianMillis)
        assertEquals(180, config.maximumCoreP95ArticleMillis)
        assertEquals(260, config.maximumCoreWorstArticleMillis)
    }

    @Test
    fun rejectsInvalidSampleCount() {
        val values = mapOf(
            "KLEAD_BENCHMARK_PLATFORM" to "ios-device",
            "KLEAD_BENCHMARK_SAMPLES" to "0",
        )

        assertFailsWith<IllegalArgumentException> {
            RegressionBenchmarkConfig.fromEnvironment(values::get)
        }
    }

    @Test
    fun readsSourceUrlFromFixtureFrontmatter() {
        val html = """
            <!-- {"url": "https://example.com/article"} -->
            <article>Hello</article>
        """.trimIndent()

        assertEquals("https://example.com/article", sourceUrl("fixture", html))
    }

    @Test
    fun fallsBackToFixtureNameForSourceUrl() {
        assertEquals("https://example.com-story", sourceUrl("general--example.com-story", "<article>Hello</article>"))
    }
}
