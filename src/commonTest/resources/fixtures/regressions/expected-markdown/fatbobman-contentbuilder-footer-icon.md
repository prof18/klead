In the [WWDC 2026 session on what’s new in SwiftUI](https://developer.apple.com/videos/play/wwdc2026/269/?utm_source=Fatbobman%20Blog&utm_medium=web), Apple engineers introduced a new SwiftUI feature: `ContentBuilder`. Judging by how you use it, it looks like nothing more than a `ViewBuilder` with a wider reach — APIs that previously accepted `ViewBuilder`, `ToolbarContentBuilder`, or `CommandsBuilder` separately can now share a single builder. Apple also claims the change delivers a significant improvement in type-checking performance. This article digs into what `ContentBuilder` actually is, and where that performance comes from.

> The observations of public interfaces and implementation details in this article are based on Xcode 27 beta 4. Once the final release ships, the compatibility paths in the `.swiftinterface` will need to be re-checked.

## Everything Changed, and Nothing Did

Apple says `ContentBuilder` is transparent to developers. When I opened up Xcode 27’s public interface, the first thing I found was this:

```swift
public typealias ContentBuilder = ViewBuilder
```

`ContentBuilder` isn’t even a new result builder — it’s just a type alias for `ViewBuilder`. That left me thoroughly puzzled. Is *this* what “transparent” means? And where on earth do the performance gains come from?

A result builder isn’t a runtime container. It’s a set of compile-time syntax transformation rules. This code:

```swift
@ViewBuilder
func content() -> some View {
    Text("Hello")
    Image(systemName: "star")
}
```

can be loosely understood as:

```swift
func content() -> some View {
    let v0 = Text("Hello")
    let v1 = Image(systemName: "star")
    return ViewBuilder.buildBlock(v0, v1)
}
```

The remaining control-flow statements are handled by a set of conventional methods:

`if`, `if let`, `#available`, and loops are transformed by `buildIf` (or `buildOptional`), `buildEither`, `buildLimitedAvailability`, and `buildArray` respectively. I’ve discussed these transformation rules in detail in [ViewBuilder Research (Part 1)](https://fatbobman.com/en/posts/viewbuilder1/) and [ViewBuilder Research (Part 2)](https://fatbobman.com/en/posts/viewbuilder2/).

As SwiftUI applications grew more complex, Apple added content domains year by year — Scene, Toolbar, Commands, Table — each with its own dedicated result builder. At the same time, shared components such as `Group`, `ForEach`, and `Section` picked up initializers targeting each of those builders: identical in shape, differing only in their protocol constraints.

These shared components have one trait in common: **they can be used across multiple DSLs, and they nest repeatedly**. The problem isn’t their runtime efficiency. It’s that these groups of look-alike initializers put a multiple-choice question in front of the compiler at every single level of nesting.

Take `Group` in Xcode 26:

```swift
extension Group: View where Content: View {
    init(@ViewBuilder content: () -> Content)
}

extension Group: ToolbarContent
where Content: ToolbarContent {
    init(@ToolbarContentBuilder content: () -> Content)
}

extension Group: Commands where Content: Commands {
    init(@CommandsBuilder content: () -> Content)
}
```

And there are far more than three candidates in practice. Depending on platform and availability, `Group` in Xcode 26.6 also exposes initializers using `SceneBuilder`, `AccessibilityRotorContentBuilder`, `TableRowBuilder`, `TableColumnBuilder`, and `TabContentBuilder`. `Section` has both a `ViewBuilder` and a `TableRowBuilder` path; `ForEach` offers `ViewBuilder`, `TableRowBuilder`, and `TabContentBuilder` entry points, among others.

This style of declaration causes the compiler no end of trouble:

```swift
Group {
    Group {
        Text("Hello")
    }
}
```

For a human developer, this code presents no difficulty at all — one glance tells you what the closure resolves to. The compiler has a lot more to weigh.

When it first encounters the outer `Group`, the compiler has no idea what the closure will ultimately produce. All three initializers listed above are still viable: the result might be a `View`, or it might be `ToolbarContent` or `Commands`. To pick the right one, the part of the compiler responsible for resolving types — the constraint solver — has to keep pushing into the closure. But inside that closure sits another `Group`, and the same three candidates line up all over again. Only once it reaches `Text` at the very bottom does the compiler have enough to decide.

```text
Outer Group
├─ ViewBuilder
│  └─ Inner Group: View / Toolbar / Commands
├─ ToolbarContentBuilder
│  └─ Inner Group: View / Toolbar / Commands
└─ CommandsBuilder
   └─ Inner Group: View / Toolbar / Commands
```

> The diagram above is only illustrative. The real search space also layers in `buildBlock`’s own overloads, making it more complex than shown.

Someone [raised this issue](https://forums.swift.org/t/pitch-result-builder-scoped-unqualified-lookup/62190?utm_source=Fatbobman%20Blog&utm_medium=web) on the Swift forums years ago. What it means is that the compiler has to redo the entry-point selection at every level of nesting — and every additional level multiplies the candidate count again. Nesting like this is everywhere in a real SwiftUI application, and usually far more elaborate than the example.

When candidates can’t be eliminated early and failures happen deep inside the nesting, compilation efficiency takes a visible hit. This is the type-checking risk that has been lurking in SwiftUI’s shared-component APIs for years.

## Compiler Optimizations Alone Aren’t Enough

Some developers may object: Apple has announced compiler optimizations for result-builder compilation performance at WWDC year after year. Was all of that fiction?

Of course not.

The Swift compiler has done substantial work on this scenario in recent years: making individual statements inside a builder infer more independently and in one direction, rather than turning the whole closure into one enormous bidirectional constraint system; and pruning the solver’s search paths through multi-candidate overloads more aggressively. Every one of these improvements genuinely lowered the cost.

But they can’t cure the disease, because the multiple domain-specific initializers exposed by the old shared components are the *source* of “every level of nesting requires a fresh entry-point selection.” However clever the compiler gets, it’s still just answering a multiple-choice question that shouldn’t have been asked in the first place.

## What ContentBuilder Actually Does

Beyond the `typealias` pointing at `ViewBuilder`, the real driver of the performance change is a change in how the APIs are declared — the gains come from SwiftUI’s rework of the shared components’ initializers and of the builder’s output types.

In Xcode 27, the new core methods take roughly this shape:

```swift
public static func buildBlock<Content>(
    _ content: Content
) -> Content

@available(iOS 27.0, macOS 27.0, *)
public static func buildBlock<each Content>(
    _ content: repeat each Content
) -> TupleContent<repeat each Content>

public static func buildIf<Content>(
    _ content: Content?
) -> Content?

public static func buildEither<TrueContent, FalseContent>(
    first: TrueContent
) -> _ConditionalContent<TrueContent, FalseContent>
```

Note this carefully: **none of these signatures carries a `Content: View`, `Content: ToolbarContent`, or `Content: Commands` constraint**. The builder’s job has become purely structural — hold a single piece of content, combine several pieces, record an optional branch or an either-or branch. It’s no longer responsible for answering “which SwiftUI domain does this content belong to?”

The newly introduced `TupleContent` is the linchpin of the design. Its defining characteristic is that **it takes on different identities based on what its elements can do**:

```swift
extension TupleContent: View
where repeat each Content: View {}

extension TupleContent: ToolbarContent
where repeat each Content: ToolbarContent {}

extension TupleContent: Commands
where repeat each Content: Commands {}
```

In other words, `TupleContent` starts out belonging to no domain at all. It’s a `View` only when every element it holds is a `View`; when all its elements are `ToolbarContent`, it *is* toolbar content. `Optional`, `_ConditionalContent`, `Group`, and `ForEach` have all adopted the same approach.

The builder can therefore arrive at a single, concrete structure first:

```text
TupleContent<Text, Image>
```

Only when the function signature demands `some View` does the compiler circle back to verify that both `Text` and `Image` conform to `View`. If that same structure is required to conform to `ToolbarContent` in a toolbar context, the compiler follows a different set of conformance checks.

**The old design interleaved “building the structure” and “proving its identity” at every level of the call tree; the new design builds first and proves afterward.**

Loosening the builder alone still isn’t enough, of course. As long as the components keep exposing multiple public initializers, the multiple-choice question remains at the call site. So SwiftUI reworked the shared containers at the same time.

For `Group`, the new interface can be summarized as:

```swift
public struct Group<Content> { ... }

extension Group {
    public init(@ContentBuilder content: () -> Content)
}

extension Group: View where Content: View {}
extension Group: ToolbarContent where Content: ToolbarContent {}
extension Group: Commands where Content: Commands {}
```

For the domains `ContentBuilder` has unified — View, ToolbarContent, Commands, and so on — a `Group { ... }` in ordinary client code now prefers this general-purpose assembly entry point with no domain constraint. The compiler no longer has to enumerate several identically shaped initializers at every level. It determines `Content` first, then lets `Group<Content>` acquire the appropriate capability through conditional conformance.

To be clear, this doesn’t mean the compile time of all SwiftUI code drops by a comparable proportion, and it certainly doesn’t mean runtime performance — view creation, updates, or rendering — has improved. What Apple set out to fix is the cross-domain, nestable shared components like `Group`, `ForEach`, and `Section`. What gets shorter is the type-checking time of expressions containing those overload trees.

> Low-priority compatibility overloads retained for back deployment, along with domains that haven’t been fully unified yet — Scene, Table, Tab — may still offer dedicated entry points.

## Does Changing the API Declaration Really Help?

To test this reasoning, I built `LegacyGroup` (the old model, with multiple construction entry points) and `UnifiedContentBuilder` (the new model, keeping a single unconstrained builder and initializer) using the two declaration styles described above. [The code is on Gist](https://gist.github.com/85c57945ceb6b12ffb8c64fa12a30fd0.git?utm_source=Fatbobman%20Blog&utm_medium=web). The two are equally expressive; the only difference is *where* the decision happens.

I generated nesting from 1 to 11 levels deep. The results:

| Nesting depth | Old scopes | New scopes | Old sema | New sema | Old allocation | New allocation |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 17 | 8 | 3.40 ms | 3.22 ms | 11.80 MB | 11.35 MB |
| 3 | 217 | 22 | 5.34 ms | 3.57 ms | 11.96 MB | 11.31 MB |
| 6 | 6,067 | 43 | 56.56 ms | 3.79 ms | 15.34 MB | 11.35 MB |
| 8 | 54,667 | 57 | 518.13 ms | 3.87 ms | 43.32 MB | 11.53 MB |
| 9 | 164,017 | 64 | 1.56 s | 5.04 ms | 103.01 MB | 11.76 MB |
| 10 | 492,067 | 71 | 4.76 s | 4.33 ms | 289.95 MB | 11.76 MB |
| 11 | 1,063,341 | 78 | failed after 10.90 s | 5.38 ms | 607.85 MB | 11.83 MB |

> The data was collected by passing `-stats-output-dir` to `swiftc`; `scopes` corresponds to the `Sema.NumConstraintScopes` statistic. Semantic analysis times are wall-clock timings from a single run and will fluctuate with machine load. “Allocation” is the maximum allocation recorded by the Swift frontend, not process RSS.

At 11 levels, the old model finally triggered that all-too-familiar diagnostic:

```text
the compiler is unable to type-check this expression in reasonable time
```

The same difference shows up in real SwiftUI when you compare Xcode versions. I had Xcode 26.6 and Xcode 27 beta 4 type-check the same five-level `Section → Group → ForEach` nesting, both spelled with `@ViewBuilder`:

| Toolchain | Constraint scopes | Semantic analysis |
| --- | --- | --- |
| Xcode 26.6 / Swift 6.3.3 | 1,050,052 | 11.06 s |
| Xcode 27 beta 4 / Swift 6.4 | 189 | 26.97 ms |

This isn’t a strictly controlled experiment, of course — the Swift compiler also went from 6.3.3 to 6.4, so the entire difference can’t be credited to SwiftUI’s API changes. But it does answer a different question: the combinatorial search Apple demonstrated at WWDC isn’t a theoretical construct. It reproduces reliably against the real SwiftUI interface in Xcode 26.

## ContentBuilder Isn’t a Cure-All

Beyond the fact that it currently covers only some of SwiftUI’s result builders, Apple has documented a set of characteristic problems related to `ContentBuilder` in [TN3211](https://developer.apple.com/documentation/technotes/tn3211-resolving-swiftui-source-incompatibilities-for-state-and-contentbuilder?utm_source=Fatbobman%20Blog&utm_medium=web).

For example:

```swift
.overlay(Color.blue.opacity(0.2)) // may become ambiguous in some contexts now that constraints are relaxed

.overlay { // the closure form restores an unambiguous API context
    Color.blue.opacity(0.2)
}
```

Similarly, when MapKit is imported alongside SwiftUI, an empty block such as `Group {}` may lack the information needed to choose among different empty content types — writing `EmptyView()` explicitly resolves it. Charts still keeps a dedicated compatibility builder path on older deployment targets, and complex conditional branches can be extracted into a standalone `@ChartContentBuilder` function to narrow the type-checking scope.

Together these cases reveal a general rule: **once the builder stops pre-supplying a protocol for every expression, code that relied on those implicit domain constraints to resolve overloads has to put the context back — via closures, explicit types, or smaller function boundaries.**

## ContentBuilder Isn’t Just for Views

Once you understand what Apple changed, an interesting capability comes into view: third parties can borrow `ContentBuilder` to construct non-`View` DSLs.

Say we define a set of article nodes:

```swift
import SwiftUI

protocol ArticleContent {
    func render() -> [String]
}

struct Heading: ArticleContent {
    let text: String

    func render() -> [String] {
        ["# \(text)"]
    }
}

struct Paragraph: ArticleContent {
    let text: String

    func render() -> [String] {
        [text]
    }
}
```

Then we make `ContentBuilder`’s structural outputs conform to `ArticleContent` whenever their elements qualify:

```swift
extension TupleContent: ArticleContent
where repeat each Content: ArticleContent {
    func render() -> [String] {
        var output: [String] = []

        for element in repeat each content {
            output += element.render()
        }

        return output
    }
}

extension Optional: ArticleContent
where Wrapped: ArticleContent {
    func render() -> [String] {
        self?.render() ?? []
    }
}

extension _ConditionalContent: ArticleContent
where TrueContent: ArticleContent,
      FalseContent: ArticleContent {
    func render() -> [String] {
        switch storage {
        case let .trueContent(content):
            content.render()
        case let .falseContent(content):
            content.render()
        }
    }
}

extension ForEach: ArticleContent
where Content: ArticleContent {
    func render() -> [String] {
        data.flatMap { content($0).render() }
    }
}
```

And now `@ContentBuilder` can be reused directly:

```swift
@ContentBuilder
func article(showNote: Bool) -> some ArticleContent {
    Heading(text: "ContentBuilder")
    Paragraph(text: "Assemble the structure first, verify the capability after.")

    if showNote {
        Paragraph(text: "This is an optional passage.")
    }

    ForEach(
        ["Remove the overloads", "Conditional conformance"],
        id: \.self
    ) {
        Paragraph(text: $0)
    }
}
```

> The code above was verified in Xcode 27 beta 4.

This is more than sharing an attribute name. `TupleContent`, `Optional`, `_ConditionalContent`, and `ForEach` all come along for the ride — and building a custom result builder with the same completeness used to take considerably more code than what you see above.

> A caveat: not all of the internal content of `Group` and `Section` is suitable for third parties to traverse.

## What ContentBuilder Teaches Us About API Design

The significance of `ContentBuilder` reaches beyond SwiftUI. It demonstrates a design principle applicable to generic DSLs: **when several domains share the same structural operations, keep the construction path unique wherever possible, and leave domain capability to be verified through conditional conformance on the resulting type.**

That doesn’t mean “defer every constraint as long as possible.” If deferring a constraint robs an overload of the context it needs, or lets an error range across too much code, a constraint at the entry point still earns its keep.

What genuinely needs avoiding is this: placing several identically shaped overloads, differing only in their protocol constraints, at every nesting node — all to express a handful of identical structural capabilities.

## Why Didn’t SwiftUI Do It This Way From the Start?

Having worked out what `ContentBuilder` changed, one question kept nagging at me: if the approach is this natural, why didn’t SwiftUI adopt it on day one? Tracing the evolution of SwiftUI and Swift, I arrived at the following conjectures:

- SwiftUI’s content domains were added year by year. That overload tree didn’t exist at the outset — it grew a layer at a time alongside the feature set.
- `TupleContent` depends on parameter packs and variadic generic types, and that language capability has only recently matured. Before it existed, this design simply couldn’t be written.
- ABI stability and back deployment require Apple to retain compatibility paths. A change this simple on the surface means a great deal of testing and preparation for a framework.

You could say Apple’s emphasis on developer experience and performance in 2026 was only the final push that got `ContentBuilder` over the line. It reads more like the natural outcome of a long evolution.

Apple didn’t make much of `ContentBuilder`, and for the vast majority of developers and scenarios it truly is invisible. But its influence on SwiftUI — and on Swift API design more broadly — shouldn’t be underestimated.

There are surely more of these quiet but consequential changes still undiscovered, waiting to be dug up.

Tags [#SwiftUI](https://fatbobman.com/en/tags/SwiftUI/)

### Subscribe to Fatbobman

Weekly Swift & SwiftUI highlights. Join developers.
