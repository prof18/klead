# Cross-Platform Benchmarking

Klead measures the same real-world article-extraction pipeline on every locally runnable platform family. The suite is separate from `check`: correctness tests should be deterministic and frequent, while performance measurements need stable devices, optimized binaries, warm-up, and repeated samples.

## Benchmark Model

Every platform reports two cohorts from the same measured passes:

- **Performance core:** the frozen 56-fixture cohort listed in [`performance-core.txt`](../src/commonTest/resources/fixtures/regressions/performance-core.txt). Its membership does not change when a new site regression is captured, so its total, p95, and worst-article metrics remain comparable. Only this cohort enforces performance budgets.
- **Full corpus:** every non-harness input under `fixtures/regressions/input-html`. It grows with real-world coverage and reports current workload health without an absolute total-time gate.

Do not add ordinary regression fixtures to the performance-core manifest. Change the manifest only as an explicit benchmark-suite revision, then rerun and recalibrate every platform baseline in the same change.

Each platform performs one unmeasured warm-up and five measured full-corpus passes by default. Fixture loading and output validation are outside the timed region. Parsing, extraction, cleaned HTML generation, and Markdown generation are inside it, with debug diagnostics enabled consistently on every target.

## Metrics

Each cohort reports:

| Metric | Meaning |
|---|---|
| `median` | Median total time for one pass over the cohort. It is not one article and does not combine all measured passes. |
| `meanArticle` | Median cohort-pass time divided by its fixture count. Useful as a simple typical cost, but sensitive to the mix of article sizes. |
| `p50Article` | Median of the per-fixture medians. This is the best answer to “how long does a typical article take?” |
| `p95Article` | 95th percentile of the per-fixture medians. This exposes broad tail degradation without letting one outlier dominate. |
| `maxArticle` / `slowest` | Median time and identity of the slowest fixture. This exposes a single pathological regression. |
| `throughput` | Total raw input bytes divided by the median cohort-pass time. This adds size context when the full corpus changes. |

The core total catches broad throughput regressions. Its p95 catches degradation across the slow tail, and its worst fixture catches a localized pathological slowdown. The full-corpus versions are observational because newly added websites can legitimately change them.

Timings are measured in microseconds internally and rounded to milliseconds only for the human-readable total and sample list. The console and native test reports retain the 12 slowest fixture medians, including input size and whether each fixture belongs to the core.

## Complete Run

Connect an Android device and paired physical iPhone, then run:

```sh
./scripts/run-regression-benchmarks
```

The command executes:

| Result key | Runtime | Runner |
|---|---|---|
| `jvm` | JVM 21 on the Mac | `jvmRegressionBenchmark` |
| `android` | ART on the connected Android device | Android instrumented test APK |
| `ios-simulator` | Optimized Kotlin/Native | iOS Simulator ARM64 Release test binary |
| `ios-device` | Optimized Kotlin/Native | Signed Release host on the physical iPhone |
| `macos` | Optimized Kotlin/Native | macOS ARM64 Release test binary |

The collected machine-readable schema-v2 result is written to:

```text
build/reports/benchmarks/regression-corpus/latest.json
build/reports/benchmarks/regression-corpus/run-<UTC timestamp>.json
```

The top-level fields in each platform result retain the schema-v1 full-corpus total for compatibility. Distribution and throughput fields describe the growing full corpus; the nested `core` object contains the stable performance gate.

## Core Reference Results (2026-08-24)

The frozen core is the original 56-site corpus used to establish these cross-platform totals:

| Platform | Benchmark target | Reference median | Total failure budget |
|---|---|---:|---:|
| JVM | Azul Zulu OpenJDK 21, Apple M1 Max | 848 ms | 1,100 ms |
| Android | Pixel 4 XL, Android 13 | 12,930 ms | 17,000 ms |
| iOS Simulator | iPhone 17 Pro, iOS 26.5, Release | 3,347 ms | 4,000 ms |
| iOS device | iPhone 16e, iOS 26.5, Release | 2,798 ms | 4,000 ms |
| macOS | Mac Studio, Apple M1 Max, macOS 26.5.2, Release | 3,341 ms | 4,000 ms |

The Apple toolchain was Xcode 26.6. These figures are regression baselines, not a ranking of platforms: runtimes and hardware differ, so compare a target only with later runs of the same target under similar device and thermal conditions.

Tracked reference values and maximum accepted core total, p95-article, and worst-article metrics live in [`regression-corpus-baselines.properties`](../benchmarks/regression-corpus-baselines.properties). The JVM, Android, Simulator, and macOS tail references were calibrated with the schema-v2 five-sample runner on 2026-08-25; the physical-iOS tail references use the latest three-sample device run. Update a reference or budget only after confirming a deliberate engine, core-cohort, toolchain, or permanent benchmark-device change. Do not raise a budget merely to make an unexplained slowdown pass.

## Repeated Runs

Override the number of measured samples when investigating noise:

```sh
KLEAD_BENCHMARK_SAMPLES=7 ./scripts/run-regression-benchmarks
```

Use an odd sample count so the median is unambiguous. Five is the default balance between stability and device runtime; use seven or more before deciding whether to change a baseline.

Device selection is explicit when the defaults are not connected:

```sh
KLEAD_ANDROID_DEVICE_SERIAL='<adb serial>' \
  ./scripts/run-regression-benchmarks '<CoreDevice identifier>'
```

The runner auto-detects the first connected Android device and physical iPhone. iPhone auto-detection requires `jq`; the signed iOS benchmark host also requires XcodeGen and a valid local development signing identity. Provide the Apple development team locally with either:

```sh
export KLEAD_IOS_DEVELOPMENT_TEAM='<10-character team ID>'
```

or add this entry to the ignored `local.properties` file:

```properties
klead.iosDevelopmentTeam=<10-character team ID>
```

The environment variable takes precedence over `local.properties`. The team ID selects a signing team; the signing certificate and private key remain in the local Keychain.

## Fixture Packaging

JVM, macOS, and iOS Simulator load `src/commonTest/resources` externally. Android packages the resources only in the instrumented test APK, and physical iOS packages them only in the signed benchmark host app. The published Android AAR, JVM artifact, and Apple framework do not contain the fixture corpus.

The normal test gate validates that every frozen performance-core entry still exists and that the manifest has no duplicates.

## Individual Runners

For focused investigation:

```sh
./gradlew -q --console=plain jvmRegressionBenchmark
./gradlew -q --console=plain iosSimulatorArm64ReleaseRegressionBenchmarkTest
./gradlew -q --console=plain macosArm64ReleaseRegressionBenchmarkTest
./scripts/run-ios-device-regression-benchmark
```

The Android runner needs instrumentation arguments, so the complete script is the supported entry point for Android measurements. `nativeReleaseBenchmark` remains available for the smaller Apple smoke benchmarks plus the iOS Simulator and macOS corpus runs.
