package com.prof18.klead.fixtures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

data class FixtureCase(
    val name: String,
    val path: Path,
    val sourceUrl: String,
    val rawHtml: String,
    val expectedMarkdown: ExpectedResult?,
    val expectedHtml: String?,
    val categories: Set<FixtureCategory>,
)

data class FeedFlowReaderDumpCase(
    val name: String,
    val path: Path,
    val sourceUrl: String,
    val rawHtml: String,
    val expectedMarkdown: ExpectedResult?,
    val expectedHtml: String?,
)

data class ExpectedResult(val metadata: Map<String, String>, val markdownBody: String)

enum class FixtureCategory {
    CALLOUTS,
    CODE_BLOCKS,
    CODEBLOCKS,
    COMMENTS,
    CONTENT_PATTERNS,
    CUSTOM_ELEMENTS,
    ELEMENTOR,
    ELEMENTS,
    ENTRY_POINT,
    EXTRACTOR,
    EYEBROW,
    FOOTNOTES,
    GATED_CONTENT,
    GENERAL,
    HEADINGS,
    HIDDEN,
    ISSUES,
    LISTING,
    MATH,
    METADATA,
    SCORING,
    SELECTORS,
    SMALL_IMAGES,
    STANDARDIZE,
    TABLE_LAYOUT,
    UNCATEGORIZED,
}

enum class FixtureMode {
    STRICT,
    RELAXED,
    DIAGNOSTIC,
}

object FixtureLoader {
    fun loadAll(): List<FixtureCase> {
        val fixtureDir = resourceDir("defuddle-fixtures")
        return Files.list(fixtureDir).use { paths ->
            paths
                .filter { it.extension == "html" }
                .sorted(Comparator.comparing<Path, String> { it.name })
                .map { path ->
                    val name = path.nameWithoutExtension
                    val rawHtml = path.readText()
                    FixtureCase(
                        name = name,
                        path = path,
                        sourceUrl = extractUrl(name, rawHtml),
                        rawHtml = rawHtml,
                        expectedMarkdown = ExpectedResultLoader.load(name),
                        expectedHtml = ExpectedResultLoader.loadHtml(name),
                        categories = setOf(categoryFor(name)),
                    )
                }
                .toList()
        }
    }

    fun extractUrl(fixtureName: String, html: String): String {
        val match = FRONTMATTER_REGEX.find(html)
        if (match != null) {
            val parsedUrl = runCatching {
                Json.parseToJsonElement(match.groupValues[1])
                    .jsonObject["url"]
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
            if (parsedUrl != null) return parsedUrl
        }
        return fallbackUrl(fixtureName)
    }

    private fun fallbackUrl(fixtureName: String): String {
        val urlName = fixtureName.replace(Regex("""^[a-z]+--"""), "")
        return "https://$urlName"
    }

    private fun categoryFor(fixtureName: String): FixtureCategory {
        val prefix = fixtureName.substringBefore("--", missingDelimiterValue = "")
        if (prefix.isBlank()) return FixtureCategory.UNCATEGORIZED
        return runCatching {
            FixtureCategory.valueOf(prefix.uppercase().replace("-", "_"))
        }.getOrDefault(FixtureCategory.UNCATEGORIZED)
    }

    private val FRONTMATTER_REGEX = Regex("""<!--\s*(\{"url":.*?})\s*-->""")
}

object FeedFlowReaderDumpLoader {
    fun loadAll(requireExpectedSnapshots: Boolean = true): List<FeedFlowReaderDumpCase> {
        val dumpDir = resourceDir("feedflow-reader-dumps")
        return Files.list(dumpDir).use { paths ->
            paths
                .filter { it.extension == "html" }
                .sorted(Comparator.comparing<Path, String> { it.name })
                .map { path ->
                    val name = path.nameWithoutExtension
                    val rawHtml = path.readText()
                    FeedFlowReaderDumpCase(
                        name = name,
                        path = path,
                        sourceUrl = FixtureLoader.extractUrl(name, rawHtml),
                        rawHtml = rawHtml,
                        expectedMarkdown = ExpectedResultLoader.loadFeedFlowMarkdown(
                            fixtureName = name,
                            required = requireExpectedSnapshots,
                        ),
                        expectedHtml = ExpectedResultLoader.loadFeedFlowHtml(
                            fixtureName = name,
                            required = requireExpectedSnapshots,
                        ),
                    )
                }
                .toList()
        }
    }
}

object ExpectedResultLoader {
    fun load(fixtureName: String): ExpectedResult? = loadFrom(
        resourceDirectory = "defuddle-expected",
        fixtureName = fixtureName,
    )

