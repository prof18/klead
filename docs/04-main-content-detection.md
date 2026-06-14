# 04 Main Content Detection

## Goal

Select the best DOM element containing the main readable content.

This phase should not remove clutter yet except where needed for focused tests.

## Inputs

- jsoup document clone
- optional `contentSelector`
- schema.org data
- entry point selector constants
- content scoring function

## Entry Point Selectors

Port the upstream selector list, including:

- `#post`
- `.post-content`
- `.post-body`
- `.article-content`
- `#article-content`
- `.js-article-content`
- `.entry-content`
- `.markdown-body`
- `article`
- `[role="article"]`
- `main`
- `[role="main"]`
- `.article-body`
- `#content`
- `body`

Keep list order because priority matters.

## Scoring

Port `ContentScorer.scoreElement` with tests for:

- word count
- paragraph count
- comma count
- image density penalty
- content class/id bonus
- date signal
- author signal
- footnote signal
- nested table penalty
- link density multiplier

## Selection Algorithm

1. If `contentSelector` is provided and matches, use it.
2. Find all entry-point candidates.
3. Score each candidate:
   - selector priority bonus
   - content score
4. Sort by descending score.
5. If only body matched, try table-layout content detection.
6. Prefer higher-priority child candidates when:
   - child is contained by top candidate
   - child has meaningful content
   - there are not multiple sibling candidates indicating a listing page
7. If no entry candidates, fallback to block scoring.
8. If body is selected and schema text identifies a smaller matching element, use schema match.

## Table Layout Detection

Support old layouts:

- table width over threshold
- table align center
- class/id indicates content/article
- multi-column row with explicit cell width

Only choose a cell if it contains enough of the body text to plausibly be main content.

## Debug Output

When debug is enabled, include:

- selected content selector
- candidate selectors and scores if practical

## TDD Checklist

- `[ ]` `contentSelector` override wins.
- `[ ]` `article` beats `body`.
- `[ ]` child `article` can beat parent `main`.
- `[ ]` multiple article cards keep parent listing container.
- `[ ]` body fallback works.
- `[ ]` table-based layout selects main cell.
- `[ ]` peripheral table does not steal content.
- `[ ]` schema text can refine body selection.
- `[ ]` debug selector is stable enough for diagnostics.

## Acceptance Gate

- `[ ]` Main content detection passes focused fixtures for article, main, body fallback, listing, and table layout.

## Commit Slices

- Scoring function.
- Entry-point selection.
- Child candidate refinement.
- Table layout detection.
- Schema text refinement.
- Debug candidate reporting.

