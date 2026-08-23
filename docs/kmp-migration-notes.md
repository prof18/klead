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

## Phase 3 — Publication and FeedFlow consumer

- Added `maven-publish`; KMP now generates umbrella, JVM, and iOS publications.
- Verified the umbrella POM remains `com.prof18:klead:0.1.0-SNAPSHOT`; the JVM target
  publication is `com.prof18:klead-jvm:0.1.0-SNAPSHOT` and is selected through Gradle
  module metadata.
- In `/Users/mg/Workspace/feedflow/feed-flow-defuddle-kotlin`, moved
  `implementation(libs.defuddle.kotlin)` from `androidMain` and `jvmMain` to
  `commonMain` without altering the existing composite-build substitution.
- Consumer gate passed against this worktree with:
  `./gradlew -q --console=plain :shared:androidJar -Pfeedflow.useLocalDefuddle=false --include-build /Users/mg/.codex/worktrees/72c0/defuddle-kotlin`.
- FeedFlow emitted only its existing missing Dropbox-key warning; the build succeeded.

## Compatibility differences

### Phase 4.1 — JVM URL characterization

- Consolidated the eight `java.net.URI` call sites behind `parseKleadUri` and
  `resolveKleadUri` while retaining `java.net.URI` as the implementation.
- Added characterization coverage for absolute and protocol-relative references,
  every relative-reference shape in the plan, query and fragment references, malformed
  input, base trailing-slash behavior, decoded versus raw paths, host casing, opaque
  URIs, and Java's Unicode/ASCII serialization behavior.
- `./gradlew -q --console=plain jvmTest --rerun` passed with the complete JVM fixture
  suite and unchanged snapshots.

### Phase 4.2 — Multiplatform URL facade

- Ksoup 0.2.6 contains URL resolution helpers only in internal packages, so they are not
  a supported library API. `uri-kmp` 0.0.21 does not provide relative resolution.
  Ktor 3.5.2's `URLBuilder.takeFrom` source does not normalize `..` segments and changes
  the base path for fragment-only references, conflicting with the characterization
  suite. Per the plan's fallback order, implemented the small resolver locally.
- The portable facade preserves Java's decoded `path` versus raw path/query split,
  null host for non-ASCII authority, uppercase UTF-8 percent encoding in the ASCII form,
  query-only and fragment-only resolution quirks, and malformed-input null behavior.
- All characterization tests passed unchanged. The complete JVM fixture suite also
  passed with no snapshot modifications.

### Phase 4.3 — Portable identity collections

- Verified Ksoup 0.2.6 `Node.equals` uses reference equality and `Node.hashCode`
  delegates to the platform object's identity hash. Standard Kotlin maps and sets
  therefore preserve the old `IdentityHashMap` semantics in O(1) expected time.
- Replaced both `MainContentDetector` identity collections and the additional
  `ChromeBlockCaps` identity map found during the plan audit. Added a regression test
  with structurally identical sibling elements to pin the collection semantics.

### Phase 4.4 — Portable selector diagnostics lock

- Added exact `kotlinx-atomicfu` 0.33.0 and replaced JVM `synchronized` with its
  multiplatform `SynchronizedObject` lock.
- Preserved insertion order, the 100-entry cap, snapshot-copy reads, and clear behavior;
  added direct coverage for ordering and the cap.

### Phase 4.5 — Portability sweep

- The Java/JVM API sweep found no remaining imports or runtime calls. Native compilation
  additionally exposed `MutableMap.putIfAbsent`, which is not in the common collection
  API; replaced it with an equivalent contains-and-set operation that keeps the first
  rendered footnote definition.
- `compileKotlinIosArm64` and `compileKotlinIosSimulatorArm64` passed. The full
  `./gradlew -q --console=plain check --rerun` gate passed with no snapshot changes.

### Phase 5 — Non-JVM smoke tests

- Added ten portable, string-only parser tests covering empty input, exact Markdown,
  HTML sanitization, dangerous URLs and attributes, malformed HTML/URL/JSON-LD,
  footnotes, metadata, relative URL resolution, valid JSON-LD, and an embedded MDN
  article adapted from the JVM fixture corpus.
- `./gradlew -q --console=plain iosSimulatorArm64Test` passed all common tests on the
  native simulator target. The same smoke tests passed on JVM.

### Phase 6 — Final performance validation

- `final-jvm-ksoup` five-run median: `retry=1035ms`, `attempt1.total=965ms`,
  `attempt1.removalPipeline=498ms`, `attempt1.removalPipeline.removeContentPatterns=313ms`,
  `documentParse=236ms`, `attempt1.removalPipeline.removeContentPatterns.nestedArticleFooterBlocks=214ms`,
  `attempt1.mainContentDetection=159ms`, `attempt1.htmlStandardizer=84ms`.
- Five-run `retry` samples: `1106ms`, `1035ms`, `1035ms`, `1026ms`, `1042ms`.
- Against `baseline-jvm-jsoup`, final total retry improved about 1.2%, first-attempt
  total improved about 1.8%, and document parsing regressed about 8.3%. The end-to-end
  corpus result did not regress and every submetric remains comfortably inside +25%.
- Added a print-only common micro-benchmark that parses the embedded medium MDN article
  ten times. iOS Simulator arm64: `min=50ms`, `median=51ms`, samples
  `[50, 50, 50, 50, 51, 51, 51, 51, 51, 65]`. JVM reference: `min=7ms`,
  `median=11ms`, samples `[7, 7, 8, 8, 8, 11, 11, 12, 15, 170]`.

### Phase 7 — Native macOS extension

- Added `macosArm64()` with no platform-specific implementation; it consumes the same
  `commonMain` parser and `commonTest` rendering suite as iOS.
- Evaluated `macosX64`: both compilation and all common tests passed under Rosetta, but
  Kotlin 2.3.21 warns that the target is deprecated. It is intentionally excluded in
  favor of the supported arm64 target; Intel desktop consumers keep using the JVM
  artifact.
- All 11 macOS arm64 native tests passed. The embedded medium article benchmark reported
  `min=48ms`, `median=49ms`, samples `[48, 48, 49, 49, 49, 49, 50, 50, 50, 76]`.
- Generated the target publication POM as
  `com.prof18:klead-macosarm64:0.1.0-SNAPSHOT` with the expected native Ksoup,
  coroutines, serialization, and AtomicFU dependencies.
- Maven Central POM validation remains blocked project-wide by missing canonical
  project URL, license, developer, and SCM metadata; no values were invented as part of
  this target addition.

## Final handoff gate

- `./gradlew -q --console=plain check compileKotlinIosArm64 compileKotlinIosSimulatorArm64 iosSimulatorArm64Test compileKotlinMacosArm64 macosArm64Test --rerun`
  passed: 459 JVM tests, 11 iOS Simulator arm64 tests, and 11 native macOS
  arm64 tests, with zero failures or skips.
- All 551 JVM fixture/resource files are byte-identical to branch point `60cdc36` after
  accounting for the source-set path move. The sorted Git-blob aggregate SHA-256 is
  `3730df7038e6ee3fe592744aba1361eca73326030e4bc42e2513149029897a1e`
  at both revisions.
- The final FeedFlow consumer gate passed against this worktree with
  `:shared:androidJar` after the native macOS variant was added; only its existing
  missing Dropbox-key warning was emitted.

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
- D4 iOS fallback: not needed. The 51ms simulator median is far below the plan's
  approximate 2-second fallback threshold; keep native Ksoup and confirm later on a
  physical device inside FeedFlow as planned.
