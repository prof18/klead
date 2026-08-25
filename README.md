# Klead

Klead is a Kotlin Multiplatform library for extracting the main content from web
pages. Give it a page's HTML and source URL, and it removes navigation, ads,
related-content blocks, and other clutter before returning clean Markdown, cleaned
HTML, or both—depending on the outputs you request.

Supported targets: JVM, Android, iOS, and native macOS on Apple
silicon and Intel. Kotlin deprecates the `macosX64` target as of 2.3.20, so Intel
native support is transitional.

## Alpha status

Klead is currently in alpha. Its API and output may change as it is tested against
more sites.

It is already being used as an experimental article-extraction engine in
[FeedFlow](https://www.feedflow.dev/), the open-source RSS reader available for
Android, iOS, macOS, Windows, and Linux. The results have been very promising so
far. Klead will evolve toward a stable release based on real-world FeedFlow usage
and the reports received through the [FeedFlow issue
tracker](https://github.com/prof18/feed-flow/issues). You can also browse the
[FeedFlow source code](https://github.com/prof18/feed-flow).

## Features

- Extracts the main article content and metadata from static HTML.
- Removes common page clutter and unsafe markup.
- Returns Markdown, cleaned HTML, or both in a single parse.
- Supports domain-scoped custom extractors for sites that need specialized handling.
- Runs from shared Kotlin code across JVM, Android, iOS, and macOS targets.

## Inspiration and credit

Klead is heavily inspired by [Defuddle](https://github.com/kepano/defuddle), an
excellent project for extracting readable content from web pages. Defuddle's ideas,
extraction behavior, and fixture corpus provided the foundation and reference point
for much of Klead's development. Many thanks to Steph Ango and the Defuddle
contributors for their work.

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

Choose the output that fits your use case. To request both Markdown and cleaned HTML:

```kotlin
suspend fun parseArticle(html: String, url: String): KleadResult =
    Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(
            outputs = setOf(KleadOutput.MARKDOWN, KleadOutput.HTML),
        ),
    )
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

Portable parser tests, the upstream fixture corpus, and new site regressions live in
`commonTest` and run through every target's test task. Only JVM reflection,
documentation, fixture-maintenance, and explicit snapshot-writing tooling remain in
`jvmTest` because they exercise host tooling rather than cross-platform parser behavior.

Configured Gradle gates:

```sh
./gradlew detekt -q --console=plain
./gradlew jvmTest -q --console=plain
./gradlew check -q --console=plain
```

To turn a broken live page into a portable regression, use the shared
`klead-regression-fix` skill. The underlying commands are
`./scripts/capture-site-regression '<url>' <name>` and
`./scripts/update-site-regression <name>`. Capture preserves the current Markdown and
cleaned-HTML behavior before the engine fix; update regenerates both expectations from
the same frozen input HTML after the fix.

`check` includes Detekt, tests, and documentation checks. Detekt builds on its
default Kotlin rule configuration and adds ktlint formatting rules.

## Documentation

- [Markdown output policy](docs/markdown-policy.md)
- [Security policy](docs/security-policy.md)
- [Cross-platform benchmarking](docs/benchmarking.md)

## Cross-platform regression benchmarks

Klead benchmarks the complete extraction pipeline on JVM, Android, iOS Simulator,
physical iPhone, and macOS. Each run reports two complementary cohorts:

- A [frozen 56-article performance core](src/commonTest/resources/fixtures/regressions/performance-core.txt)
  provides stable, apples-to-apples regression gates for total throughput, p95 article
  latency, and the slowest article.
- The growing real-world regression corpus reports current mean, p50, p95, worst-case,
  and input-size throughput without treating added fixtures as a parser slowdown.

Run the complete suite with connected Android and iPhone devices:

```shell
./scripts/run-regression-benchmarks
```

Current reference results for the stable performance core:

| Platform | Core pass | p95 article | Slowest article | Core budget |
|---|---:|---:|---:|---:|
| JVM | **848 ms** | 46 ms | 55 ms | 1,100 ms |
| Android | **12,930 ms** | 669 ms | 944 ms | 17,000 ms |
| iOS Simulator | **3,347 ms** | 166 ms | 237 ms | 4,000 ms |
| iOS device | **2,798 ms** | 132 ms | 197 ms | 4,000 ms |
| macOS | **3,341 ms** | 168 ms | 239 ms | 4,000 ms |

The core pass is one complete pass over all 56 stable fixtures. The p95 and slowest
values are per-article medians across repeated passes. Results are target-specific;
compare later runs of the same target rather than comparing platforms with each other.

The runner performs one warm-up and five measured passes, then writes the unified
schema-v2 report to `build/reports/benchmarks/regression-corpus/latest.json`. See the
[benchmarking guide](docs/benchmarking.md) for metric definitions, dated reference
results, device setup, repeated runs, and individual platform commands. The tracked
[platform budgets](benchmarks/regression-corpus-baselines.properties) are the
machine-readable source for the stable-core gates.

## License

Klead is released under the [Apache License 2.0](LICENSE). Vendored Defuddle test
fixtures retain their original MIT attribution in [Third-party notices](THIRD_PARTY_NOTICES.md).
