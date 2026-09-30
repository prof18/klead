18 November 2025

---

*Posted by Ajesh R Pai - Developer Relations Engineer & Ben Weiss - Senior Developer Relations Engineer*

![](https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEjerUR41OKjjCgIPVCNWdqXtlOOk4nr9NtGNRpeLSxpvzH_2c1VJblar5Uet6O4Y2wf1P18QQ6tyz7WagZVZrz8hBFCYhyjHUzsZJ4eNSovpOs_ybXDKsLA7tVyRm1R3OdwyOPkCtXjgeCViCHLf7c-0wHH7R7VpwtFZRmK0PNi4Rfmp8WO2mA4A409slU/s1600/SPOTLIGHT_configure&troubleshoot01.png)

In modern Android development, shipping a small, fast, and secure application is a fundamental user expectation. The Android build system's primary tool for achieving this is the R8 optimizer, the compiler that handles dead code and resource removal for shrinking, code renaming or minification, and app optimization.

Enabling R8 is a critical step in preparing an app for release, but it requires developers to provide guidance in the form of "Keep Rules."

After reading this article, check out the Performance Spotlight Week video on enabling, debugging and troubleshooting the R8 optimizer on YouTube.

![](https://www.youtube.com/watch?v=A0I6pNSM14o)

## Why Keep Rules are needed

The need to write Keep Rules stems from a core conflict: R8 is a static analysis tool, but Android apps often rely on dynamic execution patterns like reflection or calls in and out of native code using the JNI (Java Native Interface).

R8 builds a graph of used code by analyzing direct calls. When code is accessed in a dynamic way, R8's static analysis cannot predict that and it will identify that code as unused and remove it, leading to runtime crashes.

A keep rule is an explicit instruction to the R8 compiler, stating: "This specific class, method, or field is an entry point that will be accessed dynamically at runtime. You must keep it, even if you cannot find a direct reference to it."

See the official guide for more details on [Keep Rules](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview).

## Where to write Keep Rules

Custom Keep Rules for an application are written in text file. By convention, this file is named proguard-rules.pro and is located in the root of the app or library module. This file is then specified in your module's build.gradle.kts file's release build type.

```
release {
    isShrinkResources = true
    isMinifyEnabled = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
    )
}
```

### Use the correct default file

The getDefaultProguardFile method imports a default set of rules provided by the Android SDK. When using the wrong file your app might not be optimized. Make sure to use proguard-android-optimize.txt. This file provides the default Keep Rules for standard Android components andenables R8's code optimizations. The outdated proguard-android.txt only provides the Keep Rules but does not enable R8's optimizations.

![](https://blogger.googleusercontent.com/img/a/AVvXsEiiukX26-UQMKnjkz1jgYseUm3HkYhJxeIclKTT8sUB1MUL2XWLFwCxOddYcWCUsCd-tmq7nbppdX1pacpX46j0VBRub11_emjc0j4q2KbXfH3fvBGsfkkROSZ0OBk9UCQqOuAwj8X0uG_FuVem5hI_lVTFSc8gP4Q9lBYVf3pQLtTBKuudhVK9RqavRvc)

Since this is a serious performance problem, we are starting to warn developers about using the wrong file, starting in Android Studio Narwhal 3 Feature Drop. And starting with the Android Gradle Plugin Version 9.0 we're no longer supporting the outdated proguard-android.txt file. So make sure you upgrade to the optimized version.

## How to write Keep Rules

A keep rule consists of three main parts:

1. An option like -keep or -keepclassmembers
2. Optional modifiers like allowshrinking
3. A class specification that defines the code to match

For the complete syntax and examples, refer to the guidance to [add Keep Rules](https://developer.android.com/topic/performance/app-optimization/add-keep-rules).

## Keep Rule anti-patterns

It's important to know about best practices, but also about anti-patterns. These anti-patterns often arise from misunderstandings or troubleshooting shortcuts and can be catastrophic for a production build's performance.

## Global options

These flags are global toggles that should never be used in a release build. They are only for temporary debugging to isolate a problem.

Using -dontotptimize effectively disables R8's performance optimizations leading to a slower app.

When using -dontobfuscate you disable all renaming and using -dontshrink turns off dead code removal. Both of these global rules increase app size.

Avoid using these global flags in a production environment wherever possible for a more performant app user experience.

## Overly broad keep rules

The easiest way to nullify R8's benefits is to write overly-broad Keep Rules. Keep rules like the one below instruct the R8 optimizer to not shrink, not obfuscate, and not optimize any class in this package or any of its sub-packages. This completely removes R8's benefits for that entire package. Try to write narrow and specific Keep Rules instead.

```
-keep class com.example.package.** { *;} // WIDE KEEP RULES CAUSE PROBLEMS
```

## The inversion operator (!)

The inversion operator (!) seems like a powerful way to exclude a package from a rule. But it's not that simple. Take this example:

```
-keep class !com.example.my_package.** { *; } // USE WITH CAUTION
```

You might think that this rule means "do not keep classes incom.example.package." But it actually means "keep every class, method and propertyin the entire application that is not in com.example.package." If that came as a surprise to you, best check for any negations in your R8 configuration.

## Redundant rules for Android components

Another common mistake is to manually add Keep Rules for your app's Activities, Services, or BroadcastReceivers. This is unnecessary. The default proguard-android-optimize.txt file already includes the relevant rules for these standard Android components to work out of the box.

Also many libraries bring their own Keep Rules. So you should not have to write your own rules for these. In case there is a problem with Keep Rules from a library you're using, it is best to reach out to the library author to see what the problem is.

## Keep Rule best practices

Now that you know what not to do, let's talk about best practices.

## Write narrow Keep Rules

Good Keep Rules should be as narrow and specific as possible. They should preserve only what is necessary, allowing R8 to optimize everything else.

| Rule | Quality |
| --- | --- |
| `-keep class com.example.** { ; }` | Low: Keeps an entire package and its subpackages |
| `-keep class com.example.MyClass { ; }` | Low: Keeps an entire class which is likely still too wide |
| `-keepclassmembers class com.example.MyClass { private java.lang.String secretMessage; public void onNativeEvent(java.lang.String); }` | High: Only relevant methods and properties from a specific class are kept |

## Use common ancestors

Instead of writing separate Keep Rules for multiple different data models, write one rule that targets a common base class or interface. The below rule tells R8 to keep any members of classes that implement this interface and is highly scalable.

```
# Keep all fields of any class that implements SerializableModel
-keepclassmembers class * implements com.example.models.SerializableModel {
    <fields>;
}
```

## Use Annotations to target multiple classes

Create a custom annotation (e.g., @Serialize) and use it to "tag" classes that need their fields preserved. This is another clean, declarative, and highly scalable pattern. You can create Keep Rules for already existing annotations from frameworks you're using as well.

```
# Keep all fields of any class annotated with @Serialize
-keepclassmembers class * {
    @com.example.annotations.Serialize <fields>;
}
```

## Choose the right Keep Option

The Keep Option is the most critical part of the rule. Choosing the wrong one can needlessly disable optimization.

| Keep Option | What It Does |
| --- | --- |
| -keep | Prevents the class and members mentioned in the declaration from being removed or renamed. |
| -keepclassmembers | Prevents the specified members from being removed or renamed, but allows the class itself to be removed but only on classes which are not otherwise removed. |
| -keepclasseswithmembers | A combination: Keeps the class and its members, only if all the specified members are present. |

You can find more about the keep option in our [documentation for Keep Options.](https://developer.android.com/topic/performance/app-optimization/add-keep-rules#choose-keep)

## Allow optimization with Modifiers

Modifiers like allowshrinking and allowobfuscation relax a broad -keep rule, giving optimization power back to R8. For example, if a legacy library forces you to use -keep on an entire class, you might be able to reclaim some optimization by allowing shrinking and obfuscation:

```
# Keep this class, but allow R8 to remove it if it's unused and allow R8 to rename it.
-keep,allowshrinking,allowobfuscation class com.example.LegacyClass
```

## Add global options for additional optimization

Beyond Keep Rules, you can add global flags to your R8 configuration file to encourage even more optimization.

-repackageclasses is a powerful option that instructs R8 to move all obfuscated classes into a single package. This saves significant space in the DEX file by removing redundant package name strings.

-allowaccessmodification allows R8 to widen access (e.g., private to public) to enable more aggressive inlining. This is now enabled by default when using proguard-android-optimize.txt.

Warning: Library authors must never add these global optimization flags to their consumer rules, as they would be forcibly applied to the entire app.

And to make it even more clear, in version 9.0 of the Android Gradle Plugin we're going to start ignoring global optimization flags from libraries altogether.

## Best practices for libraries

Every Android app relies on libraries one way or another. So let's talk about best practices for libraries.

## For library developers

If your library uses reflection or JNI, you have the responsibility to provide the necessary Keep Rules to its consumers. These rules are placed in a consumer-rules.pro file, which is then automatically bundled inside the library's AAR file.

```
android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    ...
}
```

## For library consumers

### Filter out problematic Keep Rules

If you must use a library that includes problematic Keep Rules, you can filter them out in your build.gradle.kts file starting with AGP 9.0 This tells R8 to ignore the rules coming from a specific dependency.

```
release {
    optimization.keepRules {
        // Ignore all consumer rules from this specific library
        it.ignoreFrom("com.somelibrary:somelibrary")
    }
}
```

### The best Keep Rule is no Keep Rule

The ultimate R8 configuration strategy is to remove the need to write Keep Rules altogether. For many apps can be achieved by choosing modern libraries that favor code generation over reflection. With code generation, the optimizer can more easily determine what code is actually used at runtime and what code can be removed. Also not using any dynamic reflection means no "hidden" entry points, and therefore, no Keep Rules are needed. When choosing a new library, always prefer a solution that uses code generation over reflection.

For more information about how to choose libraries, check [choose library wisely](https://developer.android.com/topic/performance/app-optimization/choose-libraries-wisely#tips).

## Debugging and troubleshooting your R8 configuration

When R8 removes code it should have kept, or your APK is larger than expected, use these tools to diagnose the problem.

## Find duplicate and global Keep Rules

Because R8 merges rules from dozens of sources, it can be hard to know what the "final" ruleset is. Adding this flag to your proguard-rules.pro file generates a complete report:

```
# Outputs the final, merged set of rules to the specified file
-printconfiguration build/outputs/logs/configuration.txt
```

You can search this file to find redundant rules or trace a problematic rule (like -dontoptimize) back to the specific library that included it.

## Ask R8: Why are you keeping this?

If a class you expected to be removed is still in your app, R8 can tell you why. Just add this rule:

```
# Asks R8 to explain why it's keeping a specific class
class com.example.MyUnusedClass
-whyareyoukeeping
```

During the build, R8 will print the exact chain of references that caused it to keep that class, allowing you to trace the reference and adjust your rules.

For a full guide, check out the [troubleshoot R8](https://developer.android.com/topic/performance/app-optimization/test-and-troubleshoot-the-optimization) section.

## Next steps

R8 is a powerful tool for enhancing Android app performance. Its effectiveness, depends on a correct understanding of its operation as a static analysis engine.

By writing specific, member-level rules, leveraging ancestors and annotations, and carefully choosing the right keep options, you can preserve exactly what is necessary. The most advanced practice is to eliminate the need for rules entirely by choosing modern, codegen-based libraries over their reflection-based predecessors.

As you're following along Performance Spotlight Week, make sure to check out today's Spotlight Week video on YouTube and continue with our R8 challenge. Use #optimizationEnabled for any questions on enabling or troubleshooting R8. We're here to help.

It's time to see the benefits for yourself.

We challenge you to enable R8 full mode for your app today.

1. Follow our developer guides to get started: [Enable app optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
2. Check if you still use proguard-android.txt and replace it with proguard-android-optimize.txt.
3. Then, measure the impact. Don't just feel the difference, verify it. Measure your performance gains by adapting the code from our[Macrobenchmark sample app on GitHub](https://github.com/android/performance-samples/blob/main/MacrobenchmarkSample/macrobenchmark/src/main/kotlin/com/example/macrobenchmark/benchmark/startup/FullyDrawnStartupBenchmark.kt) to measure your startup times before and after.

We're confident you'll see a meaningful improvement in your app's performance.

While you're at it, use the social tag #AskAndroid to bring your questions. Throughout the week our experts are monitoring and answering your questions.

Stay tuned for tomorrow, where we'll talk about Profile Guided Optimization with Baseline and Startup Profiles, share how Compose rendering performance improved over the past releases and share performance considerations for background work.
