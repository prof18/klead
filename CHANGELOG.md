# Changelog

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
