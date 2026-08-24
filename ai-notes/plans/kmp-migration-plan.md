# KMP Migration Plan: klead → Kotlin Multiplatform (Android / Desktop-JVM / iOS / native macOS)

**Status:** executed 2026-08-23, including the native macOS extension.
**Audience:** this document is written to be executed by an engineer or an AI coding agent
with limited context. Follow it phase by phase. Do not skip gates. Do not improvise
replacements for anything marked VERIFY — check the referenced source of truth first.

---

## 0. Ground rules for the executor (READ FIRST)

1. **Never proceed past a phase whose gate fails.** Each phase ends with a "Gate" section:
   commands that must pass. If a gate fails, fix it within the phase or stop and report.
2. **The fixture snapshot suite is the source of truth for behavior.** After any parsing
   change, run the full test suite. Snapshots must be **byte-identical**. If a snapshot
   differs, do NOT regenerate/update it to make tests pass. Instead: triage the diff (see
   §"Snapshot triage protocol" at the end), document it, and decide keep/fix.
3. **Do not change the public API** (`com.prof18.klead.*` non-internal types: `Klead`,
   `KleadOptions`, `KleadOutput`, `KleadResult`, `KleadContent`, `KleadMetadata`,
   `RemovalRecord`, `extractors.Extractor` and friends) except where this plan explicitly
   says so.
4. **Run Gradle with `-q --console=plain`** (project convention).
5. **One commit per numbered step**, message prefix `kmp:`. Do not amend, do not push
   unless asked.
6. **Do not reformat unrelated code.** Detekt (with ktlint wrapper) is part of `check`;
   keep it green but don't run broad autoformatting.
7. If an API named in this plan doesn't exist in the version you resolve (Ksoup evolves),
   STOP for that item, check the library's README/source for the current name, and record
   the substitution you made in the migration notes file (`ai-notes/notes/kmp-migration-notes.md`,
   create it in Phase 1).

---

## 1. Current state (verified facts, 2026-07-07)

- Single-module Gradle project, `kotlin("jvm") 2.3.21`, toolchain 21, group
  `com.prof18`, artifact `klead`, version `0.1.0-SNAPSHOT`.
- Dependencies: `org.jsoup:jsoup:1.22.2`, `kotlinx-coroutines-core:1.11.0`,
  `kotlinx-serialization-json:1.11.0`. Detekt `2.0.0-alpha.5` with ktlint wrapper.
- Coroutines and serialization are already multiplatform libraries → no change needed.
- **jsoup is the only substantial JVM-only dependency.** Import surface used in
  `src/main`:
  - `org.jsoup.Jsoup` (2 files: `internal/KleadParser.kt`, `internal/dom/DomMutations.kt`)
    — only `Jsoup.parse(html, baseUri)` and `Jsoup.parseBodyFragment(html, baseUri)`.
  - `org.jsoup.nodes.Element` (44 files), `Document` (10), `TextNode` (9), `Node` (7),
    `Comment` (1: `KleadParser`), `org.jsoup.select.Elements` (1: `internal/removal/BlockScan.kt`).
  - Notable member APIs used: `outputSettings().prettyPrint(false)` (2 sites in
    `KleadParser`), `` `is`(selector) `` (3 sites), `normalName()` (~122),
    `outerHtml()` (~16), `classNames()` (~12), `ownText()`, `wholeText()`, `absUrl()`,
    `attributes()`, `data()`, `html()`, plus the standard `select/selectFirst/attr/text/
    parent/parents/children/childNodes/remove/before/after/appendChild/replaceWith/clone`.
  - Tests import `org.jsoup.Jsoup` (13 files), `Element` (2), `TextNode` (1).
  - The FeedFlow app itself uses `org.jsoup.nodes.Entities.escape` (in
    `feed-flow/shared/.../FeedItemParser.kt`) — app-side, out of scope here, but the same
    mapping applies.
