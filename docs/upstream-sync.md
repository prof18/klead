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
cp /private/tmp/defuddle-upstream/tests/fixtures/*.html src/test/resources/defuddle-fixtures/
cp /private/tmp/defuddle-upstream/tests/expected/* src/test/resources/defuddle-expected/
cp /private/tmp/defuddle-upstream/LICENSE src/test/resources/defuddle-license.txt
```

Update `src/test/resources/defuddle-upstream.sha` to the cloned commit SHA.

Do not edit files under `src/test/resources/defuddle-fixtures/` or `src/test/resources/defuddle-expected/` by hand. Kotlin-specific expected output belongs only in `src/test/resources/kotlin-expected/`.

After syncing, run:

```sh
./gradlew test -q --console=plain --tests dev.defuddle.fixtures.FixtureCoverageTest
```

Classify new failures before changing parser code or Kotlin expected outputs.

## Programmatic Sync Helper

`dev.defuddle.sync.FixtureSync` can sync from an already-cloned upstream checkout into local fixture directories and generate a Markdown report. It is covered by `FixtureSyncTest`.
