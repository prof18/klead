# 10 Site Extractors

## Goal

Port Defuddle's site-specific extractors after the generic extractor and Markdown writer are stable.

This is part of the broad port target, not an optional feature family. The work can be staged by extractor risk and value.

## Policy

- Generic extraction must work without site extractors.
- Extractors should feed the same cleanup and Markdown pipeline where possible.
- Async/network extractors must be behind options and injected HTTP clients.
- Do not let extractor work block core Markdown output.
- Extractors that cannot be ported exactly must have a documented reason and fixture coverage for the closest practical behavior.

## Registry

Implement a small registry:

```kotlin
interface Extractor {
    fun canExtract(document: Document, url: String): Boolean
    fun extract(document: Document, url: String): ExtractorResult
}
```

The registry should:

- preserve priority order
- allow disabling extractors
- expose extractor type in result
- be testable without network

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
- `[x]` Network extractors use injected HTTP clients and are controlled by options.

## Acceptance Gate

- `[x]` Enabled extractors improve fixture results without bypassing cleanup and Markdown rules unexpectedly.

## Commit Slices

- Registry interface.
- One static extractor.
- Fixture tests for that extractor.
- Async interface with fake HTTP client.
- One network-backed extractor at a time.