- **Non-jsoup JVM-only usage in `src/main` (complete list):**
  | Construct | Files | Usage pattern |
  |---|---|---|
  | `java.net.URI` | `internal/KleadParser.kt`, `internal/dom/DomUrls.kt`, `internal/metadata/PageMetadataExtractor.kt`, `internal/media/TrustedEmbeds.kt`, `extractors/Extractors.kt`, `internal/extractors/site/GitHubProfile.kt`, `internal/extractors/site/ObsidianPublishProfile.kt`, `internal/extractors/site/MastodonProfile.kt` | `URI(s).host` (4×), `URI(base).resolve(rel).toString()` (2×), `URI(s).toASCIIString()` (1×), `URI(s).path` (1×), `URI(s)` parse for host/path checks (TrustedEmbeds) |
  | `java.util.IdentityHashMap`, `java.util.Collections.newSetFromMap` | `internal/content/MainContentDetector.kt` | identity-keyed score cache + identity de-dup |
  | `synchronized(lock)` | `internal/dom/DomSelectors.kt` (`SelectorDiagnostics`) | guards a small `LinkedHashSet` |
  - Everything else is pure Kotlin stdlib (`kotlin.time.measureTimedValue`, `Regex`,
    `AbstractMutableList` for `DiscardedRemovals` — all KMP-safe).
- **Test-layer JVM dependence** (bigger than main): fixture harness reads files via
  `java.io`, uses `System.getenv` / `System.getProperty("user.dir")`, JUnit platform,
  `kotlinx-coroutines-test`. Plan: fixtures stay JVM-only; common tests get a small
  portable subset (Phase 5).
- Consumer: FeedFlow worktree `/Users/mg/Workspace/feedflow/feed-flow-defuddle-kotlin`
  substitutes `com.prof18:klead` with `includeBuild("../../defuddle-kotlin")` behind
  Gradle property `feedflow.useLocalDefuddle` (default `true`). It currently declares the
  dependency in `androidMain` and `jvmMain` of `:shared`.

## 2. Target state

- Same single module, converted to `kotlin("multiplatform")`.
- Targets: `jvm()`, `androidLibrary` **not needed** (FeedFlow consumes the JVM artifact
  fine on Android today; keep it that way — see §9 decision D3), `iosArm64()`,
  `iosSimulatorArm64()`, `iosX64()` (x64 optional; include for CI simulators on Intel),
  and the post-plan extension targets `macosArm64()` and `macosX64()`.
- HTML parsing: **Ksoup** (`com.fleeksoft.ksoup`) on ALL targets, replacing jsoup
  everywhere. One source tree, no expect/actual DOM facade.
- URL handling, identity maps, locking: small portable replacements (Phase 4).
- Fixture/regression suites keep running on JVM against the same snapshots; a smoke
  subset runs on iOS simulator.

### Why Ksoup everywhere (and not jsoup-on-JVM + ksoup-on-native)

A dual-backend facade doubles maintenance and makes fixture results non-representative
for iOS. Ksoup is a line-by-line port of jsoup (0.2.6 ≈ jsoup 1.22.1, one patch behind
our 1.22.2) and ports jsoup's own test suite (`nodes/`, `parser/`, `select/`, `safety/`
test trees). Risk is managed empirically by the Phase-1 spike: if our ~fixture corpus is
byte-identical under Ksoup on JVM, the fidelity question is settled for our use-case.

Known trade-offs to accept up front:
- JVM parse speed may regress slightly vs jsoup (Ksoup publishes benchmarks; validate in
  Phase 1 gate — accept up to +25% on the corpus timing test, else stop and report).
- Kotlin/Native (iOS) will be slower than JVM. Phase 6 measures it; there is a fallback
  decision point (§9 D4).
- Single-maintainer dependency: pin the exact version, record it, and vendor only if it
  becomes unmaintained (not now).

---

## 3. Phase 0 — Baseline (do this before touching anything)

1. Ensure working tree is clean; if the current perf fixes are uncommitted, commit them
   first (separate commit, not part of migration).
