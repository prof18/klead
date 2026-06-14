# 13 Release Gate And Docs

## Goal

Prepare a usable internal release of the Kotlin Markdown extractor with clear coverage, limitations, and commands.

## Required Docs

- README with API examples.
- Known differences from Defuddle TypeScript.
- Fixture coverage report.
- Security policy for output sanitization.
- Markdown output policy.
- Upstream sync process.
- Release scope.

## README Examples

Include:

```kotlin
val result = Defuddle.parseHtml(html, url)
println(result.contentMarkdown)
```

Include debug:

```kotlin
val result = Defuddle.parseHtml(
    html = html,
    url = url,
    options = DefuddleOptions(debug = true)
)

result.debug?.removals?.forEach(::println)
```

Include known limitation notes:

- no JS execution
- no browser layout
- complex table fallback
- math rendering/conversion exclusion
- site extractor coverage status

## Known Differences

Track:

- jsoup serialization differences
- Markdown table fallback for complex tables
- MathML fallback when no LaTeX exists, because math conversion/rendering is excluded
- no browser-computed styles
- not-yet-ported async extractors, if any remain before a staged release
- intentionally cleaner Markdown than upstream Turndown in some cases

## Release Gate Commands

Use project-specific tasks. Default candidates:

```text
./gradlew test -q --console=plain
./gradlew lint -q --console=plain
./gradlew detekt -q --console=plain
```

Only list commands that actually exist. If lint/detekt are not configured, document that.

Also run:

- strict fixture suite
- diagnostic fixture suite
- benchmark suite
- security tests

## Release Checklist

- `[ ]` API examples compile.
- `[ ]` Active fixture scope is green, excluding documented math rendering/conversion differences.
- `[ ]` Diagnostic suite has no unknown failures.
- `[ ]` Known differences are documented.
- `[ ]` Security tests pass.
- `[ ]` Benchmarks recorded.
- `[ ]` Upstream SHA recorded.
- `[ ]` No flexmark HTML-to-Markdown dependency in production path.
- `[ ]` No Compose/WebView/Graal dependency in core module.
- `[ ]` Site extractor coverage status is documented.

## TDD Checklist

- `[ ]` README example is covered by a test or sample compile check.
- `[ ]` Known differences fixtures are covered.
- `[ ]` Release command docs match actual Gradle tasks.

## Acceptance Gate

- `[ ]` A new developer can clone, run tests, parse an HTML string, and understand current coverage without extra context.

## Commit Slices

- README examples.
- Known differences doc.
- Coverage report.
- Release command docs.
- Final gate fixes.
