# 12 Upstream Sync Process

## Goal

Keep upstream Defuddle fixtures and expected outputs updateable without losing local Kotlin-specific decisions.

## Sync Inputs

- upstream repository URL
- upstream commit/tag
- local fixture destination
- local expected destination
- current pinned SHA

## Sync Outputs

- copied fixture files
- copied expected files
- updated pinned SHA
- sync report

## Sync Report

Report:

- previous SHA
- new SHA
- added fixtures
- removed fixtures
- changed fixtures
- added expected files
- removed expected files
- changed expected files
- upstream source files changed in ported areas

## Safety Rules

- Never overwrite `kotlin-expected/`.
- Never edit upstream fixtures manually.
- Keep fixture sync in a separate commit when practical.
- After sync, run diagnostic fixture suite before changing code.
- Classify new failures before updating expected outputs.

## Suggested Task

Add a Gradle task or script:

```text
syncDefuddleFixtures --sha <commit>
```

It should fetch upstream, copy only fixture and expected assets, and write a report.

Manual sync is acceptable at first, but document exact commands.

## TDD Checklist

- `[ ]` Existing pinned SHA is read.
- `[ ]` New SHA is written.
- `[ ]` Fixture files copy to the right location.
- `[ ]` Kotlin-specific expected files are untouched.
- `[ ]` Sync report lists changed files.
- `[ ]` Missing upstream directory fails with clear message.

## Acceptance Gate

- `[ ]` A developer can update upstream fixtures with a repeatable process and reviewable diff.

## Commit Slices

- Pinned SHA file.
- Manual sync docs.
- Automated sync task.
- Sync report generation.

