# Release Scope

This repository currently provides an alpha Kotlin Multiplatform library for extracting
clean Markdown and sanitized HTML from static HTML on JVM, Android, iOS, and native macOS
on Apple silicon and Intel. The Intel-native `macosX64` target is transitional because
Kotlin deprecated it in 2.3.20.

Included:

- generic main-content extraction
- metadata extraction
- schema.org JSON-LD extraction
- removal pipeline
- HTML standardization
- direct Markdown writer and cleaned-HTML output
- upstream fixture harness and diagnostic coverage
- built-in domain-scoped extractors plus caller-provided custom extractors
- security and robustness smoke coverage

Excluded or staged:

- fetching is out of scope; callers provide HTML and source URL
- JavaScript execution
- browser layout and computed styles
- WebView, GraalJS, browser DOM, Compose/UI rendering
- rendered math fidelity and MathML/LaTeX conversion
- unsupported upstream fixture behavior outside the documented static-HTML and math/rendering scope
- network-backed extraction
