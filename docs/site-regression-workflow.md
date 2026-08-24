# Site Regression Workflow

Use this workflow when a page looks wrong with Klead. The issue report needs only:

- the page URL;
- a screenshot showing the bad reader result;
- one short sentence describing what should be different, when the screenshot is ambiguous.

The screenshot is diagnostic evidence. The fetched input HTML plus expected Markdown and cleaned HTML become the deterministic regression test, so the test does not depend on the live page remaining unchanged.

## Capture The Page

Choose a stable lowercase fixture name and run:

```sh
./scripts/capture-site-regression \
  'https://example.com/article' \
  example--article
```

This creates three matching files under `src/commonTest/resources/fixtures/regressions/`:

- `input-html/<name>.html`: the captured source page and URL frontmatter;
- `expected-markdown/<name>.md`: Markdown produced by the current Klead engine;
- `expected-html/<name>.html`: cleaned HTML produced by the current Klead engine.

The capture command refuses to overwrite existing fixtures. It immediately runs the frozen HTML through Klead, so the first snapshots characterize the current behavior and `SiteRegressionTest` passes before any code change. Review those snapshots alongside the supplied screenshot to confirm that the reported issue is represented. For pages that require JavaScript, authentication, or consent interaction, save the browser's rendered HTML to the matching `input-html` path and then run `./scripts/update-site-regression <name>`.

## Describe The Fix

Do not hand-author the initial output. It records what the engine currently does. A Markdown file may keep the optional JSON preamble used by imported fixtures; the snapshot comparison starts after that preamble:

````md
```json
{"title":"Captured page title"}
```

Expected article Markdown.
````

The cleaned HTML snapshot is stored directly:

```html
<article>
  <p>Expected article content.</p>
</article>
```

The regression gate compares the cleaned Markdown and HTML exactly after minimal line-ending and trailing-whitespace normalization.

## Reproduce, Fix, And Verify

Run the regression on JVM first:

```sh
./gradlew -q --console=plain jvmTest
```

First confirm the captured snapshots match the current broken rendering described by the screenshot. Implement the engine fix against the frozen HTML. The existing snapshot test should then fail because the output changed.

Regenerate only the Markdown and cleaned-HTML snapshots from the same frozen input:

```sh
./scripts/update-site-regression example--article
```

Review the snapshot diff and confirm it contains the intended correction rather than unrelated churn. Then run the shared suite on Apple targets as well:

```sh
./gradlew -q --console=plain iosSimulatorArm64Test macosArm64Test
```

Before handoff, run the complete project gate:

```sh
./gradlew -q --console=plain check
```

`SiteRegressionTest` executes the exact same input and Markdown/HTML snapshots on JVM, iOS, and native macOS. As in RSS-Parser, a small platform test loader reads the external `commonTest` resource directory supplied by Gradle, so the 18 MB corpus is not compiled into every native test binary.
