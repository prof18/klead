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

Recognized newsletter templates are reduced to editorial content before Markdown
conversion. Email mastheads, subscription controls and explicitly sponsored
modules are omitted; editorial sections, links and images remain in reading order.
Presentation tables are flattened without repeating nested cell text. Tables that
carry data retain their rows and cells and follow the normal table output rules.

Unrecognized newsletters get a narrower, layout-only cleanup. It runs only for
`kill-the-newsletter.com` sources (exact host of the source URL, not canonical
metadata) when no extractor produced a result and the selected content contains a
`table[role=presentation]`. It runs after the removal pipeline and extractor
post-processing, and before final standardization, so hidden elements are already
gone. Presentation tables become `div`s (rows, groups, captions and cells keep
their attributes and IDs); code, SVG/MathML, tables without the presentation role
and `col`/`colgroup` tables are left untouched. Unknown template chrome such as
mastheads, sponsors and footers can remain, and layout tables without the
presentation role are not guessed.
