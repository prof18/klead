# Defuddle Kotlin Migration

This folder is the execution plan for building a Kotlin/JVM port of Defuddle that outputs clean Markdown.

The project goal is a broad feature port of Defuddle's extraction behavior, with Markdown generation as the primary output. Compose, WebView, and UI rendering are out of scope for this repository unless a later product decision adds an example app.

## How To Use This Folder

1. Start with the first unchecked item in this README.
2. Open the linked phase file for implementation details.
3. Write the test first.
4. Implement the smallest behavior that passes the test.
5. Run the targeted test and relevant fixture subset.
6. Commit when the change is self-contained, even if the phase is not complete.
7. Update this README and the phase file notes before moving on.

Small commits are preferred. A commit may be smaller than a phase when it completes one coherent behavior, such as "fixture URL parser", "case-insensitive selector wrapper", or "Markdown image rendering".

## Status Legend

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked
- `[d]` Deferred by explicit decision

## Scope

- `[x]` SCOPE-001 Kotlin/JVM library accepts static HTML plus URL.
- `[x]` SCOPE-002 Library emits clean Markdown as the primary content output.
- `[x]` SCOPE-003 Library may also return cleaned HTML for debugging and test comparison.
- `[x]` SCOPE-004 No WebView.
- `[x]` SCOPE-005 No GraalJS.
- `[x]` SCOPE-006 No browser DOM dependency.
- `[x]` SCOPE-007 No flexmark HTML-to-Markdown conversion in the core pipeline.
- `[~]` SCOPE-008 Use upstream Defuddle fixtures as the regression oracle.
- `[x]` SCOPE-009 Use TDD for every ported behavior.
- `[x]` SCOPE-010 Compose rendering is out of scope.
- `[ ]` SCOPE-011 Port the major Defuddle feature families, including metadata, removals, standardization, Markdown, fixture coverage, and site extractors.
- `[x]` SCOPE-012 Math content should be preserved when practical, but MathML/LaTeX conversion and math rendering fidelity are out of scope.

## Phase Index

- `[x]` [00 Principles And Scope](00-principles-and-scope.md)
- `[x]` [01 Project Scaffold And Fixtures](01-project-scaffold-and-fixtures.md)
- `[x]` [02 DOM Compatibility Layer](02-dom-compatibility-layer.md)
- `[x]` [03 Core Pipeline](03-core-pipeline.md)
- `[x]` [04 Main Content Detection](04-main-content-detection.md)
- `[x]` [05 Metadata Extraction](05-metadata-extraction.md)
- `[x]` [06 Removal Pipeline](06-removal-pipeline.md)
- `[x]` [07 HTML Standardization](07-html-standardization.md)
- `[x]` [08 Markdown Writer](08-markdown-writer.md)
- `[ ]` [09 Fixture Coverage Expansion](09-fixture-coverage-expansion.md)
- `[ ]` [10 Site Extractors](10-site-extractors.md)
- `[ ]` [11 Performance, Security, And Robustness](11-performance-security-robustness.md)
- `[ ]` [12 Upstream Sync Process](12-upstream-sync-process.md)
- `[ ]` [13 Release Gate And Docs](13-release-gate-and-docs.md)

## Suggested Commit Slices

These are examples. Commit whenever a unit of behavior is complete and tested.

- `[x]` COMMIT-001 Initial Gradle/Kotlin scaffold and empty API.
- `[x]` COMMIT-002 Upstream fixture import with pinned SHA.
- `[x]` COMMIT-003 Fixture discovery and expected-output loader.
- `[x]` COMMIT-004 jsoup selector safety wrappers.
- `[x]` COMMIT-005 DOM mutation helpers.
- `[ ]` COMMIT-006 Word count and text normalization.
- `[x]` COMMIT-007 Meta tag collection and minimal title/domain extraction.
- `[x]` COMMIT-008 Main content scoring unit tests and implementation.
- `[x]` COMMIT-009 Main content selection over entry-point selectors.
- `[x]` COMMIT-010 Hidden element removal.
- `[x]` COMMIT-011 Exact selector removal.
- `[x]` COMMIT-012 Partial selector removal.
- `[x]` COMMIT-013 Low-scoring clutter removal.
- `[ ]` COMMIT-014 Unsafe element/attribute stripping.
- `[ ]` COMMIT-015 URL resolution.
- `[x]` COMMIT-016 Markdown writer skeleton with text/paragraph/headings.
- `[x]` COMMIT-017 Markdown links and images.
- `[x]` COMMIT-018 Markdown lists and blockquotes.
- `[x]` COMMIT-019 Markdown fenced code blocks.
- `[x]` COMMIT-020 Markdown tables.
- `[x]` COMMIT-021 Markdown callouts.
- `[x]` COMMIT-022 Markdown footnotes.
- `[x]` COMMIT-023 Image standardization.
- `[x]` COMMIT-024 Code block standardization.
- `[x]` COMMIT-025 Metadata completeness.
- `[~]` COMMIT-026 Schema.org fallback.
- `[ ]` COMMIT-027 Fixture allowlist expansion by one category.
- `[ ]` COMMIT-028 One static site extractor.
- `[ ]` COMMIT-029 One async/network extractor with injected HTTP client.
- `[ ]` COMMIT-030 Security tests.
- `[ ]` COMMIT-031 Benchmarks.
- `[ ]` COMMIT-032 Release docs and known differences.

