# Markdown Output Policy

Markdown is generated directly from the standardized portable Ksoup DOM using Kotlin code.

The production pipeline does not use flexmark HTML-to-Markdown conversion.

Output rules:

- deterministic block spacing
- one final newline for non-empty Markdown
- relative links and images resolved against the source URL
- dangerous links rendered as text or skipped
- simple rectangular tables emitted as GFM tables
- complex tables emitted as readable text fallback
- callouts emitted as Markdown alert blockquotes
- footnotes emitted as Markdown footnote definitions
- image captions emitted as italic Markdown
- footnote punctuation and spacing follow Klead's deterministic formatting rules
- math `data-latex` emitted as Markdown math text without conversion/rendering guarantees
