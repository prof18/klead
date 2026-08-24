# 04 Main Content Detection

## Goal

Select the best DOM element containing the main readable content.

This phase should not remove clutter yet except where needed for focused tests.

## Inputs

- jsoup document clone
- optional extractor-provided content selector
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

- `[x]` word count
- `[x]` paragraph count
- `[x]` comma count
- `[x]` image density penalty
- `[x]` content class/id bonus
- `[x]` date signal
- `[x]` author signal
- `[x]` footnote signal
- `[x]` nested table penalty
- `[x]` link density multiplier

## Selection Algorithm

1. If an extractor-provided content selector is available and matches, use it.
2. Try matching extractor `contentSelectors` before generic scoring.
3. Find all entry-point candidates.
4. Score each candidate:
   - selector priority bonus
   - content score
5. Sort by descending score.
6. If only body matched, try table-layout content detection.
7. Prefer higher-priority child candidates when:
   - child is contained by top candidate
   - child has meaningful content
   - there are not multiple sibling candidates indicating a listing page
8. If no entry candidates, fallback to block scoring.
9. If body is selected and schema text identifies a smaller matching element, use schema match.

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

- `[x]` extractor content selector wins.
- `[x]` `article` beats `body`.
- `[x]` child `article` can beat parent `main`.
- `[x]` multiple article cards keep parent listing container.
- `[x]` body fallback works.
- `[x]` table-based layout selects main cell.
- `[x]` peripheral table does not steal content.
- `[x]` schema text can refine body selection.
- `[x]` debug selector is stable enough for diagnostics.

## Acceptance Gate

- `[x]` Main content detection passes focused fixtures for article, main, body fallback, listing, and table layout.

## Commit Slices

- Scoring function.
- Entry-point selection.
- Child candidate refinement.
- Table layout detection.
- Schema text refinement.
- Debug candidate reporting.
