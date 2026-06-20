# Known Differences

Last updated: 2026-06-20

- jsoup serialization differs from browser DOM serialization and Defuddle TypeScript output.
- Non-empty Kotlin Markdown output is normalized to exactly one final newline. Empty output remains an empty string.
- Complex tables fall back to readable text instead of raw HTML.
- Math with `data-latex` is emitted as Markdown math text. MathML without LaTeX falls back to readable text; MathML/LaTeX conversion and rendered math fidelity are excluded by scope.
- Browser-computed styles and JavaScript-rendered content are not available.
- Fetching is out of scope; callers provide static HTML and a source URL.
- Image captions are emitted as italic Markdown because FeedFlow renders them better that way, even when upstream Defuddle expected output is plain text.
- Supported upstream Markdown fixtures are compared strictly against the pinned repo expected output.
- Fixture exclusions must be explicit and limited to documented scope differences such as math/LaTeX conversion.