2. Record baselines into `ai-notes/notes/kmp-migration-notes.md` (create the file):
   ```bash
   ./gradlew check -q --console=plain            # must pass
   KLEAD_PRINT_FEEDFLOW_TIMINGS=true ./gradlew jvmTest -q --console=plain \
     --tests "com.prof18.klead.fixtures.FeedFlowReaderDumpTimingTest" --rerun
   grep -h "TIMING" build/test-results/jvmTest/TEST-com.prof18.klead.fixtures.FeedFlowReaderDumpTimingTest.xml
   ```
   Paste the `TIMING_TOTALS` line into the notes file as `baseline-jvm-jsoup`.
3. Create branch `kmp-migration`.

**Gate P0:** `check` green on the branch point; baseline numbers recorded.

---

## 4. Phase 1 — Spike: Ksoup on JVM only (fidelity test, throwaway-able)

Goal: prove Ksoup parses our corpus identically, while the project is still plain
`kotlin("jvm")`. This is the cheapest possible fidelity experiment.

1. In `build.gradle.kts` replace the jsoup dependency:
   ```kotlin
   // remove: implementation("org.jsoup:jsoup:1.22.2")
   implementation("com.fleeksoft.ksoup:ksoup:0.2.6")   // VERIFY latest 0.2.x on Maven Central
   ```
   VERIFY: Ksoup ships several artifacts (`ksoup`, and IO/network extensions like
   `ksoup-kotlinx`, `ksoup-network-ktor3`). klead parses **strings only** — no network,
   no file IO — so the core `ksoup` artifact should suffice. If the core artifact at the
   resolved version requires an engine artifact to compile, add the kotlinx one
   (`com.fleeksoft.ksoup:ksoup-kotlinx`) and record it in the notes. Consult
   https://github.com/fleeksoft/ksoup README for the current artifact matrix.
2. Mechanical import rewrite across `src/main` and `src/test`. Mapping table:
   | jsoup | Ksoup |
   |---|---|
   | `org.jsoup.Jsoup` | `com.fleeksoft.ksoup.Ksoup` |
   | `Jsoup.parse(html, url)` | `Ksoup.parse(html = html, baseUri = url)` |
   | `Jsoup.parseBodyFragment(html, url)` | `Ksoup.parseBodyFragment(html, url)` — VERIFY exact name in `com.fleeksoft.ksoup.Ksoup` |
   | `org.jsoup.nodes.Element` / `Document` / `Node` / `TextNode` / `Comment` | same names under `com.fleeksoft.ksoup.nodes.*` |
   | `org.jsoup.select.Elements` | `com.fleeksoft.ksoup.select.Elements` |
   | `org.jsoup.nodes.Entities` | `com.fleeksoft.ksoup.nodes.Entities` (app-side note only) |
   Per project convention, do NOT use a repo-wide sed; edit file by file (44 files import
   `Element`; it is mechanical but review each hunk). An acceptable middle ground: change
   imports only, since type and member names are identical in the port; compile errors
   will pinpoint real API gaps.
3. Expected API friction points — check each explicitly after the rewrite compiles:
   - `element.`is`(selector)` (3 call sites; find with `grep -rn '\`is\`(' src/main`).
     VERIFY whether Ksoup kept `` `is` `` or renamed it (Kotlin-friendly port may expose
     `matches`/`isMatch`). Use whatever exists; do not write a custom matcher.
   - `document.outputSettings().prettyPrint(false)` (2 sites in `KleadParser`). VERIFY the
     Ksoup `Document.OutputSettings` API — property syntax may be `prettyPrint = false`.
   - `TextNode(" ")` constructor (6 sites) — VERIFY constructor signature (jsoup's is
     `TextNode(String)`, the port should match).
   - `attributes().asList()` (2 sites) — VERIFY iteration API on Ksoup `Attributes`.
   - `absUrl` behavior depends on the parser's base-URI handling; covered by fixtures.
4. Fix compile errors using the above. Anything unexpected → record in notes.
5. Run the full suite and the timing test:
   ```bash
   ./gradlew check -q --console=plain
   KLEAD_PRINT_FEEDFLOW_TIMINGS=true ./gradlew jvmTest -q --console=plain \
     --tests "com.prof18.klead.fixtures.FeedFlowReaderDumpTimingTest" --rerun
   ```
