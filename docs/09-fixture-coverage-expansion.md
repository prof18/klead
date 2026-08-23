# 09 Fixture Coverage Expansion

## Goal

Expand from focused unit tests and a small fixture allowlist to broad upstream Defuddle fixture coverage.

## Fixture Modes

Strict:

- exact selected metadata fields
- exact Markdown after minimal normalization
- exact HTML only for stable expected HTML cases

Relaxed:

- content not empty
- key expected text present
- known clutter absent
- metadata sanity checks
- Markdown structurally valid enough for renderer-independent parsing if a parser is used in tests

Diagnostic:

- runs all fixtures
- classifies failures
- produces report
- can be non-gating while the port is under active development

## Fixture Categories

Use filename and manual labels:

- metadata
- general article
- code blocks
- images
- tables
- footnotes
- math
- callouts
- hidden
- content patterns
- scoring
- listing pages
- site extractors
- comments/conversations

## MVP Allowlist

Start with a small list that exercises the core:

```text
metadata--author-by-prefix-and-url.html
metadata--h1-sibling-byline.html
general--stephango.com-buy-wisely.html
general--daringfireball.net-2025-02-the_iphone_16e.html
entry-point--js-article-content.html
hidden--nodes.html
hidden--visibility.html
elements--lazy-image.html
elements--image-dedup.html
codeblocks--hljs-header.html
codeblocks--chroma-line-spans.html
elements--data-table.html
elements--bootstrap-alerts.html
footnotes--numeric-anchor-id.html
content-patterns--trailing-related-posts.html
table-layout--single-column.html
```

## Failure Classification

Every failing fixture gets one reason:

- parser bug
- metadata bug
- selector compatibility bug
- removal false positive
- removal false negative
- standardization missing
- Markdown writer missing
- site extractor not yet ported
- fetching excluded by scope
- math rendering/conversion excluded by scope
- acceptable Kotlin Markdown difference
- upstream fixture import issue

Unknown failures are not acceptable for a release gate.

## Kotlin-Specific Expected Outputs

Allowed only when:

- behavior is intentionally different
- output is still clean Markdown
- reason is documented in known differences
- upstream expected output remains unchanged

Naming:

```text
src/jvmTest/resources/kotlin-expected/<fixture-name>.md
```

## Coverage Report

Generate or maintain a report containing:

- total fixtures
- active strict fixtures
- relaxed fixtures
- not-yet-ported fixtures
- pass/fail counts by category
- known differences
- top failure reasons

## TDD Checklist

- `[x]` Fixture category labels are correct.
- `[d]` MVP allowlist runs in strict mode.
- `[x]` Full fixture set runs in diagnostic mode.
- `[x]` Failing fixtures are classified.
- `[d]` Kotlin expected outputs are loaded only when explicitly allowed.
- `[x]` Coverage report is generated or documented.

Note: the MVP allowlist runs in relaxed release mode for broad diagnostics. Supported upstream Markdown fixtures and supported upstream metadata fields (`title`, `author`, `site`) are covered by strict snapshots, and Kotlin-specific expected outputs are not used in this release.

## Acceptance Gate

- `[d]` All fixtures outside the explicit math rendering/conversion exception pass strict or accepted-difference tests.
- `[x]` Full diagnostic suite has no unknown failures.

Release decision: supported upstream Markdown and metadata fixture parity is enforced by strict snapshots. The remaining documented exclusions are math/rendering conversion, network-backed extraction, browser-rendered content, computed styles, and upstream `published` frontmatter that is not part of the public metadata model.

## Commit Slices

- Category labels.
- MVP allowlist.
- Diagnostic report.
- Category expansion one group at a time.
- Known-difference expected output for one classified behavior.
