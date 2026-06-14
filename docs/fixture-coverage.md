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
- Strict whole-corpus Markdown parity: deferred as a documented known difference

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

- Full exact Markdown parity across every upstream fixture is staged behind classified known differences.
- Production network-backed site extractor coverage beyond the injected-client hook remains staged.
- Math rendering/conversion fidelity is excluded by scope.
