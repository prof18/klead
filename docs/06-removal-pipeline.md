# 06 Removal Pipeline

## Goal

Remove non-content clutter without deleting article content.

Each removal step must be independently tested before joining the full pipeline.

## Removal Steps

Recommended order:

1. Remove metadata block near title.
2. Remove small images.
3. Remove hidden elements.
4. Remove eyebrow labels.
5. Remove exact selector matches.
6. Remove partial selector matches.
7. Remove low-scoring non-content blocks.
8. Remove content patterns.
9. Deduplicate images.
10. Remove cover image duplicating metadata image.

## Hidden Elements

Remove:

- `[hidden]`
- `[aria-hidden=true]` except math/paywall exceptions
- inline `display:none`
- inline `visibility:hidden`
- inline `opacity:0`
- class token `hidden`
- class token `invisible`
- variant tokens ending in `:hidden` or `:invisible`

Preserve:

- math wrappers
- elements with responsive show classes
- configured paywall text exceptions

## Exact Selectors

Port upstream constants but run them through `selectSafe`.

Important categories:

- scripts/styles/meta/link
- empty media
- ads
- comments
- navigation/header/footer
- metadata/bylines/tags
- table of contents
- inputs/forms/buttons
- iframes and embeds
- logos/icons
- sharing widgets
- related content
- skip links
- dismiss buttons

## Partial Selectors

Partial matching should inspect class/id/data attributes.

Risks:

- deleting code blocks with class names containing `content`
- deleting legitimate article body with broad `post` patterns
- deleting footnotes or callouts

Add protection checks before removal.

## Low-Scoring Blocks

Port `scoreAndRemove`:

- skip main content ancestors
- skip `pre`
- skip extractor output
- skip likely content
- score non-content indicators
- remove only negative score

Likely-content checks:

- role article/main/contentinfo
- content class/id indicators
- contains `pre`, `table`, `figure`, `picture`
- meaningful paragraphs/list items
- prose punctuation with low link density

Non-content signals:

- nav words
- high link density
- high link text ratio
- list-heavy link sections
- social profile links in small blocks
- byline/date metadata block
- card grids
- non-content class/id patterns

## Content Patterns

Port later than selector and scoring removal. Each pattern needs:

- one positive removal test
- one false-positive preservation test
- one fixture test

Examples:

- read time
- breadcrumb
- social counters
- subscribe CTAs
- related posts
- table of contents
- live-blog metadata
- trailing newsletter blocks

## Debug Removals

When debug is enabled, record:

- step
- selector or pattern where applicable
- reason
- first text preview

## TDD Checklist

- `[ ]` Hidden inline styles are removed.
- `[ ]` Math hidden wrappers are preserved.
- `[ ]` Exact selectors remove obvious nav/footer/ad blocks.
- `[ ]` Exact selectors preserve footnotes and callouts.
- `[ ]` Partial selectors do not remove code blocks.
- `[ ]` Low-scoring removes related/link-heavy sections.
- `[ ]` Low-scoring preserves prose sections.
- `[ ]` Content patterns remove trailing subscribe blocks.
- `[ ]` Content patterns preserve legitimate final article paragraphs.
- `[ ]` Debug records identify removals.

## Acceptance Gate

- `[ ]` Removal fixture subset has no major false positives.
- `[ ]` Debug output is useful enough to inspect failed fixtures.

## Commit Slices

- Hidden removal.
- Exact selector removal.
- Partial selector removal.
- Scoring removal.
- Content pattern group by group.
- Image dedup/cover removal.
- Debug removal records.

