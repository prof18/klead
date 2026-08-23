# Klead

Kotlin Multiplatform library for turning static article HTML into clean Markdown.

Supported targets: JVM (including desktop macOS), iOS, and native Apple-silicon macOS.

## Usage

```kotlin
suspend fun renderArticle(html: String, url: String): String {
    val result = Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
    )
    return result.content.requireMarkdown()
}
```

Request only the outputs you need:

```kotlin
suspend fun renderMarkdown(html: String, url: String): String? {
    val result = Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
    )
    return result.content.markdown
}
```

Debug output:

```kotlin
suspend fun renderWithDebug(html: String, url: String) {
    val result = Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(
            outputs = setOf(KleadOutput.MARKDOWN),
            debug = true,
        ),
    )

    val removals = result.debug["removals"] as? List<*>
    val parseTimeMillis = result.debug["parseTimeMillis"] as? Long
    removals?.forEach(::println)
}
```

Custom extractors can add domain-scoped content and removal selectors without
making those selectors global:

```kotlin
object MyExtractor : Extractor {
    override val id = "my-site"
    override val domains = setOf("example.com")
    override val contentSelectors = listOf("article.story")
    override val postContentRemoveSelectors = listOf(".related-widget")
}

suspend fun renderStory(html: String): String {
    val result = Klead.parseHtml(
        html = html,
        url = "https://example.com/story",
        options = KleadOptions(
            outputs = setOf(KleadOutput.MARKDOWN),
            customExtractors = listOf(MyExtractor),
            debug = true,
        ),
    )
    return result.content.requireMarkdown()
}
```

## Scope

- Static HTML input plus source URL.
- Clean Markdown is the primary output.
- Cleaned HTML is available when requested through `KleadOptions.outputs`.
- `parseHtml` is suspending only and runs CPU-heavy parsing on an internal dispatcher.
- Blocking callers own their blocking boundary, for example by calling the suspend API from their own `runBlocking` scope.
- Fetching is out of scope; callers provide HTML and source URL.
- Domain-scoped extractors can guide content selection and cleanup before the
  generic fallback pipeline runs.
- No JavaScript execution, WebView, browser DOM, GraalJS, Compose UI, or flexmark HTML-to-Markdown conversion in the production pipeline.
- Math source data is preserved where practical, but MathML/LaTeX conversion and rendered math fidelity are out of scope.

## Verification

Configured Gradle gates:

```sh
./gradlew detekt -q --console=plain
./gradlew test -q --console=plain
./gradlew check -q --console=plain
```

`check` includes Detekt, tests, and documentation checks. Detekt builds on its
default Kotlin rule configuration and adds ktlint formatting rules.

See [docs/README.md](docs/README.md) for the implementation plan and [docs/fixture-coverage.md](docs/fixture-coverage.md) for current fixture coverage.
