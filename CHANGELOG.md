# Changelog

## 0.0.1-alpha07 — 2026-09-07

- Improved article extraction across ANSA, CHIP, Corriere, DDay, Geopop, HDmotori, Il Fatto Quotidiano, Il Sole 24 Ore, iPhoneItalia, iSpazio, Macitynet, Reuters, Sky TG24, SmartWorld, and Tom's Hardware by removing promotional, recommendation, moderation, and footer clutter.
- Improved lazy-loaded image handling and preserved compact inline suggestion links in extracted articles.

## 0.0.1-alpha06 — 2026-09-01

- Fixed Android compatibility when consuming Klead from apps compiled against newer Android SDKs. Klead no longer emits calls to Java `List.removeLast()`, preventing crashes on Android 14 and earlier and clearing Google Play's Kotlin incompatibility warning.
