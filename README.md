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
documentation, upstream fixture-sync, and explicit snapshot-writing tooling remain in
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

Run the complete real-world regression corpus benchmark with connected Android and iPhone devices:

```shell
./scripts/run-regression-benchmarks
```

Each reported sample is the total parser time for one full pass over the frozen
real-world regression corpus. On every platform, the benchmark:

- loads fixture files before timing starts
- times Klead's content extraction and generation of both Markdown and cleaned HTML
- enables the same debug diagnostics
- performs one warm-up, then uses the median of three measured corpus passes
- compares that median with the platform's regression budget

Latest reference medians from 2026-08-24:

| Platform | Reference target | Median | Failure budget |
|---|---|---:|---:|
| JVM | OpenJDK 21 on Apple M1 Max | **848 ms** | 1,100 ms |
| Android | Pixel 4 XL, Android 13 | **12,930 ms** | 17,000 ms |
| iOS Simulator | iPhone 17 Pro, iOS 26.5 | **3,347 ms** | 4,000 ms |
| iOS device | iPhone 16e, iOS 26.5 | **2,798 ms** | 4,000 ms |
| macOS | Apple M1 Max, macOS 26.5.2 | **3,341 ms** | 4,000 ms |

Per-page context from that same dated reference run:

| Platform | Approx. average per page | Slowest page median | Slowest page |
|---|---:|---:|---|
| JVM | **15 ms** | **53 ms** | Android Central — Honor Magic V6 review |
| Android | **231 ms** | **958 ms** | Android Central — Honor Magic V6 review |
| iOS Simulator | **60 ms** | **251 ms** | The Verge — Sony Xperia 1 VIII review |
| iOS device | **50 ms** | **209 ms** | The Verge — Sony Xperia 1 VIII review |
| macOS | **60 ms** | **250 ms** | The Verge — Sony Xperia 1 VIII review |

The average is the full-corpus median divided by the pages in that reference run. The
slowest-page value is that page's median across the measured passes. Both will evolve
with the corpus, so they are dated performance snapshots rather than fixed corpus
statistics.

The same workload runs on JVM, a physical Android device, optimized iOS Simulator
ARM64, an optimized physical iPhone, and optimized macOS ARM64. The runner writes a
unified comparison report to `build/reports/benchmarks/regression-corpus/latest.json`.
See the [cross-platform benchmark results](docs/benchmarking.md#reference-results-2026-08-24)
for the recorded JVM, Android, iOS Simulator, physical iPhone, and macOS measurements,
plus repeated runs, device selection, baselines, and individual platform commands.

## License

Klead is released under the [Apache License 2.0](LICENSE). Vendored Defuddle test
fixtures retain their original MIT attribution in [Third-party notices](THIRD_PARTY_NOTICES.md).
