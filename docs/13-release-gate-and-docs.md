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
suspend fun renderArticle(html: String, url: String): String {
    val result = Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(outputs = setOf(KleadOutput.MARKDOWN)),
    )
    return result.content.requireMarkdown()
}
```

Include debug:

```kotlin
suspend fun renderWithDebug(html: String, url: String) {
    val result = Klead.parseHtml(
        html = html,
        url = url,
        options = KleadOptions(
            outputs = setOf(KleadOutput.MARKDOWN),
            debug = true,
        )
    )

    val removals = result.debug["removals"] as? List<*>
    val parseTimeMillis = result.debug["parseTimeMillis"] as? Long
    removals?.forEach(::println)
}
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
- strict upstream Markdown fixture parity for supported fixtures

## Release Gate Commands

Use project-specific tasks. Actual project commands:

```text
./gradlew jvmTest -q --console=plain
./gradlew check -q --console=plain
```

Only list commands that actually exist. If lint/detekt are not configured, document that.

Also run:

- strict fixture suite
- diagnostic fixture suite
- benchmark suite
- security tests

## Release Checklist

- `[x]` API examples compile.
- `[x]` Active fixture scope is green, excluding documented math rendering/conversion differences.
- `[x]` Diagnostic suite has no unknown failures.
- `[x]` Known differences are documented.
- `[x]` Security tests pass.
- `[x]` Benchmarks recorded.
- `[x]` Upstream SHA recorded.
- `[x]` No flexmark HTML-to-Markdown dependency in production path.
- `[x]` No Compose/WebView/Graal dependency in core module.
- `[x]` Site extractor coverage status is documented.

## TDD Checklist

- `[x]` README example is covered by a test or sample compile check.
- `[x]` Known differences fixtures are covered by the fixture diagnostics and release docs test.
- `[x]` Release command docs match actual Gradle tasks.

## Acceptance Gate

- `[x]` A new developer can clone, run tests, parse an HTML string, and understand current coverage without extra context.

## Commit Slices

- README examples.
- Known differences doc.
- Coverage report.
- Release command docs.
- Final gate fixes.
