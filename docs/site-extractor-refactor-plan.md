# Extractor Refactor Note

This document previously described a separate `SiteExtractor` API. That split
has been removed.

The current model has one extension type:

- `Extractor`
- `ExtractorContext`
- `ExtractorRegistry`
- `DefaultExtractors`

Domain-scoped selectors, direct content extraction, network-backed extraction,
post-processing, priority, and disable-list behavior all flow through that one
type. Use `DefuddleOptions.extractors` to replace or extend defaults.

See [10 Extractors](10-site-extractors.md) for the maintained contract.
