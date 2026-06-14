# 05 Metadata Extraction

## Goal

Extract useful page metadata before cleanup removes scripts and metadata tags.

## Metadata Fields

Return:

- title
- description
- domain
- favicon
- image
- language
- published
- author
- site
- schema.org data
- word count and parse time are filled by the parser, not metadata extractor

## Meta Tag Collection

Collect meta tags into:

```kotlin
data class MetaTagItem(
    val name: String?,
    val property: String?,
    val content: String?
)
```

Collect all relevant `<meta>` tags before selector removals.

## Schema.org Extraction

Support:

- JSON-LD scripts
- arrays
- `@graph`
- Article-like fields
- nested author/publisher/image/date fields

Invalid JSON should not crash parsing. Record debug diagnostics if enabled.

## Title Extraction

Candidate order:

1. `og:title`
2. `twitter:title`
3. schema `headline`
4. `meta[name=title]`
5. `sailthru.title`
6. document `<title>`
7. first `h1`

Clean:

- remove site suffix/prefix
- reject placeholder values
- avoid returning site/domain as title when a better candidate exists

## Author Extraction

Sources:

- `sailthru.author`
- `article:author`
- `author`
- `byl`
- `authorList`
- `citation_author`
- `dc.creator`
- schema `author.name`
- `a[rel~=author]`
- `address[rel~=author]`
- DOM author selectors
- h1-adjacent byline patterns

Clean:

- strip `By `
- strip URLs
- normalize `and` to comma where appropriate
- reject placeholders
- avoid large comment/contributor lists

## Date Extraction

Sources:

- schema date fields
- `article:published_time`
- `datePublished`
- `pubdate`
- `time[datetime]`
- h1-adjacent date blocks
- common date meta tags

Normalize only when reliable. Do not invent timezone precision.

## Image And Favicon

Image sources:

- schema image
- `og:image`
- `twitter:image`
- article image candidates

Favicon sources:

- `link[rel=icon]`
- `link[rel=shortcut icon]`
- default `/favicon.ico` only if policy allows

Resolve relative URLs.

## Language

Sources:

- `html[lang]`
- content-language meta
- `og:locale`
- schema language fields
- options language fallback

## TDD Checklist

- `[ ]` Placeholder values are rejected.
- `[ ]` Site suffix is removed from title.
- `[ ]` Brand-only title falls back to better candidate.
- `[ ]` Multi-author citation tags join correctly.
- `[ ]` rel-author in bio container does not capture full bio.
- `[ ]` h1 sibling byline extracts author.
- `[ ]` h1 sibling date extracts published date.
- `[ ]` canonical URL determines domain.
- `[ ]` relative favicon resolves absolute.
- `[x]` JSON-LD invalid syntax is ignored safely.
- `[x]` `@graph` schema fields are found.

## Acceptance Gate

- `[ ]` Metadata fixture subset passes strict expected fields.
- `[ ]` Metadata extractor has focused unit tests independent of full parser.

## Commit Slices

- Meta tag collector.
- Schema.org extractor.
- Title/domain/site extraction.
- Author extraction.
- Date extraction.
- Description/image/favicon/language extraction.
