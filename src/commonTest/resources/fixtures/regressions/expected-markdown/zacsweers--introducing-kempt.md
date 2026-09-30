[kempt](https://github.com/zacsweers/kempt) is a little hobby project I've put together to get away from Spotless/Gradle and simplify setting up commit hooks across my projects.

💡

****TL;DR:****

```kg-callout-text
kempt
```

is a single-entry point CLI that delegates to language-specific formatters with easy installation, automatic binary management, git hook integration, license headers, whitespace handling, and simple configuration.

Spotless (and other formatter Gradle plugins) are frankly too heavy-weight because they run on Gradle. Most repos I work in that need to move away from Gradle-based formatting then eventually collect a familiar little pile of formatting scripts. `kempt` aims to turn that pile into one repo-local pipeline.

Right now it supports:

- Kotlin via `ktfmt`
- Java via `google-java-format`
- Rust via `cargo fmt`
- License header insertion
- Trailing whitespace normalization.

Repos configure it with a `.kempt.toml`, then use the same tool locally, in pre-commit, and in CI.

## Quickstart

```bash
$ brew install ZacSweers/tap/kempt-fmt
$ kempt init           # inits a .kempt.toml based on detected langs
$ kempt install-hook   # optional git commit hook integration
$ kempt format|check   # runs the format or check
```

For CI, `kempt check` is the read-only mode. It exits non-zero when files need formatting and prints the command to fix them. For local commits, the hook formats staged files and re-stages only the files it changed, while refusing\* unsafe partial-staging cases instead of silently pulling unrelated work into a commit.

Full usage/configuration/details are all in the repo README.md!

![](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/thumbnail/kempt-19823c7e-9399-4655-9c86-4568097e5c8d)

---

\*it does support formatters that support partial formats! Namely GJF and (a future version of) ktfmt.