6. Triage every failure with the Snapshot triage protocol (§10). Classify:
   parser-difference (Ksoup vs jsoup 1.22.2) / selector gap / our-code assumption.
   Record all diffs, even ones you fix.

**Gate P1:**
- `check` green with **zero snapshot modifications**, OR every residual diff explicitly
  approved by the maintainer (stop and ask — do not self-approve).
- Corpus `TIMING_TOTALS` within +25% of `baseline-jvm-jsoup`. Record as
  `spike-jvm-ksoup`.

If Gate P1 fails on fidelity in a way that can't be fixed locally: STOP. Report the diff
list. (Fallback decision §9 D4 belongs to the maintainer, not the executor.)

---

## 5. Phase 2 — Convert the build to Kotlin Multiplatform

Only after Gate P1. No source-code changes in this phase beyond moving directories.

1. `settings.gradle.kts`: unchanged (single module).
2. `build.gradle.kts` — replace the plugins/kotlin blocks:
   ```kotlin
   plugins {
       kotlin("multiplatform") version "2.3.21"
       id("dev.detekt") version "2.0.0-alpha.5"
   }

   kotlin {
       jvmToolchain(21)
       jvm()
       iosArm64()
       iosSimulatorArm64()
       iosX64()

       sourceSets {
           commonMain.dependencies {
               implementation("com.fleeksoft.ksoup:ksoup:0.2.6") // version from Phase 1
               implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
               implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
           }
           commonTest.dependencies {
               implementation(kotlin("test"))
               implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
           }
           jvmTest.dependencies {
               // JUnit engine comes via kotlin("test") on JVM; keep useJUnitPlatform below
           }
       }
   }

   tasks.named<Test>("jvmTest") {
       useJUnitPlatform()
   }
   ```
   Keep the detekt config, `docsCheck`, and `check` dependencies as they are; VERIFY
   detekt 2.0 task names for KMP (per-source-set tasks exist; wire `check` to the main
   ones the same way as today).
3. Move sources (git mv, preserve history):
   ```bash
   git mv src/main/kotlin src/commonMain/kotlin
   git mv src/test/kotlin src/jvmTest/kotlin
   mkdir -p src/commonTest/kotlin
   ```
   Test resources: fixtures live wherever `FixtureHarness` resolves them today
   (`user.dir`-relative paths) — VERIFY by reading
   `src/jvmTest/kotlin/com/prof18/klead/fixtures/FixtureHarness.kt` after the move and
   keep the paths working (they are filesystem-relative, so JVM tests are unaffected).
4. The whole `src/commonMain` will NOT compile for iOS yet (java.net.URI etc.). That's
   Phase 4. To keep this phase mergeable, it is acceptable that only `jvm` compiles:
   run `./gradlew jvmTest detektJvmMain -q --console=plain` as the interim gate, and do
   NOT register the ios targets until Phase 4 if compilation noise gets in the way —
   alternatively register them and tolerate red native compile until Phase 4 lands in
   the same PR. Prefer: do Phases 2–4 in one PR, gating on JVM after Phase 2 and on all
   targets after Phase 4.

**Gate P2 (interim):** `./gradlew jvmTest -q --console=plain` green; snapshots untouched.

---

## 6. Phase 3 — Publishable coordinates & FeedFlow wiring (can run parallel to Phase 4)

1. Add `maven-publish` (KMP publishes per-target artifacts automatically; root module
   `com.prof18:klead` remains the umbrella). Keep group/artifact/version identical so
   FeedFlow's `libs.versions.toml` entry keeps working.
2. FeedFlow side (worktree `feed-flow-defuddle-kotlin`):
   - `shared/build.gradle.kts`: move `implementation(libs.defuddle.kotlin)` from
     `androidMain` and `jvmMain` into `commonMobileMain`/`commonMain` — concretely: add to
     `commonMain` once klead's iOS targets exist, delete the two per-target declarations.
   - The `includeBuild` substitution in `settings.gradle.kts` keeps working with KMP
     (dependency substitution maps target artifacts automatically).
   - iOS consumption: `iosMain` code can then call `com.prof18.klead.Klead.parseHtml`
     directly; the existing `AndroidFeedItemParserWorker`/`DesktopFeedItemParserWorker`
     pattern gains an iOS sibling (out of scope for this plan; note only).

