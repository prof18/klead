package com.prof18.klead.fixtures

import com.prof18.klead.KleadOutput
import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import kotlin.test.Test

class FeedFlowReaderDumpTimingTest {
    @Test
    fun `print FeedFlow reader dump timing diagnostics when requested`() {
        if (!shouldPrintTimings()) return

        val reports = FeedFlowReaderDumpLoader.loadAll(requireExpectedSnapshots = false)
            .map { case ->
                val result = parseHtmlForTest(
                    html = case.rawHtml,
                    url = case.sourceUrl,
                    options = testOptions(
                        outputs = setOf(KleadOutput.HTML),
                        debug = true,
                    ),
                )
                TimingReport(
                    name = case.name,
                    parseTimeMillis = result.debug["parseTimeMillis"] as? Long ?: 0L,
                    selectedSelector = result.debug["selectedContentSelector"] as? String ?: "",
                    timings = result.debug.timingsMap(),
                    removalReasons = result.debug.removalReasons(),
                )
            }

        reports
            .sortedByDescending { it.parseTimeMillis }
            .take(SLOW_REPORT_LIMIT)
            .forEach { report ->
                println(
                    buildString {
                        append("TIMING ")
                        append(report.parseTimeMillis)
                        append("ms ")
                        append(report.name)
                        append(" selector=")
                        append(report.selectedSelector)
                        append(" top=")
                        append(report.timings.slowestPhases())
                        append(" removals=")
                        append(report.removalReasons)
                    },
                )
            }

        println("TIMING_TOTALS ${reports.phaseTotals().slowestPhases()}")
    }

    private fun Map<String, Any?>.timingsMap(): Map<String, Long> {
        val raw = this["timingsMillis"] as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val name = key as? String ?: return@mapNotNull null
            val millis = value as? Long ?: return@mapNotNull null
            name to millis
        }.toMap()
    }

    private fun Map<String, Any?>.removalReasons(): String {
        val removals = this["removals"] as? List<*> ?: return ""
        return removals
            .mapNotNull { removal ->
                removal.toString()
                    .substringAfter("reason=", missingDelimiterValue = "")
                    .substringBefore(", preview=", missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(REMOVAL_REASON_REPORT_LIMIT)
            .joinToString(", ") { "${it.key}:${it.value}" }
    }

    private fun List<TimingReport>.phaseTotals(): Map<String, Long> {
        val totals = linkedMapOf<String, Long>()
        for (report in this) {
            for ((phase, millis) in report.timings) {
                totals[phase] = totals.getOrDefault(phase, 0L) + millis
            }
        }
        return totals
    }

    private fun Map<String, Long>.slowestPhases(): String = entries
        .filter { it.value > 0L }
        .sortedByDescending { it.value }
        .take(PHASE_REPORT_LIMIT)
        .joinToString(", ") { "${it.key}=${it.value}ms" }

    private fun shouldPrintTimings(): Boolean = java.lang.Boolean.getBoolean(PRINT_TIMINGS_PROPERTY) ||
        System.getenv(PRINT_TIMINGS_ENV) == "true"

    private data class TimingReport(
        val name: String,
        val parseTimeMillis: Long,
        val selectedSelector: String,
        val timings: Map<String, Long>,
        val removalReasons: String,
    )

    private companion object {
        const val PRINT_TIMINGS_PROPERTY = "klead.printFeedFlowTimings"
        const val PRINT_TIMINGS_ENV = "KLEAD_PRINT_FEEDFLOW_TIMINGS"
        const val SLOW_REPORT_LIMIT = 12
        const val PHASE_REPORT_LIMIT = 8
        const val REMOVAL_REASON_REPORT_LIMIT = 5
    }
}
