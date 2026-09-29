On iPhone Duo, navigation controls, toolbar actions, and tabs can move into a shared vertical bar along the edge of the display, leaving more vertical space for content. Apps built with the iOS 27.1 SDK get this adaptation through standard containers such as `NavigationStack`, `NavigationSplitView`, and `TabView`. We continue to declare toolbar actions in their usual placements, and SwiftUI determines how to present them in the current context.

![The same photo editor with horizontal toolbars on iPhone 18 Pro and a vertical toolbar along the right edge of the outer display on iPhone Duo](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/toolbar-comparison@2x.PYDMmLrAhAqgRfgZpJVSgoKB57Jlo59TO2pGsXdtSXY.png)

iOS 27.1 also introduces APIs for refining that presentation. We can control which items participate in the vertical bar, adapt custom content to its placement, and express which controls should remain visible when space becomes limited. We can also disable vertical bars for interfaces that benefit from a horizontal arrangement. In this post, we'll look at these additions and when to use them.

## Controlling an item's axis with axisBehavior(\_:)

SwiftUI uses an item's content to decide whether it can move into a vertical bar. Standard controls with symbols can participate automatically, while text-only items and more complex custom content remain horizontal by default. The new [axisBehavior(\_:)](https://developer.apple.com/documentation/swiftui/toolbarcontent/axisbehavior\(_:\)) modifier lets us override this choice for individual toolbar items.

For example, we can create a menu with a custom label that combines a shape and text. With the default `automatic` axis behavior, SwiftUI places this menu in the horizontal bar:

```article-page
ToolbarItem(placement: .bottomBar) {
    Menu {
        // Actions for choosing the line width
    } label: {
        VStack {
            Capsule()
                .frame(width: 24, height: width)
            Text("\(width, format: .number) pt")
        }
    }
}
```
![A menu with a custom shape-and-text label stays in the bottom horizontal bar on iPhone Duo with automatic axis behavior](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/axis-automatic@2x.jF86Gn6hGhVulv-q29z-V4QSDK4Y-wlslap560azNlk.png)

If our custom toolbar button label is small enough to fit in the vertical bar, we can apply `.axisBehavior(.verticalPreferred)` to the enclosing `ToolbarItem`:

```article-page
ToolbarItem(placement: .bottomBar) {
    // ... Menu with the custom label shown above ...
}
.axisBehavior(.verticalPreferred)
```

The menu now joins the other controls along the right edge, leaving no toolbar item at the bottom of the content area.

![The same custom menu moves into the right-hand vertical bar on iPhone Duo with verticalPreferred](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/axis-vertical-preferred@2x.Sd8_zQ0K2W8AQ3SoIi50v7_RV0QnKFPpld8K58boZXs.png)

For custom content that needs more horizontal space, we can use the `horizontalOnly` option to keep the item in a horizontal bar. For example, a slider already stays horizontal with `automatic`, but applying `horizontalOnly` makes that constraint explicit:

```article-page
ToolbarItem(placement: .bottomBar) {
    Slider(value: $width, in: 2...6, step: 2) {
        Text("Line width")
    }
    .frame(width: 180)
}
.axisBehavior(.horizontalOnly)
```
![A slider remains in the bottom horizontal toolbar on iPhone Duo with horizontalOnly](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/axis-horizontal-slider@2x.mc7WKGWS2k2DUFEMnZomrDFc-DGu9fjRdDz4_2JQvh4.png)

The `horizontalOnly` option is also useful when an item's content changes and we want its axis to remain stable across those changes.

## Reading the vertical bar's preferred edge with toolbarVerticalEdge

