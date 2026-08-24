# 09 Fixture Coverage Expansion

## Goal

Expand from focused unit tests and a small fixture allowlist to broad upstream Defuddle fixture coverage.

## Current Fixture Gate

- exact selected metadata fields
- exact Markdown after minimal normalization
- exact HTML for stable expected HTML cases
- explicit exclusions for unsupported math/rendering conversion

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

## Kotlin-Specific Expected Outputs

Allowed only when:

- behavior is intentionally different
- output is still clean Markdown
- reason is documented in known differences
- upstream expected output remains unchanged

Naming:

```text
src/commonTest/resources/fixtures/regressions/expected-markdown/<fixture-name>.md
```

## TDD Checklist

- `[x]` Fixture category labels are correct.
- `[x]` Supported fixtures run as exact snapshots.
- `[d]` Kotlin expected outputs are loaded only when explicitly allowed.
- `[x]` Coverage report is generated or documented.

Supported upstream Markdown fixtures and supported upstream metadata fields (`title`, `author`, `site`) are covered by strict snapshots, and Kotlin-specific expected outputs are not used in this release.

## Acceptance Gate

- `[d]` All fixtures outside the explicit math rendering/conversion exception pass strict or accepted-difference tests.
- `[x]` Every supported fixture is covered by the exact common snapshot suite.

Release decision: supported upstream Markdown and metadata fixture parity is enforced by strict snapshots. The remaining documented exclusions are math/rendering conversion, network-backed extraction, browser-rendered content, computed styles, and upstream `published` frontmatter that is not part of the public metadata model.

## Commit Slices

- Category labels.
- Category expansion one group at a time.
- Known-difference expected output for one classified behavior.
