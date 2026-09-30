## Flattening my Dependency Graph

Jesse explores a Gradle module structure that he likes.

[Read now ➡](https://publicobject.com/2026/02/03/flattening-my-dependency-graph/)

I liked this not-so-recent post from Jesse Wilson, which outlines a Gradle module convention that prioritizes clear public/private boundaries to support encapsulation and build speed. The approach, adapted from one [presented by Ralf Wondratschek](https://ralf-wondratschek.com/presentation/android-at-scale-at-square.html), also happens to closely resemble one that I helped institute at Dropbox shortly after I joined four and a half years ago.

## Modules built for speed

In my proposal, called “Modules Built for Speed”, I defined a structure in which each feature contains two modules:

- `api`: The feature’s public API interfaces and value objects.
- `impl`: The feature’s implementation classes; service objects that implement the feature’s public API interfaces.

With these two modules and a few rules we were able to significantly reduce build times, reduce confusing coupling between features, and better enforce test coverage.

## The `api` module

The `api` module is usually quite simple since it only contains interfaces and value objects like `data` classes. This module is the public API of the feature, and is the **only** module that other features depend on.

The original implementation of this proposal included a third module, `test-utils`, that was for providing test doubles of the interfaces defined in the `api` module. The Android Gradle Plugin’s support for `testFixtures` in version 8.5.1 has since made this obsolete, and these fixtures now live in the `testFixtures` configuration of the `api` module.

### Test Coverage

Within our codebase we have a rule that enforces 80% test coverage by lines of code per module. While I don’t *prefer* these kinds of strict coverage requirements and we *do* support approved coverage overrides in our Gradle build files, they *have* helped nudge a large group of engineers towards ensuring reasonable test coverage.

For API modules this should be trivial, but can be a little annoying. Initially, it was common for engineers to slap a `coverageOverride = 0.0` into their `api` module’s `build.gradle.kts` file, since these modules are for “interfaces and value objects”. I pushed hard against this, as JaCoCo is smart enough to know that interfaces don’t generally contain coverable lines, but Kotlin also makes it easy to “accidentally” include logic in your interfaces via `default` implementations and extension functions. Additionally, `data` classes *can* contain logic, which should be covered.

In order to ensure we maintain coverage of logic while not being too burdensome, we simply add a [toehold test](https://publicobject.com/2019/02/17/toehold-test/) for data classes that instantiate them and validate equality. This ensures that we aren’t adding blanket coverage overrides that make it easy to add uncovered code.

## The `impl` module

The `impl` module is where the classes that implement the interfaces in the `api` module live, along with any internal support classes. Because they contain the business logic, they are the modules that are heavily tested.

Since we used Anvil, and now use [Metro](https://zacsweers.github.io/metro/latest/), which support aggregation of bindings at build time, we don’t need an `impl-wiring` module like Ralf outlines. Our `app` modules simply depend on the feature `impl` modules, and the wiring happens automatically, allowing the Gradle dependencies to define what contributes implementations.

## The rules

In order for this to truly speed up build times there are a few rules that must be followed:

- Feature modules **only** depend on `api` modules.
- `impl` modules depend on their `api` modules in the `api` configuration.

With these simple rules, `app` modules only list the `impl` modules of features they depend on, `impl` modules can build in parallel because they only depend on `api` modules, and changes in an `impl` module don’t cause recompilation of any other `impl` modules.

Additionally “wiring” code, which defines which implementations satisfy which interfaces, was previously defined in both kotlin code **and** gradle build files (i.e. depending on a different `impl-wiring` module changes the implementation), but it now lives **only** in the `dependencies` block of your app’s gradle build file.

Lastly, while Java’s `package-private` scope was never really private, this approach allows us to get back some of that clear scoping that we used to use an `internal` package declaration for. Since features only depend on other feature’s `api` modules, any classes or helpers defined in the `impl` module will truly be internal to that feature.

## Conclusion

This module structure was primarily aimed at helping us improve our build speeds, and it succeeded at that. Additionally, we found all of these extra benefits, including many we didn’t aniticipate, and this is now a well understood standard at Dropbox. I’d recommend giving it a try.