We can also adapt the layout of custom toolbar content using the new [toolbarVerticalEdge](https://developer.apple.com/documentation/swiftui/environmentvalues/toolbarverticaledge) environment value. It indicates whether the system prefers the `leading` or `trailing` edge of the interface for the vertical bar. When the system doesn't use vertical bars in the current context, the value is `nil`, allowing us to choose a horizontal layout for our custom content instead.

For example, we can adapt a custom toolbar button label for an item whose `axisBehavior(_:)` modifier is set to `verticalPreferred`. We arrange its shape and text horizontally when `toolbarVerticalEdge` is `nil`, and vertically otherwise:

```article-page
struct AdaptiveToolbarLabel: View {
    @Environment(\.toolbarVerticalEdge) private var verticalEdge

    let width: CGFloat

    var body: some View {
        let layout = verticalEdge == nil
            ? AnyLayout(HStackLayout())
            : AnyLayout(VStackLayout())

        layout {
            Capsule()
                .frame(width: 24, height: width)
            Text("\(width, format: .number) pt")
        }
    }
}
```

The shape and text now appear side by side in a horizontal toolbar, and one above the other in a vertical bar.

![The custom menu label arranges its shape and text horizontally on the inner display of iPhone Duo and vertically on its outer display](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/adaptive-label@2x.mHhyBfJZ12wuZlC4TxbKxttUp4whCaf1mNIr2XNGZYQ.png)

If we set a toolbar item's `axisBehavior(_:)` modifier to `horizontalOnly`, we should keep its label's horizontal layout even when `toolbarVerticalEdge` has a value.

## Prioritizing actions or tabs with toolbarVerticalCompressionBehavior(\_:)

When tabs and toolbar items share a vertical bar, there may not be enough space to show all of them. SwiftUI can make room by moving toolbar items into overflow or collapsing the tabs into a single control for switching between them. The new [toolbarVerticalCompressionBehavior(\_:)](https://developer.apple.com/documentation/swiftui/view/toolbarverticalcompressionbehavior\(_:\)) modifier lets us choose which happens first.

The default `automatic` behavior favors the tab bar on iOS, so toolbar items move into overflow before the tabs collapse. We can also request this behavior explicitly with `prefersTabBar`.

For example, we can give the tab bar priority over the toolbar actions provided by a view in a navigation stack:

```article-page
TabView {
    Tab("Library", systemImage: "photo.on.rectangle") {
        NavigationStack {
            PhotoEditor()
                .toolbarVerticalCompressionBehavior(.prefersTabBar)
        }
    }

    // Other tabs
}
```

When space is limited, SwiftUI keeps the tabs visible and moves some of the editor's toolbar items into overflow. In this case, the arrow tool stays in the bar, while the remaining editing controls move into the overflow menu.

![All three tabs stay visible on iPhone Duo while the rectangle tool, drawing tool, and line-width menu move into overflow](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/compression-tabs@2x.QlReFWVllc_k0eaw3ed2_2IS8PEhWVH-IWvfRxgbgB4.png)

When toolbar actions should take priority over tab navigation, we can set the `toolbarVerticalCompressionBehavior(_:)` modifier to `prefersToolbarItems`:

```article-page
PhotoEditor()
    .toolbarVerticalCompressionBehavior(.prefersToolbarItems)
```

The editor's toolbar items now remain in the bar, while the tabs collapse into a single control for switching between them.

![All editing controls stay visible on iPhone Duo while the tabs collapse into a single control](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/compression-actions@2x.f9WufPwy0OcZLHeLif_P8j4Oa8IWEYw94qtpfdD2mM0.png)

The compression behavior sets the priority between tabs and toolbar items. To control which individual toolbar items remain visible when the toolbar compresses, we can use the `visibilityPriority(_:)` modifier covered in [Adaptive SwiftUI toolbars in iOS 27](https://nilcoalescing.com/blog/AdaptiveSwiftUIToolbarsInIOS27/).

## Disabling vertical bars with toolbarVerticalBehavior(\_:)

For interfaces that benefit from more horizontal space, we can opt out of vertical bars with the new [toolbarVerticalBehavior(\_:)](https://developer.apple.com/documentation/swiftui/view/toolbarverticalbehavior\(_:\)) modifier. Setting it to `disabled` returns bar content to the standard horizontal top and bottom bars and restores the status bar's horizontal arrangement.

For example, in a sheet where the only toolbar action is a close button, the vertical bar leaves less width for the content:

![A help sheet with its close button in a vertical bar, reducing the width available to the text](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/sheet-automatic@2x.BDqSwDWiWgzqhwumZDkp6UtRL-cy5DHjWTAevlxNYLM.png)

To disable vertical bars for a window or presentation, we can apply the `toolbarVerticalBehavior(_:)` modifier with the `disabled` option to a view within it:

```article-page
.sheet(isPresented: $showsHelp) {
    AnnotationHelp()
        .toolbarVerticalBehavior(.disabled)
}
```
![The same help sheet with its close button at the top and more horizontal space for the text](https://nilcoalescing.com/static/blog/ConfiguringSwiftUIToolbarsOnIPhoneDuo/sheet-disabled@2x.wG4CCs3GbzcR5f1_IhtFsvf7H6yOL1QFc7R9cJ-AVic.png)

When we apply the modifier inside a sheet, it controls the sheet's bars independently of the presenting view. Within a window, SwiftUI uses the bar behavior specified by the topmost view in a `NavigationStack`, the selected tab in a `TabView`, or the trailingmost column in a `NavigationSplitView`.

Setting different bar behaviors on views in the same navigation stack can make the bars switch between horizontal and vertical as we navigate. Apple recommends keeping the behavior consistent rather than changing it frequently during navigation or in response to temporary view state.

If you are looking to build a strong foundation in SwiftUI, my book [SwiftUI Fundamentals](https://books.nilcoalescing.com/swiftui-fundamentals?utm_source=nilcoalescing&utm_term=ConfiguringSwiftUIToolbarsOnIPhoneDuo&utm_content=inline&utm_medium=blog) takes a deep dive into the framework's core principles and APIs to help you understand how it works under the hood and how to use it effectively in your projects. And my more recent book [The SwiftUI Way](https://books.nilcoalescing.com/the-swiftui-way?utm_source=nilcoalescing&utm_term=ConfiguringSwiftUIToolbarsOnIPhoneDuo&utm_content=inline&utm_medium=blog) helps you adopt recommended patterns, avoid common pitfalls, and use SwiftUI's native tools appropriately to work with the framework rather than against it.

For more resources on Swift and SwiftUI, check out my other [books](https://books.nilcoalescing.com?utm_source=nilcoalescing&utm_term=ConfiguringSwiftUIToolbarsOnIPhoneDuo&utm_content=inline&utm_medium=blog) and book [bundles](https://books.nilcoalescing.com/bundles?utm_source=nilcoalescing&utm_term=ConfiguringSwiftUIToolbarsOnIPhoneDuo&utm_content=inline&utm_medium=blog).
