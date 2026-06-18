# 10 Site Extractors

## Goal

Port Defuddle's site-specific extractors after the generic extractor and Markdown writer are stable.

This is part of the broad port target, not an optional feature family. The work can be staged by extractor risk and value.

## Policy

- Generic extraction must work without site extractors.
- Extractors should feed the same cleanup and Markdown pipeline where possible.
- Domain/site profile selectors must stay scoped to matching hosts.
- Async/network extractors must be behind options and suspend injected HTTP clients.
- Do not let extractor work block core Markdown output.
- Extractors that cannot be ported exactly must have a documented reason and fixture coverage for the closest practical behavior.

## Domain-Scoped Profiles

Site profiles are lightweight, synchronous selectors that run alongside the
generic detector and cleanup pipeline. Use them for publisher-specific scars:
branded widgets, exact layout classes, known recirculation modules, and strong
content containers.

```kotlin
interface SiteExtractor {
    val id: String
    val domains: Set<String>
    val priority: Int get() = 0

    fun matches(context: SiteExtractionContext): Boolean

    val contentSelectors: List<String> get() = emptyList()
    val preContentRemoveSelectors: List<String> get() = emptyList()
    val postContentRemoveSelectors: List<String> get() = emptyList()

    fun postProcess(content: Element, context: SiteExtractionContext, debug: MutableList<RemovalRecord>) = Unit
}
```

Default profiles are exposed through `DefaultSiteExtractors.all` and can be
replaced or extended with `DefuddleOptions.siteExtractors`:

```kotlin
Defuddle.parseHtml(
    html = html,
    url = url,
    options = DefuddleOptions(
        siteExtractors = DefaultSiteExtractors.all + MySiteExtractor,
    ),
)
```

Pipeline order:

1. Resolve matching profiles from the source URL host.
2. Apply matching `preContentRemoveSelectors` to the working document.
3. Try matching profile `contentSelectors` before generic scoring.
4. Run the generic removal pipeline.
5. Apply matching `postContentRemoveSelectors`.
6. Run profile `postProcess`, standardization, and Markdown conversion.

When `debug = true`, profile-aware runs can report:

- `siteExtractorIds`
- `profileContentSelector`
- `profileRemovals`

The initial built-in profile slice covers Motorsport, SI/MinuteMedia,
PhoneArena, Android Authority, Rolling Stone, PopCulture, Valnet, GameSpot,
GamingOnLinux, Axios, Business Insider, Mashable, BBC, BuzzFeed, Fortune,
Entrepreneur, Future/Android Central, Variety, MacRumors,
Citynews/VeneziaToday, TechCrunch, Ars Technica, Blogger/Google Blog,
JetBrains Blog, Il Post, Substack, Vox, PianetaBasket, NASA, 9to5, and
WordPress-family selectors that were previously global exact removals.

The remaining global exact selectors are generic structural, comments,
newsletter, metadata, byline, ad, share, and related-content rules. Treat new
publisher-specific cleanup as profile-scoped by default.

## Registry

Implement a small registry:

```kotlin
interface DefuddleHttpClient {
    suspend fun get(url: String): String
}

interface Extractor {
    fun canExtract(document: Document, url: String): Boolean
    suspend fun extract(document: Document, url: String, context: ExtractorContext): ExtractorResult
}
```

The registry should:

- preserve priority order
- allow disabling extractors
- expose extractor type in result
- be testable without network

`Defuddle.parseHtmlAsync` runs the parse on `DefuddleOptions.parseDispatcher`, defaulting to `Dispatchers.Default`. `Defuddle.parseHtml` remains a blocking compatibility wrapper around the suspend path. Injected HTTP clients should use their own non-blocking APIs or switch blocking I/O to an appropriate dispatcher internally.

## Suggested Priority

Low-risk static extractors first:

1. Wikipedia
2. Hacker News
3. GitHub static issue/PR pages, if product needs them
4. C2 Wiki
5. LWN, if product needs it

Port after static extractors are stable:

- YouTube transcript fetching
- Reddit comment fetching
- X/Twitter async/oEmbed fallback
- social/conversation extractors with fragile DOM assumptions

## Extractor Result

Support:

- direct content HTML
- content selector
- title
- author
- published
- site
- description
- variables

If extractor returns a content selector, run the normal pipeline against that content root.

## TDD Checklist

- `[x]` Registry priority is deterministic.
- `[x]` Disabled extractors are skipped.
- `[x]` Static extractor can return content selector.
- `[x]` Static extractor can return direct content.
- `[x]` Extractor variables appear in result.
- `[x]` Extractor output still goes through Markdown writer.
- `[x]` Network extractors use suspend injected HTTP clients and are controlled by options.

## Acceptance Gate

- `[x]` Enabled extractors improve fixture results without bypassing cleanup and Markdown rules unexpectedly.

## Commit Slices

- Registry interface.
- One static extractor.
- Fixture tests for that extractor.
- Suspend async interface with fake HTTP client.
- One network-backed extractor at a time.
