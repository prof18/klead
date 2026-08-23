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

## Compatibility differences

None recorded yet.

## Decision points

- D1 Ksoup version pinning: pending Phase 1 version verification.
- D2 jsoup/Ksoup parser parity: pending Phase 1 fixture results.
- D3 Android target: follow the plan and keep the JVM target unless consumer validation
  demonstrates an R8 or variant-metadata problem.
- D4 iOS fallback: pending Phase 6 simulator performance results.
