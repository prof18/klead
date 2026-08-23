# Upstream Fixture Sync

Last updated: 2026-06-14

Upstream repository: `https://github.com/kepano/defuddle`

## Manual Sync

```sh
git clone --depth 1 https://github.com/kepano/defuddle.git /private/tmp/defuddle-upstream
git -C /private/tmp/defuddle-upstream rev-parse HEAD
```

Then copy only upstream-owned test assets:

```sh
cp /private/tmp/defuddle-upstream/tests/fixtures/*.html src/jvmTest/resources/defuddle-fixtures/
cp /private/tmp/defuddle-upstream/tests/expected/* src/jvmTest/resources/defuddle-expected/
cp /private/tmp/defuddle-upstream/LICENSE src/jvmTest/resources/defuddle-license.txt
```

Update `src/jvmTest/resources/defuddle-upstream.sha` to the cloned commit SHA.

Do not edit files under `src/jvmTest/resources/defuddle-fixtures/` or `src/jvmTest/resources/defuddle-expected/` by hand. Kotlin-specific expected output belongs only in `src/jvmTest/resources/kotlin-expected/`.

After syncing, run:

```sh
./gradlew jvmTest -q --console=plain --tests com.prof18.klead.fixtures.FixtureCoverageTest
```

Classify new failures before changing parser code or Kotlin expected outputs.

## Programmatic Sync Helper

The test-scoped `com.prof18.klead.fixtures.FixtureSync` helper can sync from an already-cloned upstream checkout into local fixture directories and generate a Markdown report. It is covered by `FixtureSyncTest` and is not part of the shippable library artifact.
