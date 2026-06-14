# Known Differences

Last updated: 2026-06-14

- jsoup serialization differs from browser DOM serialization and Defuddle TypeScript output.
- Non-empty Kotlin Markdown output is normalized to exactly one final newline. Empty output remains an empty string.
- Complex tables fall back to readable text instead of raw HTML.
- Math with `data-latex` is emitted as Markdown math text. MathML without LaTeX falls back to readable text; MathML/LaTeX conversion and rendered math fidelity are excluded by scope.
- Browser-computed styles and JavaScript-rendered content are not available.
- Async/network extractors are supported through injected clients, but only the test/fake network path is covered at this stage.
- Full exact Markdown parity across every upstream fixture is staged behind classified known differences.
- The writer intentionally produces cleaner deterministic Markdown rather than byte-for-byte Turndown parity.