## Global TDD Rules

- Every behavior starts with a failing test.
- Prefer narrow unit tests before fixture tests.
- A fixture failure must be classified before it is ignored.
- Kotlin-specific expected outputs are allowed only for intentional differences.
- Do not edit upstream fixture files by hand.
- Do not update dependency versions and fixture baselines in the same commit.
- Do not add broad rewrites without a fixture or unit test proving the behavior.

## Global Acceptance Criteria

- `[ ]` DONE-001 The library parses representative static HTML pages.
- `[ ]` DONE-002 The library extracts metadata and main content.
- `[ ]` DONE-003 The library outputs clean Markdown without flexmark HTML-to-Markdown.
- `[ ]` DONE-004 Upstream Defuddle fixtures are pinned and runnable.
- `[ ]` DONE-005 Major Defuddle feature families are implemented, excluding math rendering/conversion.
- `[ ]` DONE-006 Fixture failures are green or documented as intentional known differences.
- `[ ]` DONE-007 Security sanitization tests pass.
- `[ ]` DONE-008 Performance is acceptable for a desktop JVM app.
- `[ ]` DONE-009 Docs explain current coverage and known differences.

## Progress Notes

Add notes in this format:

```text
YYYY-MM-DD - STEP-ID - status - note
```

2026-06-14 - SCOPE-001..007 - complete - Added Kotlin/JVM scaffold and jsoup-backed static HTML API with Markdown as the primary output and cleaned HTML as secondary output; no WebView, GraalJS, browser DOM, or flexmark conversion is used.
2026-06-14 - PHASE-00 - complete - Initial public contract tests cover empty HTML, minimal article Markdown, cleaned HTML debug output, and unsupported browser/CSS behavior documentation.
2026-06-14 - COMMIT-002 - complete - Vendored upstream Defuddle fixtures from kepano/defuddle at 9db72600a0cfc568eafb31e85ef68ba16add072e: 190 HTML fixtures, 190 expected Markdown files, 3 expected HTML files, and upstream license.
2026-06-14 - COMMIT-003 - complete - Added fixture harness test utilities for discovering upstream fixtures, extracting fixture URLs, parsing expected Markdown JSON preambles, normalizing Markdown, and reporting fixture categories.
2026-06-14 - PHASE-02 - in progress - Added basic jsoup element helpers and URL helpers; selector safety and mutation helpers remain.
2026-06-14 - COMMIT-004 - complete - Added safe selector wrappers with unsupported-selector diagnostics, case-insensitive attribute matching, `:scope >` direct-child handling, and known `:has` fallbacks.
2026-06-14 - COMMIT-005 - complete - Added DOM mutation helpers, document cloning, and malformed fragment parsing while preserving text-node order and base URI.
2026-06-14 - PHASE-03 - in progress - Added Defuddle-compatible option defaults and verified Markdown can be disabled while cleaned HTML remains available.
2026-06-14 - PHASE-03 - in progress - Added document preparation for noscript image fallback promotion, unsafe element/attribute stripping, and opt-in profile timing debug output.
2026-06-14 - PHASE-03 - complete - Added clone-based internal parse retries for short content: without partial selectors, without hidden-element removal, and index-page retry options.
2026-06-14 - COMMIT-008 - complete - Added content scoring with word, paragraph, comma, link-density, image-density, content-hint, date, author, footnote, and nested-table factors.
2026-06-14 - COMMIT-009 - complete - Added ordered entry-point main content detection, contentSelector override, listing-container safeguard, and debug candidate reporting.
2026-06-14 - PHASE-04 - complete - Added table-layout main-cell detection, peripheral table safeguards, and schema-text body refinement in the main content detector.
2026-06-14 - PHASE-05 - in progress - Added structured meta tag collection and JSON-LD schema.org extraction with arrays, `@graph`, nested fields, and invalid JSON diagnostics.
2026-06-14 - COMMIT-007 - complete - Added page metadata extraction for cleaned titles, canonical domains, authors, dates, description, image, favicon, language, and public parser metadata fields.
2026-06-14 - PHASE-05 - complete - Added strict upstream metadata fixture coverage for h1 sibling byline/date, placeholder fallbacks, and rel-author bio-container handling.
2026-06-14 - COMMIT-010 - complete - Added hidden element removal with math-wrapper preservation and debug removal records.
2026-06-14 - COMMIT-011..013 - complete - Added exact selector removal, partial selector removal with code/footnote/callout protections, low-scoring link-heavy block removal, and a trailing subscribe content pattern.
2026-06-14 - PHASE-06 - complete - Added duplicate image removal, metadata cover-image removal, and relaxed upstream hidden fixture coverage for removal false-positive checks.
2026-06-14 - PHASE-07 - complete - Added HTML standardization for duplicate headings, code blocks, lazy images, figures, callouts, simple footnotes, tables, math preservation, and empty wrappers.
2026-06-14 - PHASE-08 - complete - Replaced the primitive private writer with a direct Kotlin Markdown writer for blocks, inline formatting, links, images, lists, blockquotes, fenced code, tables, callouts, footnotes, math preservation, and post-processing.
