# KMP Migration Notes

## Phase 0 — JVM/jsoup baseline

- Date: 2026-08-23
- Branch point: `60cdc36`
- JVM: toolchain 21
- Parser: `org.jsoup:jsoup:1.22.2`
- Correctness gate: `./gradlew -q --console=plain check` passed.
- Rendering gate: the existing fixture and snapshot suite passed without snapshot changes.
- Timing command: `KLEAD_PRINT_FEEDFLOW_TIMINGS=true ./gradlew -q --console=plain test --tests "com.prof18.klead.fixtures.FeedFlowReaderDumpTimingTest" --rerun`
- `baseline-jvm-jsoup` first run: `TIMING_TOTALS retry=1094ms, attempt1.total=1023ms, attempt1.removalPipeline=537ms, attempt1.removalPipeline.removeContentPatterns=332ms, documentParse=232ms, attempt1.removalPipeline.removeContentPatterns.nestedArticleFooterBlocks=229ms, attempt1.mainContentDetection=173ms, attempt1.htmlStandardizer=84ms`
- Five-run median: `retry=1048ms`, `attempt1.total=983ms`, `attempt1.removalPipeline=524ms`, `attempt1.removalPipeline.removeContentPatterns=329ms`, `documentParse=218ms`, `attempt1.removalPipeline.removeContentPatterns.nestedArticleFooterBlocks=228ms`, `attempt1.mainContentDetection=158ms`, `attempt1.htmlStandardizer=81ms`.
- Five-run `retry` samples: `1094ms`, `1075ms`, `1042ms`, `1048ms`, `981ms`.

The Phase 1 performance gate will compare repeated-run medians and requires the Ksoup
median to remain within +25% of the jsoup median. The first-run totals are retained to
match the migration plan's requested record.

## Plan audit

- The repository is still a single JVM module using Kotlin 2.3.21, jsoup 1.22.2,
  coroutines 1.11.0, serialization 1.11.0, and detekt 2.0.0-alpha.5.
- `BlockScan.kt` now has additional identity-map usage not listed in the prepared plan;
  Phase 4 must port it alongside `MainContentDetector.kt`.
- The JVM fixture suite has 551 resources. During the source-set move,
  `src/test/resources` must move to `src/jvmTest/resources` (or be explicitly wired)
  so the classloader-based fixture harness continues to see the byte-identical corpus.
- URL characterization must distinguish decoded `URI.path` from `URI.rawPath`, preserve
  raw query behavior for trusted embeds, and pin `URI.toASCIIString()` behavior for
  Unicode Obsidian Publish links.
- Detekt KMP task names must be discovered from the resolved build rather than assuming
  the names in the prepared plan.

## Phase 2 — Kotlin Multiplatform build conversion

- Replaced the JVM plugin with `kotlin("multiplatform")` 2.3.21 and registered `jvm`,
  `iosArm64`, `iosSimulatorArm64`, and `iosX64` targets.
- Moved production sources to `src/commonMain/kotlin`, JVM tests to
  `src/jvmTest/kotlin`, and all 551 fixture resources to `src/jvmTest/resources`.
- Resolved detekt task names from the configured build:
  `detektCommonMainSourceSet`, `detektCommonTestSourceSet`, and
  `detektJvmTestSourceSet` are wired into `check`.
- Preserved the existing detekt test exclusions for the new `commonTest` and `jvmTest`
  source-set paths.
- Interim gate `./gradlew -q --console=plain jvmTest --rerun` passed with snapshots
  untouched.

## Compatibility differences

### Phase 1 — Ksoup JVM spike

- Verified current core artifact: `com.fleeksoft.ksoup:ksoup:0.2.6`; no IO or network
  extension artifact is required for string parsing.
- Ksoup 0.2.6 is aligned with jsoup 1.22.1.
- `TextNode.wholeText` is exposed as `TextNode.getWholeText()` in Ksoup's Kotlin API.
- `Comment.data` is exposed as `Comment.getData()` in Ksoup's Kotlin API.
- `Ksoup.parse`, `Ksoup.parseBodyFragment`, `Document.outputSettings().prettyPrint(false)`,
  `Element.`is``, `TextNode(String)`, and attribute iteration compiled without further
  compatibility code.
- `./gradlew -q --console=plain check --rerun` passed with the complete fixture suite.
- The 305 checked-in expected snapshot files were unchanged; their aggregate SHA-256
  remained `07b03dac327957296bed4079a2c30ee7c62f65eb0e83a7f3de09ae94be92425d`.
- No parser, selector, serialization, content, metadata, or rendering differences were
  observed, so no snapshot triage items were needed.
- `spike-jvm-ksoup` five-run median: `retry=1026ms`, `attempt1.total=952ms`,
  `attempt1.removalPipeline=505ms`, `attempt1.removalPipeline.removeContentPatterns=318ms`,
  `documentParse=230ms`, `attempt1.removalPipeline.removeContentPatterns.nestedArticleFooterBlocks=222ms`,
  `attempt1.mainContentDetection=155ms`, `attempt1.htmlStandardizer=79ms`.
- Five-run `retry` samples: `1083ms`, `1026ms`, `1057ms`, `1015ms`, `976ms`.
- Compared with the jsoup median, total retry time improved by about 2.1%, total first
  attempt time improved by about 3.2%, and document parsing regressed by about 5.5%.
  All are comfortably within the plan's +25% gate.

## Decision points

- D1 Ksoup version pinning: pin exactly `0.2.6`; upgrades require the full fixture and
  performance gates.
- D2 jsoup/Ksoup parser parity: the full JVM corpus passed without rendering or snapshot
  changes despite Ksoup's jsoup 1.22.1 alignment.
- D3 Android target: follow the plan and keep the JVM target unless consumer validation
  demonstrates an R8 or variant-metadata problem.
- D4 iOS fallback: pending Phase 6 simulator performance results.
