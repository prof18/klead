# 07 HTML Standardization

## Goal

Normalize extracted article HTML into a predictable structure so the Markdown writer can stay simple and deterministic.

## Standardization Order

1. Footnotes and callouts that need to survive hidden removal.
2. Headings.
3. Code blocks.
4. Images and figures.
5. Tables.
6. Math.
7. Generic cleanup and whitespace.

Some early standardization may already happen in the core pipeline. Keep order explicit.

## Headings

Port:

- remove first h1/h2 if it duplicates title
- convert h1 to h2 when appropriate
- remove permalink anchors from headings
- preserve heading text from icon/permalink wrappers
- avoid removing fragment links that are real content

Tests:

- title duplicate
- permalink title match
- fragment URL not permalink
- heading with emoji/permalink

## Code Blocks

Normalize to:

```html
<pre><code data-lang="kotlin" class="language-kotlin">...</code></pre>
```

Port support for:

- line span wrappers
- line number gutters
- syntax highlighter wrappers
- copy buttons
- nested `code > pre`
- language classes
- data language attrs

Tests:

- Chroma
- Rouge
- Pygments
- highlight.js
- React syntax highlighter
- Rehype pretty code
- CodeMirror

## Images And Figures

Standardize:

- lazy image attributes
- `srcset`
- picture/source handling
- noscript fallbacks
- base64 placeholder removal
- figure/caption wrappers
- duplicate image variants
- SVG placeholder lazy images

Markdown writer should not need to guess messy lazy-loading patterns if this phase succeeds.

## Callouts

Normalize to:

```html
<div data-callout="info" class="callout">
  <div class="callout-title">
    <div class="callout-title-inner">Info</div>
  </div>
  <div class="callout-content">
    <p>Content</p>
  </div>
</div>
```

Sources:

- GitHub markdown alerts
- Obsidian callouts
- Bootstrap alerts
- admonitions
- callout asides

## Footnotes

Footnotes are high risk. Port in layers:

1. Simple ordered lists.
2. Anchor-id footnotes.
3. Wikipedia references.
4. Google Docs footnotes.
5. Word footnotes.
6. Sidenotes.
7. Inline footnote spans.
8. Hidden footnote sections.

Each layer needs focused unit tests and at least one fixture.

## Tables

Standardize:

- empty tables
- layout tables
- single-column layout tables
- data tables
- complex tables
- equation tables

The Markdown writer should only emit GFM tables for simple rectangular tables.

## Math

Porting policy:

- preserve `data-latex`
- preserve useful MathML where present
- normalize raw LaTeX delimiters where safe
- do not implement MathML-to-LaTeX or LaTeX-to-MathML conversion as a release requirement
- do not attempt rendered math fidelity

## Generic Cleanup

Port:

- remove obsolete elements
- unwrap useless spans/divs
- flatten custom elements
- normalize whitespace
- remove empty nodes
- preserve meaningful `br`
- resolve dangerous inline links

## TDD Checklist

- `[ ]` Heading duplicate title removed.
- `[ ]` Code language retained.
- `[ ]` Code line numbers removed.
- `[ ]` Lazy image promoted.
- `[ ]` Figure caption preserved.
- `[ ]` Callout normalized.
- `[ ]` Simple footnote normalized.
- `[ ]` Wikipedia reference normalized.
- `[ ]` Simple data table preserved.
- `[ ]` Layout table flattened.
- `[ ]` Math `data-latex` and readable fallback data are preserved.
- `[ ]` Empty wrappers removed without losing text.

## Acceptance Gate

- `[ ]` Standardized HTML is stable enough for Markdown generation across the active fixture allowlist.

## Commit Slices

- Headings.
- Code blocks by highlighter family.
- Images/lazy loading.
- Callouts.
- Footnote layer by layer.
- Tables.
- Math preservation without rendering/conversion.
- Generic cleanup.
