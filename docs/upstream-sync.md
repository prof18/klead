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
cp /private/tmp/defuddle-upstream/tests/fixtures/*.html \
  src/commonTest/resources/fixtures/defuddle/input-html/
cp /private/tmp/defuddle-upstream/tests/expected/*.md \
  src/commonTest/resources/fixtures/defuddle/expected-markdown/
cp /private/tmp/defuddle-upstream/tests/expected/*.html \
  src/commonTest/resources/fixtures/defuddle/expected-html/
```

Record the imported upstream commit in the reviewed sync report.

Do not edit files under `src/commonTest/resources/fixtures/defuddle/` by hand. Project-specific regressions belong under `src/commonTest/resources/fixtures/regressions/`.

After syncing, run:

```sh
./gradlew -q --console=plain jvmTest \
  --tests com.prof18.klead.fixtures.DefuddleFixtureMarkdownSnapshotTest
```

Review exact Markdown and supported metadata failures before changing parser code or expected outputs.

## Programmatic Sync Helper

The test-scoped `com.prof18.klead.fixtures.FixtureSync` helper can sync from an already-cloned upstream checkout into local fixture directories and generate a Markdown report. It is covered by `FixtureSyncTest` and is not part of the shippable library artifact.
