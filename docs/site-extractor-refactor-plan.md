# Extractor Refactor Note

This document previously described a separate `SiteExtractor` API. That split
has been removed.

The current model has one extension type:

- `Extractor`
- `ExtractorContext`
- `ExtractorRegistry`
- `DefaultExtractors`

Domain-scoped selectors, direct content extraction from the provided DOM,
post-processing, and priority all flow through that one type. Defaults are
always included, and `DefuddleOptions.customExtractors` adds project-specific
extractors. Fetching is out of scope.

See [10 Extractors](10-site-extractors.md) for the maintained contract.
