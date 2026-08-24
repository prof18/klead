# Cross-Platform Benchmarking

Klead benchmarks the same frozen real-world regression corpus on every locally runnable platform family. The suite is separate from `check`: correctness tests should be deterministic and frequent, while performance measurements need stable devices, optimized binaries, warm-up, and repeated samples.

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

Each platform parses all 56 captured site inputs, produces cleaned HTML and Markdown, performs one unmeasured warm-up, then records three corpus passes. The console and platform-native test reports retain the 12 slowest fixture medians.

The collected machine-readable result is written to:

```text
build/reports/benchmarks/regression-corpus/latest.json
build/reports/benchmarks/regression-corpus/run-<UTC timestamp>.json
```

Every timestamped report is retained under `build/` for before-and-after comparison during the current worktree lifecycle.

## Reference Results (2026-08-24)

These are the first tracked cross-platform results from the complete runner. Each sample is the sum of the 56 individual parser-pipeline durations for one corpus pass. Reading fixture files and checking the outputs happen outside the timed region; extraction plus generation of both cleaned HTML and Markdown happen inside it. Debug diagnostics are enabled consistently on every target.

| Platform | Benchmark target | Three measured samples | Median | Failure budget |
|---|---|---:|---:|---:|
| JVM | Azul Zulu OpenJDK 21, Apple M1 Max | 806, 848, 950 ms | **848 ms** | 1,100 ms |
| Android | Pixel 4 XL, Android 13, instrumented test APK | 12,832, 12,930, 13,031 ms | **12,930 ms** | 17,000 ms |
| iOS Simulator | iPhone 17 Pro, iOS 26.5, Release | 3,331, 3,347, 3,391 ms | **3,347 ms** | 4,000 ms |
| iOS device | iPhone 16e, iOS 26.5, Release | 2,706, 2,798, 2,892 ms | **2,798 ms** | 4,000 ms |
| macOS | Mac Studio, Apple M1 Max, macOS 26.5.2, Release | 3,332, 3,341, 3,349 ms | **3,341 ms** | 4,000 ms |

The Apple toolchain was Xcode 26.6. Every target performed one unmeasured warm-up before these samples. These figures are regression baselines, not a ranking of platforms: runtimes and hardware differ, so compare a target only with later runs of the same target under similar device and thermal conditions.

The table records the human-readable baseline. The machine-readable source remains [`benchmarks/regression-corpus-baselines.properties`](../benchmarks/regression-corpus-baselines.properties), while every new local run writes its exact samples and target identifiers to `build/reports/benchmarks/regression-corpus/latest.json`.

## Repeated Runs

Override the number of measured samples when investigating a smaller change:

```sh
KLEAD_BENCHMARK_SAMPLES=5 ./scripts/run-regression-benchmarks
```

Use an odd sample count so the reported median is unambiguous. Run the suite before an engine or dependency update, preserve that timestamped JSON report, apply the update, then run it again and compare the two reports.

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

## Budgets And Baselines

Tracked reference medians and maximum accepted medians live in [`benchmarks/regression-corpus-baselines.properties`](../benchmarks/regression-corpus-baselines.properties). Every runner fails when its median exceeds the corresponding budget. Update a reference or budget only after confirming a deliberate engine change or a permanent benchmark-device change; do not raise a budget merely to make an unexplained slowdown pass.

The baselines are device-specific. Android measurements currently refer to the connected Pixel 4 XL, and physical iOS measurements refer to the iPhone 16e. Simulator, JVM, and macOS results refer to this development Mac.

## Fixture Packaging

JVM, macOS, and iOS Simulator load `src/commonTest/resources` externally. Android packages the resources only in the instrumented test APK, and physical iOS packages them only in the signed benchmark host app. The published Android AAR, JVM artifact, and Apple framework do not contain the fixture corpus.

## Individual Runners

For focused investigation:

```sh
./gradlew -q --console=plain jvmRegressionBenchmark
./gradlew -q --console=plain iosSimulatorArm64ReleaseRegressionBenchmarkTest
./gradlew -q --console=plain macosArm64ReleaseRegressionBenchmarkTest
./scripts/run-ios-device-regression-benchmark
```

The Android runner needs instrumentation arguments, so the complete script is the supported entry point for Android measurements. `nativeReleaseBenchmark` remains available for the smaller Apple smoke benchmarks plus the iOS Simulator and macOS corpus runs.
