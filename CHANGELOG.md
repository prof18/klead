# Changelog

## 0.0.1-alpha06 — 2026-09-01

- Fixed Android compatibility when consuming Klead from apps compiled against newer Android SDKs. Klead no longer emits calls to Java `List.removeLast()`, preventing crashes on Android 14 and earlier and clearing Google Play's Kotlin incompatibility warning.
