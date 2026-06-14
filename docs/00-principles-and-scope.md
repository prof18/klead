# 00 Principles And Scope

## Goal

Define the boundaries before code is written. This project is a broad Kotlin/JVM port of Defuddle extraction for desktop use, but the deliverable is Markdown, not a UI.

## In Scope

- Parse static HTML with a source URL.
- Use jsoup as the HTML parser and mutable DOM.
- Extract main readable content.
- Extract metadata.
- Remove clutter.
- Standardize content structures needed for Markdown.
- Generate Markdown with Kotlin code.
- Keep cleaned HTML as a secondary/debug output.
- Use upstream Defuddle fixtures for regression coverage.
- Keep behavior test-driven and observable.
- Port the major Defuddle feature families, including site extractors and async fetch hooks where they are part of extraction behavior.

## Out Of Scope

- Compose integration.
- WebView rendering.
- GraalJS.
- Browser DOM or browser layout.
- JavaScript execution.
- Full CSS cascade or `getComputedStyle` parity.
- flexmark HTML-to-Markdown conversion in the core pipeline.
- Exact byte-for-byte parity with Defuddle TypeScript serialization.
- Math rendering fidelity.
- MathML-to-LaTeX or LaTeX-to-MathML conversion.

## Design Principles

- Port behavior, not syntax.
- Build a small DOM compatibility layer before porting algorithms.
- Standardize HTML before generating Markdown.
- Keep Markdown deterministic and conservative.
- Prefer content preservation over layout fidelity.
- Use upstream fixtures as the regression oracle.
- Classify differences instead of hand-waving them.
- Keep commits small and self-contained.

## Product Contract

The library should expose an API like:

```kotlin
val result = Defuddle.parseHtml(
    html = html,
    url = url,
    options = DefuddleOptions(markdown = true)
)

println(result.contentMarkdown)
```

Minimum result fields:

- `contentMarkdown`
- `contentHtml`
- `title`
- `description`
- `domain`
- `favicon`
- `image`
- `language`
- `published`
- `author`
- `site`
- `wordCount`
- `parseTimeMillis`
- `metaTags`
- `schemaOrgData`
- `debug`

## Porting Boundaries

The target is a full practical port of Defuddle's extraction behavior. Implementation can be staged, but the endpoint should cover the majority of upstream behavior:

- generic article extraction
- metadata extraction
- schema.org fallback
- removal pipeline
- HTML standardization
- Markdown output
- upstream fixture coverage
- site-specific extractors
- async/network extractors through injected clients

The explicit exception is math rendering/conversion. Preserve math source data when practical, but do not block the port on MathML-to-LaTeX, LaTeX-to-MathML, or rendered math fidelity.

Client-rendered pages with no static content remain inherently limited without JavaScript execution. The port should handle async extractor fallbacks where Defuddle has them and where they can be implemented with injected HTTP clients.

## TDD Checklist

- `[ ]` Test that public API can parse an empty HTML string without crashing.
- `[ ]` Test that public API can parse a minimal article and return Markdown.
- `[ ]` Test that unsupported browser/CSS behavior is documented and does not crash.
- `[ ]` Test that `contentHtml` remains available even when Markdown is the primary output.

## Acceptance Gate

- `[ ]` Scope is reflected in README and package docs.
- `[ ]` Compose/WebView/Graal/flexmark HTML-to-Markdown are not listed as core requirements.
- `[ ]` The only explicit feature-family exclusion is math rendering/conversion.

## Commit Slices

- Scope document update.
- Empty API contract.
- README with supported and unsupported behavior.
