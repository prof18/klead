package com.prof18.klead.fixtures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class FixtureCase(
    val name: String,
    val path: String,
    val sourceUrl: String,
    val rawHtml: String,
    val expectedMarkdown: ExpectedResult?,
    val expectedHtml: String?,
    val categories: Set<FixtureCategory>,
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

object FixtureLoader {
    fun loadAll(): List<FixtureCase> = CommonTestResources.paths
        .filter { it.startsWith(FIXTURE_PREFIX) && it.endsWith(HTML_SUFFIX) }
        .sorted()
        .map { path ->
            val name = path.substringAfterLast('/').removeSuffix(HTML_SUFFIX)
            val rawHtml = CommonTestResources.read(path)
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

    private const val FIXTURE_PREFIX = "fixtures/defuddle/input-html/"
    private const val HTML_SUFFIX = ".html"
}

object ExpectedResultLoader {
    fun load(fixtureName: String): ExpectedResult? = loadFrom(
        resourceDirectory = EXPECTED_MARKDOWN_DIRECTORY,
        fixtureName = fixtureName,
    )

    fun loadFrom(resourceDirectory: String, fixtureName: String): ExpectedResult? {
        val path = "$resourceDirectory/$fixtureName.md"
        if (path !in CommonTestResources.paths) return null
        return parse(CommonTestResources.read(path))
    }

    fun loadHtml(fixtureName: String): String? = loadHtmlFrom(EXPECTED_HTML_DIRECTORY, fixtureName)

    private fun loadHtmlFrom(resourceDirectory: String, fixtureName: String): String? {
        val path = "$resourceDirectory/$fixtureName.html"
        if (path !in CommonTestResources.paths) return null
        return CommonTestResources.read(path)
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
    private const val EXPECTED_MARKDOWN_DIRECTORY = "fixtures/defuddle/expected-markdown"
    private const val EXPECTED_HTML_DIRECTORY = "fixtures/defuddle/expected-html"
}

object MarkdownNormalizer {
    fun minimal(markdown: String): String = markdown
        .normalizeLineEndings()
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trimEnd()
}

internal fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')
