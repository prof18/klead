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

## Scope

- Static HTML input plus source URL.
- Clean Markdown is the primary output.
- Cleaned HTML remains available as secondary/debug output.
- `parseHtml` is a blocking compatibility wrapper; `parseHtmlAsync` runs parsing on `DefuddleOptions.parseDispatcher`, which defaults to `Dispatchers.Default`.
- Network-capable extractors use suspend injected HTTP clients. No built-in HTTP client is shipped.
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
