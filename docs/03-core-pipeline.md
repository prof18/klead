# 03 Core Pipeline

## Goal

Create the high-level parser flow that coordinates preparation, metadata, content selection, removals, standardization, URL resolution, and Markdown generation.

At this phase, most internal steps can be minimal or no-op. The pipeline shape and retry behavior should be in place.

## Pipeline Order

Implement the order explicitly:

1. Parse HTML with jsoup and base URL.
2. Normalize attributes on original document.
3. Resolve noscript lazy images on original document.
4. Run `parseInternal` with default options.
5. Retry with less aggressive options if content is too short.
6. Strip unsafe elements and attributes.
7. Apply schema.org text fallback if it clearly improves content.
8. Generate Markdown if requested.
9. Return result.

Internal parse order:

1. Guard broken document.
2. Merge defaults with options and overrides.
3. Extract schema.org data.
4. Collect meta tags.
5. Extract metadata.
6. Optionally remove all images.
7. Clone document.
8. Find main content.
9. Apply removal steps.
10. Apply standardization steps.
11. Resolve relative URLs.
12. Deduplicate images.
13. Serialize content HTML.
14. Count words.
15. Build result debug diagnostics.

## Options

Expose only parser behavior that callers should control:

- `outputs = setOf(DefuddleOutput.MARKDOWN)` or another explicit non-empty output set
- `debug = false`

Removal and standardization policy are internal. Callers should not choose
which cleanup or normalization stages run.

## Retries

Port Defuddle retry behavior using internal removal policy variants:

1. If default result word count is under 200, retry with partial-selector removal disabled.
2. Use retry only if it more than doubles word count.
3. If result is under 50, retry with hidden-element removal disabled.
4. If still under 50, retry with low-scoring, partial-selector, and content-pattern removal disabled.

Tests should not require full extraction implementation. Use fake internal parser hooks or small fixtures to prove retry decisions.

## Document Preparation

Normalize:

- `srcSet` to `srcset`
- image/source attribute casing
- noscript lazy image promotion where there is a real image fallback

Do not remove scripts before schema.org extraction.

## Unsafe Stripping

After parse internals, strip:

- non-math scripts
- style
- noscript
- frame/frameset
- object/embed/applet
- base
- event handler attributes
- `srcdoc`
- dangerous `href`, `src`, `action`, `formaction`, `xlink:href`

## TDD Checklist

- `[x]` Minimal HTML returns a result object.
- `[x]` Empty HTML returns empty content without crash.
- `[x]` Options default correctly.
- `[x]` Retry without partial selectors triggers under 200 words.
- `[x]` Hidden retry triggers under 50 words.
- `[x]` Index-page retry triggers under 50 words.
- `[x]` `srcSet` normalizes to `srcset`.
- `[x]` noscript image fallback promotes real image.
- `[x]` unsafe elements and attributes are stripped after schema extraction.
- `[x]` parse timing is present when debug is requested.

## Acceptance Gate

- `[x]` A simple article fixture returns non-empty `content.html` when HTML is requested.
- `[x]` `content.markdown` can be empty or primitive at this phase, but the field exists when requested.
- `[x]` Retry behavior has unit coverage.

## Commit Slices

- Options and result models.
- Parse entry points.
- Retry controller.
- Document preparation.
- Unsafe stripping.
- Minimal content serialization and word count.
