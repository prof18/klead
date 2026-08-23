# Release Scope

This repository currently provides an internal Kotlin Multiplatform library for extracting
clean Markdown from static HTML on JVM, iOS, and native macOS on Apple silicon and Intel.
The Intel-native `macosX64` target is transitional because Kotlin deprecated it in 2.3.20.

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
- unsupported upstream fixture behavior outside the documented static-HTML and math/rendering scope
- network-backed extraction
