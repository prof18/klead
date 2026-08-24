# Klead

Kotlin Multiplatform library for turning static article HTML into clean Markdown.

Supported targets: JVM (including desktop macOS), iOS, and native macOS on Apple
silicon and Intel. Kotlin deprecates the `macosX64` target as of 2.3.20, so Intel
native support is transitional.

## Native release benchmarks

Run the optimized iOS Simulator arm64 and macOS arm64 benchmark smoke tests with:

```shell
./gradlew -q --console=plain nativeReleaseBenchmark
```

The task builds dedicated Kotlin/Native release test binaries, runs
`CommonPerformanceSmokeTest` on iOS Simulator and macOS, and benchmarks every captured
real-world regression fixture on macOS. Timing output is recorded under
`build/test-results/iosSimulatorArm64ReleaseBenchmarkTest`,
`build/test-results/macosArm64ReleaseBenchmarkTest`, and
`build/test-results/macosArm64ReleaseRegressionBenchmarkTest`. These release benchmarks are
intentionally separate from the normal `check` lifecycle.

The corpus benchmark prints the three-sample median and the slowest pages. Its default
macOS ARM64 median budget is 4,000 ms; the initial reference run on the development Mac
was 3,301 ms. For a tighter update-to-update comparison, record the pre-update median
and rerun with `KLEAD_REGRESSION_CORPUS_MAX_MEDIAN_MS=<budget>`. The benchmark fails
when the measured median exceeds that budget.

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

Portable parser tests, the 190-page upstream fixture corpus, and new site regressions live in `commonTest` and run through every target's test task. Only JVM reflection, documentation, upstream fixture-sync, and explicit snapshot-writing tooling remain in `jvmTest` because they exercise host tooling rather than cross-platform parser behavior.

Configured Gradle gates:

```sh
./gradlew detekt -q --console=plain
./gradlew jvmTest -q --console=plain
./gradlew check -q --console=plain
```

To turn a broken live page into a portable regression, follow the [site regression workflow](docs/site-regression-workflow.md). Capture generates Markdown and cleaned-HTML snapshots from the current engine; after the fix, regenerate those outputs from the same frozen HTML and review the diff.

`check` includes Detekt, tests, and documentation checks. Detekt builds on its
default Kotlin rule configuration and adds ktlint formatting rules.

See [docs/README.md](docs/README.md) for the implementation plan and [docs/fixture-coverage.md](docs/fixture-coverage.md) for current fixture coverage.