**Gate P3:** `cd feed-flow-defuddle-kotlin && ./gradlew :shared:androidJar -q --console=plain`
green with local substitution (Android unaffected by the KMP conversion).

---

## 7. Phase 4 — Replace the JVM-only constructs (the real porting work)

Work file-by-file; after each item run `./gradlew jvmTest -q --console=plain` and keep
snapshots identical. **Before replacing `java.net.URI`, write characterization tests**
(step 1) so the replacement is pinned to current behavior — this is the highest-risk
substitution because URL resolution corner cases differ between libraries.

1. **Characterization tests first** (new file
   `src/jvmTest/kotlin/com/prof18/klead/internal/dom/UrlResolutionCharacterizationTest.kt`):
   Using the CURRENT `java.net.URI` implementation, assert outputs of
   `resolveUrl(base, value)` (in `internal/dom/DomUrls.kt`) for at least:
   - absolute http/https value (returned as-is, resolved form)
   - protocol-relative `//host/path`
   - root-relative `/path`, relative `path`, `../up`, `./same`
   - query-only `?q=1`, fragment-only `#frag`
   - value with spaces / needing encoding; empty value; garbage (`ht!tp://`)
   - base with path+query; base with trailing slash vs without
   - international domain / non-ASCII path (record current behavior, whatever it is)
   Also characterize `hostOrNull()` (KleadParser), and `URI(x).path`,
   `URI(x).toASCIIString()` call sites with representative inputs.
   Commit these tests while still on java.net.URI. They are the safety net.
2. **URL replacement.** Implement a single internal facade in
   `src/commonMain/.../internal/dom/DomUrls.kt` (plus a small `KleadUri` helper) and make
   the other 7 files use it. Options, in preference order:
   1. **Ksoup's own resolver, if exposed.** jsoup resolves relative URLs internally
      (`StringUtil.resolve`) for `absUrl`; VERIFY whether Ksoup exposes a public
      equivalent (check `com.fleeksoft.ksoup.internal` / `helper`). Using the same
      resolver as `absUrl` guarantees internal consistency. If public → use it.
   2. **`com.eygraber:uri-kmp`** (KMP port of android.net.Uri) — VERIFY it provides
      relative resolution; if not, skip.
   3. **Ktor `io.ktor:ktor-http`** — `URLBuilder(base).takeFrom(relative).build()`
      style resolution. Heavier dependency; acceptable since FeedFlow ships Ktor anyway.
   4. Last resort: hand-write RFC 3986 §5 merge (it's ~60 lines) driven entirely by the
      characterization tests.
   Host extraction (`URI(x).host`) and `path` are simpler: any of the above provide
   them; keep `runCatching`-based null on garbage exactly as today.
   `toASCIIString()` in `ObsidianPublishProfile` — check what it's used for (percent-
   encoding of a URL) and reproduce with the chosen library's encoder; the
   characterization test pins it.
   **Gate for this step:** characterization tests pass unchanged on the new
   implementation; full jvmTest green; snapshots identical.
3. **Identity collections** (`internal/content/MainContentDetector.kt`): replace
   `IdentityHashMap`/`Collections.newSetFromMap` with a portable identity wrapper:
   ```kotlin
   private class IdentityKey(val element: Element) {
       override fun hashCode(): Int = element.hashCode() // VERIFY: if Ksoup Element overrides hashCode structurally, use a counter-based key instead
       override fun equals(other: Any?): Boolean = other is IdentityKey && other.element === element
   }
   ```
   IMPORTANT VERIFY: jsoup's `Element.hashCode` is identity-based, but confirm Ksoup's.
   If Ksoup overrides `equals/hashCode` structurally, `hashCode` above may collide badly
   but stays correct (equals is identity); if it's expensive, key by
   `element.hashCode()` fallback or maintain an `ArrayList` + linear `any { it === e }`
   (candidate lists are small — ≤ a few dozen; linear scan is acceptable and simplest).
   Preserve semantics: `scoreCache` memoizes per unique element; `distinctByIdentity`
   dedups candidates.
