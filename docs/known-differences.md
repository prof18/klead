# Known Differences

Last updated: 2026-06-15

- jsoup serialization differs from browser DOM serialization and Defuddle TypeScript output.
- Non-empty Kotlin Markdown output is normalized to exactly one final newline. Empty output remains an empty string.
- Complex tables fall back to readable text instead of raw HTML.
- Math with `data-latex` is emitted as Markdown math text. MathML without LaTeX falls back to readable text; MathML/LaTeX conversion and rendered math fidelity are excluded by scope.
- Browser-computed styles and JavaScript-rendered content are not available.
- Fetching is out of scope; callers provide static HTML and a source URL.
- Full exact Markdown parity across every upstream fixture is staged behind classified known differences.
- The writer intentionally produces cleaner deterministic Markdown rather than byte-for-byte Turndown parity.
