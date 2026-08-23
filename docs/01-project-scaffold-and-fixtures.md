# 01 Project Scaffold And Fixtures

## Goal

Create a Kotlin/JVM project that can run tests and load upstream Defuddle fixtures before any real extraction logic is implemented.

## Deliverables

- Gradle/Kotlin JVM module.
- Public API skeleton.
- Test framework.
- Vendored or synced upstream Defuddle fixtures.
- Fixture harness that can discover fixtures and expected outputs.
- Pinned upstream Defuddle commit SHA.

## Suggested Project Layout

```text
klead/
  build.gradle.kts
  settings.gradle.kts
  README.md
  docs/
  src/commonMain/kotlin/com/prof18/klead/
  src/jvmTest/kotlin/com/prof18/klead/
  src/jvmTest/resources/defuddle-fixtures/
  src/jvmTest/resources/defuddle-expected/
  src/jvmTest/resources/kotlin-expected/
```

## Dependencies

Core:

```kotlin
implementation("org.jsoup:jsoup:<verified-version>")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<verified-version>")
```

Tests:

```kotlin
testImplementation(kotlin("test"))
```

Use project conventions if the repo already has a preferred test stack.

## Fixture Import

Copy from upstream Defuddle:

```text
tests/fixtures/*.html
tests/expected/*.md
tests/expected/*.html
```

Also store:

```text
src/jvmTest/resources/defuddle-upstream.sha
src/jvmTest/resources/defuddle-license.txt
```

Do not modify upstream fixture content. Kotlin-specific expected output belongs in:

```text
src/jvmTest/resources/kotlin-expected/
```

Imported upstream corpus:

- SHA: `9db72600a0cfc568eafb31e85ef68ba16add072e`
- HTML fixtures: 190
- Expected Markdown files: 190
- Expected HTML files: 3
- License: `src/jvmTest/resources/defuddle-license.txt`

## Fixture Harness

Implement:

- `FixtureCase`
- `FixtureLoader`
- `ExpectedResultLoader`
- `FixtureCategory`
- `FixtureMode`

`FixtureCase` should include:

- fixture name
- fixture path
- source URL
- raw HTML
- expected upstream Markdown, if present
- expected upstream HTML, if present
- category labels

## URL Extraction

Many fixtures include:

```html
<!-- {"url":"https://example.com/article"} -->
```

TDD cases:

- extracts URL from frontmatter comment
- falls back to filename-derived URL
- handles missing or malformed frontmatter safely

## Expected Markdown Loading

Upstream expected files use a JSON preamble followed by Markdown content.

TDD cases:

- parses JSON preamble
- extracts title/author/site/published
- extracts Markdown body
- supports files without preamble if encountered

## Normalization

Create test helpers:

- normalize line endings to `\n`
- trim trailing whitespace
- trim final file whitespace
- optionally collapse repeated blank lines for relaxed tests
- normalize HTML only in tests that explicitly opt in

Do not hide behavior differences with broad normalization.

## Test Modes

Strict:

- exact metadata fields
- exact Markdown after minimal whitespace normalization
- exact HTML only where stable

Relaxed:

- content exists
- important text is present
- known clutter text absent
- metadata fields pass required assertions

Diagnostic:

- run everything
- produce failure report
- not necessarily gating at first

## TDD Checklist

- `[x]` Empty project builds.
- `[x]` Placeholder API test passes.
- `[x]` Fixture loader discovers expected number of HTML files.
- `[x]` Expected loader parses a representative `.md` expected file.
- `[x]` URL frontmatter extraction works.
- `[x]` Diagnostic run reports fixture categories.

## Acceptance Gate

- `[x]` `./gradlew jvmTest -q --console=plain` runs.
- `[x]` Fixture harness can load upstream inputs and expected outputs.
- `[d]` Tests fail because parser is empty, not because resources cannot be loaded.

Decision note: the public API skeleton was implemented before the full fixture harness, so the parser is no longer empty. Fixture parity will be introduced as diagnostic/relaxed parser tests in later phases; current tests prove resource loading and expected-output parsing are not blockers.

## Commit Slices

- Gradle scaffold only.
- Public API skeleton only.
- Fixture import only.
- Fixture loader and URL extraction.
- Expected output loader.
- Test mode and normalization helpers.