    fun loadFrom(resourceDirectory: String, fixtureName: String): ExpectedResult? {
        val path = resourceDir(resourceDirectory).resolve("$fixtureName.md")
        if (!Files.isRegularFile(path)) return null
        return parse(path.readText())
    }

    fun loadHtml(fixtureName: String): String? = loadHtmlFrom("defuddle-expected", fixtureName)

    fun loadFeedFlowMarkdown(fixtureName: String, required: Boolean): ExpectedResult? {
        val result = loadFrom(
            resourceDirectory = "feedflow-reader-expected",
            fixtureName = fixtureName,
        )
        if (required && result == null) {
            error("Missing expected FeedFlow markdown fixture: $fixtureName.md")
        }
        return result
    }

    fun loadFeedFlowHtml(fixtureName: String, required: Boolean): String? {
        val result = loadHtmlFrom(
            resourceDirectory = "feedflow-reader-expected",
            fixtureName = fixtureName,
        )
        if (required && result == null) {
            error("Missing expected FeedFlow HTML fixture: $fixtureName.html")
        }
        return result
    }

    private fun loadHtmlFrom(resourceDirectory: String, fixtureName: String): String? {
        val path = resourceDir(resourceDirectory).resolve("$fixtureName.html")
        if (!Files.isRegularFile(path)) return null
        return path.readText()
    }

    fun parse(raw: String): ExpectedResult {
        val normalized = raw.normalizeLineEndings()
        if (!normalized.startsWith(JSON_PREAMBLE_START)) {
            return ExpectedResult(metadata = emptyMap(), markdownBody = normalized)
        }

        val end = normalized.indexOf(JSON_PREAMBLE_END, startIndex = JSON_PREAMBLE_START.length)
        if (end == -1) {
            return ExpectedResult(metadata = emptyMap(), markdownBody = normalized)
        }

        val jsonText = normalized.substring(JSON_PREAMBLE_START.length, end)
        val body = normalized
            .substring(end + JSON_PREAMBLE_END.length)
            .trimStart('\n')
            .trimEnd()

        return ExpectedResult(
            metadata = parseMetadata(jsonText),
            markdownBody = body,
        )
    }

    private fun parseMetadata(jsonText: String): Map<String, String> = runCatching {
        Json.parseToJsonElement(jsonText)
            .jsonObject
            .entries
            .mapNotNull { (key, value) ->
                val primitive = value as? JsonPrimitive
                val content = primitive?.content?.takeIf { it.isNotBlank() }
                if (content == null) null else key to content
            }
            .toMap()
    }.getOrDefault(emptyMap())

    private const val JSON_PREAMBLE_START = "```json\n"
    private const val JSON_PREAMBLE_END = "\n```"
}

object MarkdownNormalizer {
    fun minimal(markdown: String): String = markdown
        .normalizeLineEndings()
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trimEnd()
}

data class FixtureDiagnosticReport(val totalFixtures: Int, val categoryCounts: Map<FixtureCategory, Int>)

object FixtureDiagnostics {
    fun report(cases: List<FixtureCase>): FixtureDiagnosticReport {
        val counts = cases
            .flatMap { it.categories }
            .groupingBy { it }
            .eachCount()
        return FixtureDiagnosticReport(
            totalFixtures = cases.size,
            categoryCounts = counts,
        )
    }
}

private fun resourceDir(name: String): Path {
    val resource = Thread.currentThread().contextClassLoader.getResource(name)
        ?: error("Missing test resource directory: $name")
    return Path.of(URI(resource.toString()))
}

internal fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')
