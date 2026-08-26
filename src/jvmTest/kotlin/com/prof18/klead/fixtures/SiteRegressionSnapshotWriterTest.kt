package com.prof18.klead.fixtures

import com.prof18.klead.parseHtmlForTest
import com.prof18.klead.testOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.Test

class SiteRegressionSnapshotWriterTest {
    @Test
    fun `write current Klead behavior as regression snapshots`() {
        val fixtureName = requireNotNull(System.getProperty(FIXTURE_NAME_PROPERTY)) {
            "$FIXTURE_NAME_PROPERTY must identify the captured regression"
        }
        require(FIXTURE_NAME_REGEX.matches(fixtureName)) { "Invalid fixture name: $fixtureName" }

        val resourceRoot = File(requireNotNull(System.getenv(TEST_RESOURCES_ROOT)))
        val regressionRoot = resourceRoot.resolve("fixtures/regressions")
        val inputFile = regressionRoot.resolve("input-html/$fixtureName.html")
        require(inputFile.isFile) { "Missing captured regression input: $inputFile" }

        val inputHtml = inputFile.readText()
        val result = parseHtmlForTest(
            html = inputHtml,
            url = FixtureLoader.extractUrl(fixtureName, inputHtml),
            options = testOptions(debug = true),
        )
        if (System.getenv(PRINT_TIMINGS_ENV) == "true") {
            println(
                "SITE_REGRESSION_TIMINGS name=$fixtureName " +
                    "parseTimeMillis=${result.debug["parseTimeMillis"]} " +
                    "timingsMillis=${result.debug["timingsMillis"]} " +
                    "retryAttempts=${result.debug["retryAttempts"]}",
            )
        }

        writeAtomically(
            regressionRoot.resolve("expected-markdown/$fixtureName.md"),
            snapshot(result.content.requireMarkdown()),
        )
        writeAtomically(
            regressionRoot.resolve("expected-html/$fixtureName.html"),
            snapshot(result.content.requireHtml()),
        )
    }

    private fun writeAtomically(destination: File, content: String) {
        destination.parentFile.mkdirs()
        val temporary = destination.resolveSibling(".${destination.name}.tmp")
        temporary.writeText(content)
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun snapshot(value: String): String = value
        .normalizeLineEndings()
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trimEnd() + "\n"

    private companion object {
        val FIXTURE_NAME_REGEX = Regex("[a-z0-9][a-z0-9._-]*")
        const val FIXTURE_NAME_PROPERTY = "klead.siteRegressionName"
        const val TEST_RESOURCES_ROOT = "TEST_RESOURCES_ROOT"
        const val PRINT_TIMINGS_ENV = "KLEAD_PRINT_SITE_TIMINGS"
    }
}
