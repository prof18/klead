package com.prof18.klead.fixtures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceCoreManifestTest {
    @Test
    fun frozenPerformanceFixturesExistInTheRegressionCorpus() {
        val entries = CommonTestResources.read(CORE_MANIFEST_PATH)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .toList()
        val availableFixtures = CommonTestResources.paths
            .filter { it.startsWith(INPUT_DIRECTORY) && it.endsWith(HTML_SUFFIX) }
            .map { it.substringAfterLast('/').removeSuffix(HTML_SUFFIX) }
            .toSet()

        assertTrue(entries.isNotEmpty(), "$CORE_MANIFEST_PATH must not be empty")
        assertEquals(entries.size, entries.toSet().size, "$CORE_MANIFEST_PATH must not contain duplicates")
        assertTrue(
            availableFixtures.containsAll(entries),
            "$CORE_MANIFEST_PATH references missing fixtures: ${(entries - availableFixtures).sorted()}",
        )
    }

    private companion object {
        const val INPUT_DIRECTORY = "fixtures/regressions/input-html/"
        const val CORE_MANIFEST_PATH = "fixtures/regressions/performance-core.txt"
        const val HTML_SUFFIX = ".html"
    }
}