4. **`SelectorDiagnostics` lock** (`internal/dom/DomSelectors.kt`): replace
   `synchronized(lock)` with `kotlinx.atomicfu`:
   ```kotlin
   // build: implementation("org.jetbrains.kotlinx:atomicfu:<latest>") or the atomicfu plugin
   private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
   ... kotlinx.atomicfu.locks.synchronized(lock) { ... }
   ```
   Alternative with zero new deps: make the whole diagnostics store JVM-oddity-free by
   switching to an `atomic` reference over an immutable list. Either is fine; keep the
   100-entry cap and the exact public functions (`recordUnsupported`,
   `unsupportedSelectors`, `clear`) — tests use them.
5. Sweep for stragglers:
   ```bash
   grep -rn "^import java\.\|^import javax\.\|System\.\|Thread\.\|String.format" src/commonMain --include="*.kt"
   ```
   Must return nothing. (`AbstractMutableList`, `kotlin.time`, `Regex`, `linkedSetOf`
   are stdlib-portable — leave them.)
6. Register/enable the iOS targets (if deferred in Phase 2) and compile:
   ```bash
   ./gradlew compileKotlinIosSimulatorArm64 -q --console=plain
   ```

**Gate P4:**
- `./gradlew check -q --console=plain` green (JVM tests + detekt + docs).
- `./gradlew compileKotlinIosArm64 compileKotlinIosSimulatorArm64 -q --console=plain` green.
- Snapshots byte-identical; characterization tests unchanged.

---

## 8. Phase 5 — Tests on non-JVM targets

The fixture harness (file IO, env vars) stays `jvmTest`. Add a portable smoke layer:

1. Create `src/commonTest/kotlin/com/prof18/klead/CommonSmokeTest.kt` with ~10 tests that
   embed HTML as string constants (no file IO). Port these existing cases (copy the
   HTML/asserts from their jvmTest homes):
   - security sanitizer test (from `SecurityRobustnessTest`)
   - malformed HTML + bad URL + invalid JSON-LD test
   - one markdown-output test and one HTML-output test (e.g. from `KleadApiTest`)
   - footnote formatting happy path (from `FootnoteFormatTest`)
   - metadata extraction basic (title/site/author from meta tags)
   - a medium-sized real page inlined as a string constant (pick the smallest fixture,
     embed it) asserting a stable content substring, not full snapshot
   Use `runTest` from `kotlinx-coroutines-test` and call
   `KleadParser.parseHtml(..., parserDispatcher = StandardTestDispatcher(testScheduler))`
   — mirror `TestParsing.kt`, or move `TestParsing.kt`'s dispatcher-independent parts to
   `commonTest`.
2. Run:
   ```bash
   ./gradlew iosSimulatorArm64Test -q --console=plain
   ```
   (Requires Xcode + a simulator runtime on the machine; on CI use a mac runner.)
