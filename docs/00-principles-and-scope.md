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
- Port the major Defuddle feature families that operate on supplied static HTML, including site extractors.

## Out Of Scope

- Compose integration.
- WebView rendering.
- GraalJS.
- Browser DOM or browser layout.
- JavaScript execution.
- Full CSS cascade or `getComputedStyle` parity.
- flexmark HTML-to-Markdown conversion in the core pipeline.
- Exact byte-for-byte parity with Defuddle TypeScript serialization.
- Fetching HTML or auxiliary remote content.
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
suspend fun renderArticle(html: String, url: String): String {
    val result = Defuddle.parseHtml(
        html = html,
        url = url,
        options = DefuddleOptions(outputs = setOf(DefuddleOutput.MARKDOWN))
    )

    return result.content.requireMarkdown()
}
```

Minimum result fields:

- `content`
- `metadata`
- `debug` (`parseTimeMillis` is included here when debug mode is enabled)

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

The explicit exception is math rendering/conversion. Preserve math source data when practical, but do not block the port on MathML-to-LaTeX, LaTeX-to-MathML, or rendered math fidelity.

Client-rendered pages with no static content remain inherently limited without JavaScript execution or fetching. Callers are responsible for supplying the HTML to parse.

## TDD Checklist

- `[x]` Test that public API can parse an empty HTML string without crashing.
- `[x]` Test that public API can parse a minimal article and return Markdown.
- `[x]` Test that unsupported browser/CSS behavior is documented and does not crash.
- `[x]` Test that HTML and Markdown outputs can be requested independently.

## Acceptance Gate

- `[x]` Scope is reflected in README and package docs.
- `[x]` Compose/WebView/Graal/flexmark HTML-to-Markdown are not listed as core requirements.
- `[x]` The only explicit feature-family exclusion is math rendering/conversion.

## Commit Slices

- Scope document update.
- Empty API contract.
- README with supported and unsupported behavior.
