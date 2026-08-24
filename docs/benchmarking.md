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

The runner auto-detects the first connected Android device and physical iPhone. iPhone auto-detection requires `jq`; the signed iOS benchmark host also requires XcodeGen and a valid local development signing identity. This checkout uses development team `Q7CUB3RNAK`.

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