3. Keep the JVM-only tests where they are; do NOT try to make the fixture corpus run on
   iOS in this migration (follow-up: a resource-embedding gradle task could generate
   Kotlin string constants from fixtures — note it, don't do it).

**Gate P5:** `iosSimulatorArm64Test` green; `check` (JVM) still green.

---

## 9. Phase 6 — Performance validation & decision points

1. JVM: re-run the corpus timing test; record `final-jvm-ksoup` next to
   `baseline-jvm-jsoup` and `spike-jvm-ksoup` in the notes.
2. iOS: add a `commonTest` micro-benchmark: parse the embedded medium fixture 10× on
   `iosSimulatorArm64Test`, print min/median/max ms, and record the debug numbers.
   Add dedicated release test binaries and a `nativeReleaseBenchmark` task so runtime
   decisions use optimized iOS Simulator arm64 and macOS arm64 numbers. Simulator
   numbers remain indicative; a device run happens later inside FeedFlow.

**Decision points for the maintainer (not the executor):**
- **D1** — Ksoup version pinning: pin exact (`0.2.6`) and add a renovate rule; upgrade
  only with a full fixture run.
- **D2** — jsoup version note: klead was synced against defuddle/jsoup 1.22.2; Ksoup is
  at 1.22.1 parity. If a 1.22.2-specific parser fix matters, it will show up in the
  Phase-1 fixture diff — otherwise ignore.
- **D3** — Android target: keep consuming the `jvm()` artifact from `androidMain`
  (current setup works; a dedicated `androidTarget()` adds AGP coupling for no gain in a
  headless parsing library). Revisit only if R8/metadata issues appear.
- **D4** — iOS fallback: if simulator/device performance is unacceptable (>~2s median on
  a typical article), fall back to running upstream defuddle JS in a hidden WKWebView on
  iOS only, and keep klead-KMP for Android/desktop. This keeps the migration valuable
  either way.

**Gate P6:** numbers recorded; decisions D1–D4 answered in the notes file.

### Native macOS extension

After the original migration gate, add `macosArm64()` and `macosX64()` and reuse the complete
`commonMain` and `commonTest` source sets without platform-specific implementations.
Compile both targets, run their native tests, generate their Maven publication POMs, and
record the common and corpus benchmark output. `macosX64` is included at the maintainer's
request as transitional Intel-native support despite Kotlin 2.3.20 deprecating the target
ahead of its planned removal. Keep the JVM target as the long-term Intel-macOS path.

**Gate P7:** both macOS targets compile and their native tests are green; target POMs are
generated as `com.prof18:klead-macosarm64` and `com.prof18:klead-macosx64`; JVM/iOS gates
and snapshots remain green. Running `macosX64Test` on Apple silicon requires Rosetta.

---

## 10. Snapshot triage protocol (referenced throughout)

When a fixture/snapshot test fails after a parsing-stack change:
1. Produce the diff (test output shows expected vs actual paths; use
   `git --no-pager diff --color=never` on updated snapshot dirs if the harness writes
   actuals, or the assertion message).
2. Classify:
   - **A: Cosmetic serialization** (attribute order, entity escaping form, whitespace
     inside tags) — likely a Ksoup output difference. Acceptable ONLY if the rendered
     result is semantically identical; list every instance in the notes and get approval.
   - **B: Parsing difference** (element tree differs — content gained/lost). Try to pin
     to a jsoup vs Ksoup behavior with a minimal HTML repro; file upstream if Ksoup is
     wrong; if klead code can compensate cheaply and safely, do it; otherwise report.
   - **C: Our-code assumption** (e.g. relied on jsoup identity hashCode, iteration
     order). Fix our code.
3. Never regenerate snapshots to green a class-B diff without approval.

## 11. Risk register (for whoever reviews the PR)

| Risk | Likelihood | Mitigation |
|---|---|---|
| Ksoup parser diverges from jsoup 1.22.2 on corpus pages | low-med | Phase 1 spike gates on byte-identical snapshots |
| `` `is` `` / OutputSettings / Attributes API naming differs in port | med | Explicit VERIFY items; compile errors surface them |
| URL resolution corner cases change reader-mode links | med | Characterization tests written before swap (P4.1) |
| Ksoup Element hashCode not identity-based → MainContentDetector cache misbehaves | low | Explicit VERIFY in P4.3, linear-scan fallback |
| Kotlin/Native perf too slow for iOS | med | P6 measurement + D4 WKWebView fallback |
| Single-maintainer dep goes stale | low | Pin version; fixture suite makes future upgrades cheap to validate; vendoring possible (MIT) |
| detekt 2.0 alpha KMP task wiring surprises | med | Interim JVM-only gates; detekt config unchanged |
| Test-layer file IO breaks on move | low | Fixtures stay jvmTest; paths are user.dir-relative |

## 12. Estimated effort

- Phase 1 (spike): 0.5–1 day, mostly mechanical + triage.
- Phases 2–4: 1–2 days (URL characterization + replacement is the bulk).
- Phases 5–6: 0.5–1 day plus CI/mac-runner setup.
