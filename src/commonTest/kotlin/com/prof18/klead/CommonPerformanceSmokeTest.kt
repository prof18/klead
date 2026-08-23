package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.TimeSource

class CommonPerformanceSmokeTest {
    @Test
    fun printEmbeddedMediumFixtureTimings() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML, KleadOutput.MARKDOWN))
        val samples = buildList {
            repeat(SAMPLE_COUNT) {
                val mark = TimeSource.Monotonic.markNow()
                KleadParser.parseHtml(
                    html = COMMON_MEDIUM_FIXTURE,
                    url = "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array",
                    options = options,
                    parserDispatcher = dispatcher,
                )
                add(mark.elapsedNow().inWholeMilliseconds)
            }
        }.sorted()

        println("TIMING_COMMON_MEDIUM min=${samples.first()}ms median=${samples[samples.size / 2]}ms samples=$samples")
    }

    private companion object {
        const val SAMPLE_COUNT = 10
    }
}
