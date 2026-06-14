# 02 DOM Compatibility Layer

## Goal

Build a jsoup compatibility layer that gives the rest of the port stable DOM operations similar to the browser APIs used by Defuddle.

Do this before porting extraction logic.

## Required Helpers

Create helpers under `dom/`.

Element helpers:

- `tagLower()`
- `classNameSafe()`
- `idSafe()`
- `textContentLike()`
- `ownTextContentLike()`
- `outerHtmlStable()`
- `innerHtmlStable()`
- `childrenElements()`
- `descendants()`

Selector helpers:

- `selectSafe(selector)`
- `selectFirstSafe(selector)`
- `matchesSafe(selector)`
- `closestSafe(selector)`
- `childrenMatching(selector)`

Mutation helpers:

- `removeSafely()`
- `unwrapSafely()`
- `replaceWithChildren()`
- `transferChildrenTo(target)`
- `replaceChildrenWith(source)`
- `cloneDocument()`
- `parseFragment(html, baseUri)`

URL helpers:

- `absUrlOrEmpty(attr)`
- `resolveUrl(baseUrl, value)`
- `isDangerousUrl(value)`

## Selector Compatibility

Defuddle uses browser-like selectors that may not map one-to-one to jsoup.

Support or safely fallback:

- `[attr]`
- `[attr=value]`
- `[attr*=value]`
- `[attr^=value]`
- `[attr$=value]`
- case-insensitive attribute flags like `[class*="foo" i]`
- `:scope > child`
- `:not(...)`
- `:has(...)` for known forms
- `:last-of-type`
- comma-separated selector lists

The wrapper must not crash the whole parse for a clutter selector. If a selector is unsupported, debug mode should report it.

## Case-Insensitive Attribute Selectors

If jsoup supports a form, use it directly. If not, rewrite manually:

1. Parse the selector form.
2. Select a wider candidate set.
3. Filter by lowercase attribute value.

TDD cases:

- `[class*="ad" i]`
- `[id^="ad-" i]`
- `[role="navigation" i]`
- `[aria-label*="skip" i]`

## `:scope` Handling

Known Defuddle patterns:

- `:scope > a[href]`
- `:scope > img`
- `:scope > tbody > tr > td`
- `:scope > tr > td`
- `:scope > .sidenotes-column`

If needed, manually evaluate direct child chains.

TDD cases:

- direct child matches
- nested descendant does not match direct child selector
- comma-separated scope selectors

## `:has` Handling

Known Defuddle patterns:

- `audio:not([src]):not(:has(source))`
- `video:not([src]):not(:has(source))`
- `header:not(:has(p + p)):not(:has(img))`
- `span:has(img)`
- `figure, p:has([class*="caption"])`

Implement known forms as manual fallbacks if jsoup cannot handle them.

## DOM Mutation Rules

When removing or moving nodes:

- copy target lists before mutating
- avoid invalidating iterators
- preserve child order
- preserve text nodes where jsoup exposes them
- preserve base URI

## TDD Checklist

- `[ ]` Selector helper returns elements for standard selectors.
- `[ ]` Unsupported selector does not crash.
- `[ ]` Case-insensitive selectors work.
- `[ ]` `:scope >` direct-child selector works.
- `[ ]` Known `:has` fallbacks work.
- `[ ]` `unwrapSafely` preserves child order.
- `[ ]` `replaceWithChildren` preserves text and element nodes.
- `[ ]` `parseFragment` handles malformed HTML.
- `[x]` URL resolution handles relative paths.
- `[x]` Dangerous URL detection strips `javascript:` and `data:text/html`.

## Acceptance Gate

- `[ ]` All extraction code can depend on the compatibility layer instead of raw jsoup selectors for risky selector forms.
- `[ ]` Debug mode can report unsupported selectors.

## Commit Slices

- Basic element and URL helpers.
- Safe selector wrapper.
- Case-insensitive attribute selector support.
- `:scope` support.
- `:has` known fallback support.
- DOM mutation helpers.
