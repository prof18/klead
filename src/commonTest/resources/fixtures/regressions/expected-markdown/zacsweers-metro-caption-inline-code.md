[**Metro**](https://zacsweers.github.io/metro/) is a compile-time dependency injection framework that draws heavy inspiration from [Dagger](https://github.com/google/dagger), [Anvil](https://github.com/square/anvil), and [Kotlin-Inject](https://github.com/evant/kotlin-inject). It seeks to unify their best features in one, cohesive solution while adding a few new ones and implemented as a compiler plugin.

For some time, it's felt like the Kotlin community has wanted for a library at the intersection of these different tools and features. Different tools exist for parts of these, but there’s not yet been a unified solution that ties them all together, leaves behind some of their limitations, and embraces newer features that compiler plugins offer. Metro tries to be that answer. It doesn’t try to reinvent the wheel, it does try to make those wheels work better together. In short, Metro stands on the shoulders of giants.

## Installation

Metro 0.1.1 is available today. Installation is simple!

*Apply the Gradle plugin*

**Doc site:** [https://zacsweers.github.io/metro/](https://zacsweers.github.io/metro/)
**API docs:** [https://zacsweers.github.io/metro/latest/api/](https://zacsweers.github.io/metro/latest/api/)
**Repo:** [https://github.com/zacsweers/metro](https://github.com/zacsweers/metro)

## Features

If you've ever worked with Dagger or kotlin-inject, you'll feel right at home with Metro.

*Graphs are interfaces or abstract classes annotated with @DependencyGraph.*

*Provide dependencies with JSR-330-style constructor injection or providers directly in your graphs.*

*Perform assisted injection with @Assisted and @AssistedFactory.*

*Contribute and aggregate bindings like Anvil.*

*Optional dependencies. If the dependency doesn't exist on the injecting graph, the default parameter value is used.*

*Top-level function injection.*

![A Kotlin interface ExampleGraph annotated with @DependencyGraph contains a property val int: Int and three @Provides functions: provideNumber(), provideInt() (qualified with @Named("qualified")), and provideIntIntoSet() (with @IntoSet). Below the code, a test failure message is shown, indicating that Metro cannot find a binding for kotlin.Int requested at test.ExampleGraph.int. It lists similar bindings, but none match the unqualified Int.](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/2025/04/image.png)

*Detailed-yet-readable error messages and diagnostics.*

And much more!

**Highlights**

- Compile-time dependency graph validation
- Compile-time FIR/IR code gen
- Dagger-esque code gen and runtime
- Kotlin-Inject-esque API
- Anvil-esque aggregation
- Multiplatform
- IDE Integration
- Advanced interop
- Private providers and private member injection
- Optional dependencies
- Top-level function injection
- Detailed-yet-readable error messages and diagnostics

Head over to the [Features](https://zacsweers.github.io/metro/latest/features/) section of the project site to get a full overview and the [Usage](https://zacsweers.github.io/metro/latest/dependency-graphs/) section for full documentation of all the APIs available.

## Build Performance

Being a compiler plugin, Metro runs *significantly faster*. When benchmarking my [CatchUp](https://github.com/zacsweers/catchup) app, build performance improved remarkably.

> - Mutators are changing a low-level library.
> - Project has ~35 modules, was previously using a combination of anvil-ksp and K2 kapt for dagger-compiler.
> - Still uses some KSP for Circuit code gen in a couple modules (including the large monolithic app module at the top).
>
> ABI: ABI breaking change.
> No-ABI: Non-ABI breaking change, allowing compilation avoidance to kick in.
> IC: Incremental compilation

**Average improvements**

- ABI – 47% faster
- ABI w/ no IC – 28% faster
- No-ABI – 56% faster
- No-ABI w/ no IC – 25.5% faster

![](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/2025/03/image--4-.png)
![](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/2025/03/image--5-.png)
![](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/2025/03/image--6-.png)
![](https://storage.ghost.io/c/06/11/06119f42-b8a1-4a35-847b-3c148f338c36/content/images/2025/03/image--7-.png)

## Future Work

Metro's still in active development. This is just the first release, there will be bugs and there are a few major features I want to build out next. Nullable bindings, `@ContributesGraphExtension`, and reporting unused bindings are just a few of these. Check out the issue tracker and discussions on the repo for more details.

## FAQ

> Is your [KSP fork of anvil](https://github.com/zacsweers/anvil) still going to be maintained?

It's in maintenance mode and I'm happy to look at bug reports or cut new releases as needed to keep up with the ecosystem, but I'm unlikely to add new features to it.

> Compiler plugins are an unstable API, is this safe to use?

I maintain a few compiler plugins already and have a good routine of this. The most likely scenario is that Metro follows a pattern of doing companion releases for each Kotlin compiler version (as needed), while separately developing new features on top of them. New features are not likely to be backported to older versions, but I'm happy to reconsider if there's a strong community need.

> Is this affiliated with Slack?

Nope! Metro is solely my project.

> Don't you think that "Circuit", a name about wiring, would've been a better name for this? And [Circuit](https://github.com/slackhq/circuit), ostensibly a navigation library, would've been better off named "Metro"?

Thank you for your question.
