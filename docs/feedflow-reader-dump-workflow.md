# FeedFlow Reader Dump Workflow

Use this when testing the local FeedFlow desktop app against this library and turning a broken reader result into a Defuddle/Klead regression fixture.

## Launch FeedFlow From The Worktree

The FeedFlow worktree used for this integration is:

```bash
cd /Users/mg/Workspace/feedflow/feed-flow-defuddle-kotlin
./gradlew :desktopApp:run -q --console=plain
```

If an older desktop app is already running, stop it first so you know the app is using the included Defuddle/Klead build from this worktree.

```bash
ps -ax | rg 'desktopApp:run|com\.prof18\.feedflow\.desktop\.MainKt'
kill <pid>
```

Do not push FeedFlow or Defuddle/Klead changes from this workflow unless explicitly asked.

## Create A Reader Dump

1. Open the desktop app from the worktree above.
2. Open the article in reader mode.
3. Reproduce the bad output in the reader.
4. FeedFlow writes a debug bundle under:

```text
/Users/mg/Library/Application Support/FeedFlow-dev/reader-debug/
```

The newest dump is usually:

```bash
ls -td "$HOME/Library/Application Support/FeedFlow-dev/reader-debug"/* | head -n 1
```

Each bundle contains:

- `source.html`: pristine HTML returned by FeedFlow's retriever.
- `fixture-candidate.html`: source HTML plus URL frontmatter, ready to copy into this repo.
- `defuddle-content.html`: cleaned HTML returned by Defuddle/Klead.
- `defuddle-content.md`: Markdown returned by Defuddle/Klead.
- `reader-content.md`: final Markdown passed to FeedFlow reader mode.
- `metadata.json`: URL, suggested fixture name, parse timing, selected selector, and removal debug output.
- `README.md`: short bundle summary and a copy command.

When debugging extraction behavior, start with `reader-content.md` to confirm what the app rendered, then inspect `defuddle-content.md` and `defuddle-content.html` to decide whether the issue is in Defuddle/Klead or in FeedFlow's reader layer.

## Promote A Dump To A Regression Fixture

Use the `suggestedFixtureName` from `metadata.json`. The `.html` fixture and `.md` expected snapshot must share that exact base name.

```bash
DUMP_DIR="$HOME/Library/Application Support/FeedFlow-dev/reader-debug/<bundle-directory>"
FIXTURE_NAME="<suggestedFixtureName-from-metadata-json>"

cp "$DUMP_DIR/fixture-candidate.html" \
  "/Users/mg/Workspace/defuddle-kotlin/src/test/resources/feedflow-reader-dumps/$FIXTURE_NAME.html"

cp "$DUMP_DIR/defuddle-content.md" \
  "/Users/mg/Workspace/defuddle-kotlin/src/test/resources/feedflow-reader-expected/$FIXTURE_NAME.md"
```

The expected Markdown is only a starting point. If the dump contains known-bad output, edit the expected `.md` to describe the desired clean result before implementing the fix.

## Test The Fixture

Run the FeedFlow reader dump snapshot suite:

```bash
./gradlew test --tests com.prof18.klead.fixtures.FeedFlowReaderDumpRegressionTest -q --console=plain
```

For a new failing fixture, the normal loop is:

1. Add/copy the fixture.
2. Write or edit the expected Markdown so it encodes the desired behavior.
3. Run the snapshot test and confirm it fails for the right reason.
4. Implement the smallest Defuddle/Klead change.
5. Re-run the snapshot test plus the relevant targeted unit tests.
6. Run the full gate before handoff:

```bash
./gradlew check -q --console=plain
```

If the behavior is already correct and only the snapshot should be refreshed, use the explicit update flag:

```bash
KLEAD_UPDATE_FEEDFLOW_SNAPSHOTS=true ./gradlew test \
  --tests com.prof18.klead.fixtures.FeedFlowReaderDumpRegressionTest \
  -q --console=plain
```

Only refresh snapshots after verifying the new output is actually desired.

## Quick Triage Rules

- Junk appears in `defuddle-content.md`: fix Defuddle/Klead and add or update a fixture.
- Junk appears only in `reader-content.md`: investigate FeedFlow's reader rendering/conversion path.
- Missing or wrong cleaned HTML in `defuddle-content.html`: inspect the removal pipeline and HTML standardization.
- Slow `parseTimeMillis` in `metadata.json`: profile Defuddle/Klead.
- Fast `parseTimeMillis` but slow UI: the delay is likely outside Defuddle/Klead.
