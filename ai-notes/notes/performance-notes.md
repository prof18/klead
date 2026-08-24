# Performance And Robustness

> Historical implementation notes. Current commands, measurements, hardware, and budgets live in [`docs/benchmarking.md`](../../docs/benchmarking.md).

Last updated: 2026-06-14

## Smoke Targets

- Small article parse under 1 second.
- Synthetic long article parse under 5 seconds.
- Repeated parse smoke test should not retain debug/removal state across parses.

These are development smoke thresholds, not product SLAs.

## Current Coverage

- Security sanitizer strips dangerous links, `data:text/html`, event attributes, dangerous form actions, and `srcdoc`.
- Safe `data:image/*` sources are preserved.
- Malformed HTML, bad URLs, missing head, and invalid JSON-LD do not crash.
- Unsupported selectors are recorded by selector diagnostics and do not abort parsing.
- Long and small benchmark smoke fixtures run in tests.

## Threading Note

Parser calls are single-use. jsoup documents are mutable; consuming apps should parse on a background thread and should not share a mutable document between concurrent parses.
