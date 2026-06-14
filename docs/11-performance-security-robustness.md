# 11 Performance, Security, And Robustness

## Goal

Make the parser safe and predictable for desktop JVM use.

## Performance Targets

Define concrete targets once representative pages are selected.

Initial suggested targets:

- small article under 100 ms on developer machine
- large fixture under 1 second
- no unbounded memory growth on repeated parses
- no obvious quadratic behavior on common large pages

These are starting points, not contractual product SLAs.

## Benchmarks

Add benchmark or performance tests over:

- small article
- long article
- documentation page
- image-heavy page
- footnote-heavy page
- large MathJax page, to verify preservation/fallback behavior rather than rendering fidelity
- listing page

Track:

- parse time
- Markdown time
- total time
- output size
- word count
- optional memory estimate

## Input Limits

Add options or defaults:

- max HTML bytes
- max output bytes if needed
- max parse time/cancellation hook if app can cancel
- max fixture diagnostic time in CI

For desktop apps, cancellation matters more than hard process timeouts.

## Security Sanitization

Strip:

- scripts except math scripts during pre-clean extraction phases
- styles from final output
- event handler attributes
- `srcdoc`
- dangerous protocols:
  - `javascript:`
  - `data:text/html`
- dangerous form actions

Preserve:

- safe image URLs
- safe media embeds only if policy allows
- math source data needed for Markdown fallback

## Robustness

Tests:

- malformed HTML
- empty body
- missing head
- invalid JSON-LD
- huge text node
- deeply nested DOM
- unsupported selector
- bad URL
- relative URL without base

## Threading

Document:

- parser instances are single-use
- jsoup documents are mutable
- parse on a background thread in consuming apps
- do not share a mutable document between parses

## TDD Checklist

- `[x]` `javascript:` href stripped.
- `[x]` `data:text/html` src stripped.
- `[x]` event handler attr stripped.
- `[x]` `srcdoc` stripped.
- `[x]` malformed HTML does not crash.
- `[x]` invalid JSON-LD does not crash.
- `[x]` unsupported selector logs debug and continues.
- `[x]` benchmark fixtures run.
- `[x]` repeated parse smoke test does not leak obvious shared state.

## Acceptance Gate

- `[x]` Security tests pass.
- `[x]` Performance report is recorded.
- `[x]` Robustness tests pass.

## Commit Slices

- Security sanitizer tests and implementation.
- Robustness tests.
- Benchmark harness.
- Performance fixes based on measured hotspots.
