# Defuddle Kotlin

Kotlin/JVM port of Defuddle focused on static HTML extraction and clean Markdown output.

## Usage

```kotlin
val result = Defuddle.parseHtml(html = html, url = url)
println(result.contentMarkdown)
```

Coroutine entry point:

```kotlin
val result = Defuddle.parseHtmlAsync(html = html, url = url)
println(result.contentMarkdown)
```

Debug output:

```kotlin
val result = Defuddle.parseHtml(
    html = html,
    url = url,
    options = DefuddleOptions(debug = true),
)

val removals = result.debug["removals"] as? List<*>
removals?.forEach(::println)
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

val result = Defuddle.parseHtml(
    html = html,
    url = "https://example.com/story",
    options = DefuddleOptions(
        extractors = DefaultExtractors.all + MyExtractor,
        debug = true,
    ),
)
```

## Scope

- Static HTML input plus source URL.
- Clean Markdown is the primary output.
- Cleaned HTML remains available as secondary/debug output.
- `parseHtml` is a blocking compatibility wrapper; `parseHtmlAsync` runs CPU-heavy parsing on an internal dispatcher.
- Fetching is out of scope; callers provide HTML and source URL.
- Domain-scoped extractors can guide content selection and cleanup before the
  generic fallback pipeline runs.
- No JavaScript execution, WebView, browser DOM, GraalJS, Compose UI, or flexmark HTML-to-Markdown conversion in the production pipeline.
- Math source data is preserved where practical, but MathML/LaTeX conversion and rendered math fidelity are out of scope.

## Verification

Configured Gradle gates:

```sh
./gradlew test -q --console=plain
./gradlew check -q --console=plain
```

`lint` and `detekt` are not configured in this project.

See [docs/README.md](docs/README.md) for the implementation plan and [docs/fixture-coverage.md](docs/fixture-coverage.md) for current fixture coverage.
