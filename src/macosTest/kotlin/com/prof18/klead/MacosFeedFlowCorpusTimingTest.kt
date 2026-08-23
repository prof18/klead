package com.prof18.klead

import com.prof18.klead.internal.KleadParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.posix.SEEK_END
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.getenv
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.rewind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

@OptIn(ExperimentalForeignApi::class)
class MacosFeedFlowCorpusTimingTest {
    @Test
    fun printFeedFlowCorpusTimingsWhenRequested() = runTest(timeout = 10.minutes) {
        if (environment(PRINT_TIMINGS_ENV) != "true") return@runTest

        val projectDir = checkNotNull(environment(PROJECT_DIR_ENV))
        val target = environment(NATIVE_TARGET_ENV).orEmpty()
        val fixtureDir = "$projectDir/src/jvmTest/resources/feedflow-reader-dumps"
        val fixturePaths = listHtmlFiles(fixtureDir)
        assertEquals(EXPECTED_FIXTURE_COUNT, fixturePaths.size, "unexpected FeedFlow corpus size")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val options = KleadOptions(outputs = setOf(KleadOutput.HTML), debug = true)
        val wallMark = TimeSource.Monotonic.markNow()
        val reports = fixturePaths.map { path ->
            val html = readFile(path)
            val name = path.substringAfterLast('/').removeSuffix(".html")
            val result = KleadParser.parseHtml(
                html = html,
                url = sourceUrl(name, html),
                options = options,
                parserDispatcher = dispatcher,
            )
            CorpusTiming(
                name = name,
                parseTimeMillis = result.debug["parseTimeMillis"] as? Long ?: 0L,
                phases = result.debug.timingsMap(),
            )
        }
        val wallMillis = wallMark.elapsedNow().inWholeMilliseconds

        reports.sortedByDescending { it.parseTimeMillis }
            .take(SLOW_REPORT_LIMIT)
            .forEach { report ->
                println("TIMING_MACOS_PAGE target=$target time=${report.parseTimeMillis}ms name=${report.name}")
            }
        println(
            "TIMING_MACOS_CORPUS target=$target fixtures=${reports.size} wall=${wallMillis}ms " +
                reports.phaseTotals().slowestPhases(),
        )
    }

    private fun Map<String, Any?>.timingsMap(): Map<String, Long> {
        val raw = this["timingsMillis"] as? Map<*, *> ?: return emptyMap()
        return raw.mapNotNull { (key, value) ->
            val phase = key as? String ?: return@mapNotNull null
            val millis = value as? Long ?: return@mapNotNull null
            phase to millis
        }.toMap()
    }

    private fun List<CorpusTiming>.phaseTotals(): Map<String, Long> {
        val totals = linkedMapOf<String, Long>()
        for (report in this) {
            for ((phase, millis) in report.phases) {
                totals[phase] = (totals[phase] ?: 0L) + millis
            }
        }
        return totals
    }

    private fun Map<String, Long>.slowestPhases(): String = entries
        .filter { it.value > 0L }
        .sortedByDescending { it.value }
        .take(PHASE_REPORT_LIMIT)
        .joinToString(", ") { "${it.key}=${it.value}ms" }

    private fun sourceUrl(name: String, html: String): String = URL_FRONTMATTER.find(html)
        ?.groupValues
        ?.get(1)
        ?: "https://${name.removePrefix("general--")}"

    private fun environment(name: String): String? = getenv(name)?.toKString()

    private fun listHtmlFiles(directoryPath: String): List<String> {
        val directory = checkNotNull(opendir(directoryPath)) { "cannot open $directoryPath" }
        return try {
            buildList {
                while (true) {
                    val entry = readdir(directory) ?: break
                    val name = entry.pointed.d_name.toKString()
                    if (name.endsWith(".html")) add("$directoryPath/$name")
                }
            }.sorted()
        } finally {
            closedir(directory)
        }
    }

    private fun readFile(path: String): String {
        val file = checkNotNull(fopen(path, "rb")) { "cannot open $path" }
        return try {
            check(fseek(file, 0, SEEK_END) == 0) { "cannot seek $path" }
            val length = ftell(file)
            check(length >= 0) { "cannot determine size of $path" }
            rewind(file)
            val bytes = ByteArray(length.toInt())
            val bytesRead = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
            }
            check(bytesRead == bytes.size) { "read $bytesRead of ${bytes.size} bytes from $path" }
            bytes.decodeToString()
        } finally {
            fclose(file)
        }
    }

    private data class CorpusTiming(
        val name: String,
        val parseTimeMillis: Long,
        val phases: Map<String, Long>,
    )

    private companion object {
        val URL_FRONTMATTER = Regex("""<!--\s*\{"url":"([^"]+)"}\s*-->""")
        const val EXPECTED_FIXTURE_COUNT = 56
        const val SLOW_REPORT_LIMIT = 12
        const val PHASE_REPORT_LIMIT = 8
        const val PRINT_TIMINGS_ENV = "KLEAD_PRINT_FEEDFLOW_TIMINGS"
        const val PROJECT_DIR_ENV = "KLEAD_PROJECT_DIR"
        const val NATIVE_TARGET_ENV = "KLEAD_NATIVE_TARGET"
    }
}
