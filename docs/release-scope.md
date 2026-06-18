# Release Scope

This repository currently provides an internal Kotlin/JVM library for extracting clean Markdown from static HTML.

Included:

- generic main-content extraction
- metadata extraction
- schema.org JSON-LD extraction
- removal pipeline
- HTML standardization
- direct Markdown writer
- upstream fixture harness and diagnostic coverage
- one static site extractor: Wikipedia
- security and robustness smoke coverage

Excluded or staged:

- fetching is out of scope; callers provide HTML and source URL
- JavaScript execution
- browser layout and computed styles
- WebView, GraalJS, browser DOM, Compose/UI rendering
- rendered math fidelity and MathML/LaTeX conversion
- full exact Markdown parity for every upstream fixture
- network-backed extraction
