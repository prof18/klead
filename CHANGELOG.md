# Changelog

## 0.0.1-alpha12 — 2026-10-22

- Fixed crashes on iOS and other Kotlin/Native targets when pages nest markup, JSON-LD or JSON data very deeply, or contain long hyphenated text or oversized `lang` attributes. These overflowed the stack and terminated the host app. Content nested more than 64 levels deep is now flattened to its text, and debug output reports it as `depthLimitedSubtrees`.
- Site extractors that fail on unexpected markup now fall back to generic extraction instead of throwing from `parseHtml`.
- Fixed parses that could take minutes on pages with many empty wrappers, long runs of line breaks, `<hr>` separators, hidden elements, images, permalink anchors, footnotes or comment threads.
- The sanitizer now removes `<link>` and `<meta http-equiv>` elements from the content, and allows `data:image/` URLs only where an image is loaded.
- Markdown output keeps links and images whose URLs contain spaces, `|`, a bare `%` or other characters browsers tolerate, percent-encoding them instead of dropping the link.
- Chinese and Japanese text is counted as words for content detection, so short CJK articles no longer run every retry pass.

## 0.0.1-alpha11 — 2026-10-13

- Pages whose main landmark holds almost no text, such as video pages, now return no content instead of the whole page body with skip links, header logo markup, menus, and footer. Apps can fall back to the feed text instead. Pages with no main landmark still fall back to the body.

## 0.0.1-alpha09 — 2026-10-01

- Improved extraction performance on large documents with iterative DOM copying, fewer temporary document allocations, and bulk child transfers.
- Improved article selection and clutter removal for ApX, GitHub repositories, X, Medium, Hashnode, Blogger, Tunjid, Finanzen, and other publishers.
- Added newsletter profiles and generic layout cleanup for unrecognized Kill the Newsletter templates.
- Preserved reader theme styling, code blocks, inline word spacing, footnotes, list layout, and SVG illustrations.
- Improved Motor1 gallery images and MacStories interactive-chart fallbacks, and removed empty image wrappers that reserve blank space.

## 0.0.1-alpha07 — 2026-09-07

- Improved article extraction across ANSA, CHIP, Corriere, DDay, Geopop, HDmotori, Il Fatto Quotidiano, Il Sole 24 Ore, iPhoneItalia, iSpazio, Macitynet, Reuters, Sky TG24, SmartWorld, and Tom's Hardware by removing promotional, recommendation, moderation, and footer clutter.
- Improved lazy-loaded image handling and preserved compact inline suggestion links in extracted articles.

## 0.0.1-alpha06 — 2026-09-01

- Fixed Android compatibility when consuming Klead from apps compiled against newer Android SDKs. Klead no longer emits calls to Java `List.removeLast()`, preventing crashes on Android 14 and earlier and clearing Google Play's Kotlin incompatibility warning.
