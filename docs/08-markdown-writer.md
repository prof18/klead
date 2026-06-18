# 08 Markdown Writer

## Goal

Generate clean Markdown directly from standardized jsoup DOM with Kotlin code.

Do not use flexmark HTML-to-Markdown conversion. Prior flexmark output was unclean for this use case, and article extraction needs custom rules.

## Writer Contract

Input:

- standardized article root `Element`
- base URL
- metadata if needed for special cases

Output:

- Markdown string

Properties:

- deterministic
- stable whitespace
- readable in plain text
- compatible with common Markdown renderers
- preserves article content over HTML layout

## Suggested Structure

```text
markdown/
  KleadMarkdownWriter.kt
  MarkdownContext.kt
  MarkdownEscapes.kt
  MarkdownBlocks.kt
  MarkdownInline.kt
  MarkdownImages.kt
  MarkdownTables.kt
  MarkdownFootnotes.kt
  MarkdownCallouts.kt
```

## Context State

Track:

- list depth
- ordered list numbers
- blockquote depth
- whether currently inside table
- whether currently inside code
- whether currently inside link
- collected footnote definitions
- used footnote IDs
- base URL

## Inline Nodes

Support:

- text nodes
- `strong`, `b`
- `em`, `i`
- `code`
- `a[href]`
- `img`
- `br`
- `span`
- `sup`
- `sub`
- `mark`
- `del`
- `ins`
- `small`

Rules:

- flatten unknown inline elements
- avoid nested Markdown links
- strip dangerous links to plain text
- escape only where needed
- preserve CJK and zero-width chars

## Block Nodes

Support:

- `h1` through `h6`
- `p`
- `blockquote`
- `ul`
- `ol`
- `li`
- `pre`
- `hr`
- `figure`
- `figcaption`
- `table`
- Defuddle-style callout DOM
- Defuddle-style footnote DOM
- generic `div`, `section`, `article`, `main`

Rules:

- block containers flatten to children with block spacing
- never produce more than two blank lines
- trim trailing whitespace
- do not alter fenced code contents except final newline normalization

## Escaping

Inline escaping must handle:

- backslash
- backtick
- asterisk
- underscore
- brackets
- leading `#`
- leading list markers where text starts a block
- pipe inside table cells

Do not escape inside code spans or fenced code blocks.

## Links

Emit:

```markdown
[text](absolute-url)
```

Rules:

- resolve relative hrefs
- escape parentheses in destinations
- wrap destinations with spaces in angle brackets
- if text is empty and link contains an image, render the image
- if href is dangerous, render text only
- if already inside a link, render child text only

## Images

Emit:

```markdown
![alt](absolute-url)
```

Select URL:

1. largest `srcset` width descriptor
2. `src`
3. standardized lazy source attrs only if explicitly retained

Rules:

- resolve absolute URL
- skip dangerous URLs
- skip empty sources
- preserve alt text
- for figure with caption, emit image then caption as italic or paragraph based on expected fixtures

## Code

Inline code:

- choose enough backticks to wrap content safely
- preserve spaces inside code where needed

Fenced block:

```markdown
```kotlin
fun main() {}
```
```

Language source:

1. `code[data-lang]`
2. `code[class*=language-]`
3. `pre[class*=language-]`
4. known highlighter metadata

## Lists

Unordered:

```markdown
- item
```

Ordered:

```markdown
1. item
2. item
```

Rules:

- preserve nesting
- continuation paragraphs align with item content
- task checkboxes can emit `- [ ]` / `- [x]` if present

## Blockquotes

Prefix every line with `>`.

Nested blockquotes increment prefix.

## Tables

Emit GFM pipe tables only for simple rectangular tables:

```markdown
| A | B |
| --- | --- |
| 1 | 2 |
```

Simple table criteria:

- no rowspan
- no colspan
- no nested tables
- rectangular row shape after normalization

Complex fallback:

- convert rows to readable paragraphs/lists, or
- keep a known-difference marker in tests if policy allows

Do not emit raw HTML as the default because the target is Markdown rendering without WebView.

## Callouts

From normalized callout DOM, emit:

```markdown
> [!info] Info
> Body text.
```

Rules:

- lower-case callout type
- title optional
- body lines all blockquoted
- nested lists/code inside callouts should remain readable

## Footnotes

Emit:

```markdown
Text[^1]

[^1]: Footnote content.
```

Rules:

- assign stable IDs by reference order
- preserve numeric IDs when clean
- strip backrefs
- avoid duplicate definitions
- render multi-paragraph notes with indented continuation

## Math

Policy:

- `data-latex` inline -> `$latex$`
- `display=block` or display math class -> `$$latex$$`
- MathML without LaTeX -> readable text fallback
- no MathML-to-LaTeX conversion
- no rendered math fidelity requirement
- known differences must be documented

## Post Processing

After rendering:

- normalize line endings
- trim trailing whitespace from every line
- collapse more than two blank lines
- ensure one final newline
- preserve fenced code block internals

## TDD Checklist

- `[x]` Paragraph renders cleanly.
- `[x]` Headings render with correct levels.
- `[x]` Emphasis and strong escaping works.
- `[x]` Inline code handles embedded backticks.
- `[x]` Links resolve absolute URLs.
- `[x]` Dangerous links render as text only.
- `[x]` Images choose largest `srcset`.
- `[x]` Lists preserve nesting.
- `[x]` Blockquotes prefix all lines.
- `[x]` Fenced code preserves content.
- `[x]` Simple tables render as GFM.
- `[x]` Complex tables use documented fallback.
- `[x]` Callouts render as alert blockquotes.
- `[x]` Footnotes render references and definitions.
- `[x]` Math with `data-latex` is emitted as Markdown math text.
- `[x]` MathML without `data-latex` falls back to readable text or documented known difference.
- `[x]` Post-processing does not alter fenced code.

## Acceptance Gate

- `[x]` Active fixture allowlist has clean Markdown output.
- `[x]` No flexmark HTML-to-Markdown converter is used in production code.
- `[x]` Intentional Markdown differences from upstream are documented.

Known difference: non-empty Markdown output is normalized to one final newline. Empty content remains an empty string.

## Commit Slices

- Writer skeleton and whitespace.
- Basic block nodes.
- Inline escaping.
- Links.
- Images.
- Lists.
- Blockquotes.
- Code spans and fenced blocks.
- Tables.
- Callouts.
- Footnotes.
- Math Markdown preservation without conversion/rendering.
- Post-processing.
