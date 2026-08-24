package com.prof18.klead.fixtures

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixtureSyncTest {
    @Test
    fun `sync copies fixtures and split expected files without touching regressions`() {
        val root = Files.createTempDirectory("defuddle-sync-test")
        val upstream = root.resolve("upstream")
        val fixtures = root.resolve("resources/fixtures/defuddle/input-html").apply { createDirectories() }
        val expectedMarkdown = root.resolve("resources/fixtures/defuddle/expected-markdown").apply {
            createDirectories()
        }
        val expectedHtml = root.resolve("resources/fixtures/defuddle/expected-html").apply { createDirectories() }
        val regressions = root.resolve("resources/fixtures/regressions/expected-markdown").apply {
            createDirectories()
        }
        val shaFile = root.resolve("resources/defuddle-upstream.sha")
        val reportFile = root.resolve("sync-report.md")

        upstream.resolve("tests/fixtures").createDirectories()
        upstream.resolve("tests/expected").createDirectories()
        upstream.resolve("tests/fixtures/new.html").writeText("<html>new</html>")
        upstream.resolve("tests/fixtures/changed.html").writeText("<html>changed upstream</html>")
        upstream.resolve("tests/expected/new.md").writeText("new")
        upstream.resolve("tests/expected/changed.md").writeText("changed upstream")
        upstream.resolve("tests/expected/new.html").writeText("<article>new</article>")

        fixtures.resolve("changed.html").writeText("<html>old local</html>")
        fixtures.resolve("removed.html").writeText("<html>removed</html>")
        expectedMarkdown.resolve("changed.md").writeText("old local")
        expectedMarkdown.resolve("removed.md").writeText("removed")
        regressions.resolve("changed.md").writeText("keep regression")
        shaFile.writeText("old-sha\n")

        val report = FixtureSync.sync(
            upstreamRoot = upstream,
            fixtureDestination = fixtures,
            expectedMarkdownDestination = expectedMarkdown,
            expectedHtmlDestination = expectedHtml,
            shaFile = shaFile,
            reportFile = reportFile,
            newSha = "new-sha",
        )

        assertEquals("old-sha", report.previousSha)
        assertEquals("new-sha", shaFile.readText().trim())
        assertEquals(listOf("new.html"), report.addedFixtures)
        assertEquals(listOf("removed.html"), report.removedFixtures)
        assertEquals(listOf("changed.html"), report.changedFixtures)
        assertEquals(listOf("new.html", "new.md"), report.addedExpected)
        assertEquals("keep regression", regressions.resolve("changed.md").readText())
        assertTrue(reportFile.readText().contains("Changed fixtures"))
    }

    @Test
    fun `missing upstream directory fails clearly`() {
        val root = Files.createTempDirectory("defuddle-sync-missing")

        val error = assertFailsWith<IllegalArgumentException> {
            FixtureSync.sync(
                upstreamRoot = root.resolve("missing"),
                fixtureDestination = root.resolve("fixtures"),
                expectedMarkdownDestination = root.resolve("expected-markdown"),
                expectedHtmlDestination = root.resolve("expected-html"),
                shaFile = root.resolve("sha"),
                reportFile = root.resolve("report"),
                newSha = "sha",
            )
        }

        assertTrue(error.message.orEmpty().contains("Missing upstream fixtures directory"))
    }
}
