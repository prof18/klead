# Fixture Coverage

Last updated: 2026-06-14

Upstream Defuddle SHA: `9db72600a0cfc568eafb31e85ef68ba16add072e`

## Current Gates

- Total upstream HTML fixtures: 190
- Strict metadata fixtures: 3
- Relaxed removal fixtures: 2
- MVP relaxed allowlist: 16
- Full diagnostic fixtures: 190
- Unknown diagnostic failures: 0

## Active Strict Fixtures

- `metadata--h1-sibling-byline`
- `metadata--placeholder-values`
- `metadata--rel-author-in-bio-container`

## Active Relaxed Fixtures

- MVP allowlist from `docs/09-fixture-coverage-expansion.md`
- `hidden--nodes`
- `hidden--visibility`

## Known Differences

- Non-empty Kotlin Markdown output is normalized to one final newline. Empty content remains an empty string.
- Math rendering/conversion fidelity remains out of scope; source data is preserved where practical.
- Full exact Markdown parity is not yet expected for the diagnostic corpus.

## Top Classified Gaps

- Site extractors are not yet ported.
- Network/async extractor hooks are not yet ported.
- Some advanced standardization and Markdown parity cases remain classified by fixture category.
