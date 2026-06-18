# 10 Extractors

## Goal

Port Defuddle's site-specific extractors after the generic extractor and Markdown writer are stable.

This is part of the broad port target, not an optional feature family. The work can be staged by extractor risk and value.

## Policy

- Generic extraction must work without custom extractors.
- Extractors should feed the same cleanup and Markdown pipeline where possible.
- Domain-scoped selectors must stay scoped to matching hosts.
- Fetching is out of scope; extractors operate on the provided HTML document.
- Do not let extractor work block core Markdown output.
- Extractors that cannot be ported exactly must have a documented reason and fixture coverage for the closest practical behavior.

## Unified Extractor Type

Extractors are the single extension type. They can provide lightweight
domain-scoped selectors that run alongside the generic detector and cleanup
pipeline, or they can return direct extracted content for pages that need
special handling. Use selector hooks for publisher-specific scars: branded
widgets, exact layout classes, known recirculation modules, and strong content
containers.

```kotlin
interface Extractor {
    val id: String
    val domains: Set<String> get() = emptySet()
    val priority: Int get() = 0

    fun matches(context: ExtractorContext): Boolean

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    fun extract(context: ExtractorContext): ExtractorResult? = null

    fun postProcess(content: Element, context: ExtractorContext, debug: MutableList<RemovalRecord>) = Unit
}
```

Default extractors are always included. Custom extractors can be added with
`DefuddleOptions.customExtractors`:

```kotlin
suspend fun renderWithExtractor(html: String, url: String) {
    Defuddle.parseHtml(
        html = html,
        url = url,
        options = DefuddleOptions(
            outputs = setOf(DefuddleOutput.MARKDOWN),
            customExtractors = listOf(MyExtractor),
        ),
    )
}
```

Pipeline order:

1. Resolve matching extractors from the source URL host and DOM signals.
2. Apply matching `preContentRemoveSelectors` to the working document.
3. Try matching extractor `contentSelectors` before generic scoring.
4. Run the generic removal pipeline.
5. Apply matching `postContentRemoveSelectors`.
6. Run extractor `postProcess`, standardization, and Markdown conversion.

When `debug = true`, extractor-aware runs can report:

- `extractorIds`
- `extractorContentSelector`
- `extractorRemovals`

The initial built-in extractor slice covers Motorsport, SI/MinuteMedia,
PhoneArena, Android Authority, Rolling Stone, PopCulture, Valnet, GameSpot,
GamingOnLinux, Axios, Business Insider, Mashable, BBC, BuzzFeed, Fortune,
Entrepreneur, Future/Android Central, Variety, MacRumors,
Citynews/VeneziaToday, TechCrunch, Ars Technica, Blogger/Google Blog,
JetBrains Blog, Il Post, Substack, Vox, PianetaBasket, NASA, 9to5, and
WordPress-family selectors that were previously global exact removals.

The remaining global exact selectors are generic structural, comments,
newsletter, metadata, byline, ad, share, and related-content rules. Treat new
publisher-specific cleanup as extractor-scoped by default.

## Registry

Implement a small registry.

The registry should:

- preserve priority order
- remain independent from network access

`Defuddle.parseHtml` is suspending only and runs CPU-heavy parsing on an
internal dispatcher. Fetching HTML belongs outside this library, and callers own
any blocking boundary they need around the suspend API.

## Suggested Priority

Low-risk static extractors first:

1. Wikipedia
2. Hacker News
3. GitHub static issue/PR pages, if product needs them
4. C2 Wiki
5. LWN, if product needs it

Port after static extractors are stable:

- additional static site-specific selectors
- social/conversation pages where useful content is present in the supplied HTML
- fragile DOM assumptions only when fixture-backed

## Extractor Result

Support:

- direct content HTML
- content selector
- title
- author
- site
- description

If extractor returns a content selector, run the normal pipeline against that content root.

## TDD Checklist

- `[x]` Registry priority is deterministic.
- `[x]` Disabled extractors are skipped.
- `[x]` Static extractor can return content selector.
- `[x]` Static extractor can return direct content.
- `[x]` Extractor output still goes through Markdown writer.
- `[x]` Extractors operate only on supplied HTML and do not fetch.

## Acceptance Gate

- `[x]` Enabled extractors improve fixture results without bypassing cleanup and Markdown rules unexpectedly.

## Commit Slices

- Registry interface.
- One static extractor.
- Fixture tests for that extractor.
- Direct-content extractor using supplied HTML.
- Keep fetching out of extractor implementations.
