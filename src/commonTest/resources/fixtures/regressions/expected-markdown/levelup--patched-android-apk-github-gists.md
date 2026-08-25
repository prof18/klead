![](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*EHyyNUobQyRp5RMa_7Wnqw.png)

When people talk about reverse-engineering resistance on Android, the conversation often jumps straight to obfuscation.

Obfuscation matters. R8 matters. Naming things less helpfully can raise the cost of analysis. But in most practical reviews, that is not where the first useful finding comes from.

The first useful finding is usually much simpler: a hardcoded secret, a readable asset, a client-side check that can be patched, or a dynamic loading path that trusts a file it should never execute.

That is why I like the reverse-engineering lab in the Android Security Training project. It does not treat resilience as a checklist item. It gives you a secure build and a deliberately vulnerable one, then lets you compare the difference directly. You can open the APK. You can inspect the code. You can patch the vulnerable path. And then you can see what changes when the secure build refuses the same shortcuts.

## The Vulnerable Build Gives Attackers Too Much to Work With

The first vulnerable example is intentionally blunt:

[Android Security Training article 3 vulnerable hardcoded secret and asset read](https://levelup.gitconnected.com/media/f3777200497d4a015ceae5d8bde2d2b0)

It shows two common mistakes at once: secrets compiled into the APK and sensitive material shipped in \`assets/\`.

Neither one requires advanced reversing. If a key appears as a string in the app, JADX can usually find it. If a file ships in \`assets/\`, \`unzip\` or \`apktool\` can usually extract it. That is the point of the demo. The problem is not that an attacker has a magic tool. The problem is that the build packaged information that should not have been there in the first place.

This is why I still like showing the mechanics plainly. Many developers already know they should not ship secrets in a client app. What changes the conversation is seeing how little work it takes to recover them once they do.

## Patching an APK Is Less Dramatic Than It Sounds

The tamper-check part of the lab is useful for the same reason. The vulnerable app can be decoded, patched, rebuilt, re-signed, and installed again.

The workflow is ordinary:

[Android Security Training article 3 APK patch workflow](https://levelup.gitconnected.com/media/0294744cafcb5df1a92253ccdf562865)

That matters because “someone could patch this” can sound abstract until you do it once. If a critical decision exists only on the client and can be changed by flipping a branch, it is fragile by design.

Client-side checks can still be useful. They can add friction, detect obvious tampering, and improve telemetry. But they should not be the only thing standing between an attacker and a sensitive server-side decision.

## The Secure Build Is Stronger Because It Uses Policy

The secure helper takes a different shape:

[Android Security Training article 3 secure signature verification and dynamic load block](https://levelup.gitconnected.com/media/cc708732fad37a8dfd4d5d51ff511158)

It computes the app signing certificate digest at runtime and compares it with the expected release signing digest. That is a more meaningful identity check than trusting a package name or assuming the installed app is still the one you shipped.

It also refuses dynamic code loading in secure builds.

I like that decision because it is clear. The secure flavor does not try to make untrusted runtime code loading “mostly okay.” It says the feature is not part of the secure policy.

There is still nuance here. Runtime signature verification is useful, but it does not replace server-side authorization or backend trust decisions. Obfuscation is useful, but it does not fix hardcoded secrets. The secure build is better because the controls line up with the risk instead of decorating it.

## Dynamic Code Loading Becomes a Liability Quickly

The vulnerable dynamic loading path is the most interesting part of the lab:

[Android Security Training article 3 vulnerable dynamic DexClassLoader path](https://levelup.gitconnected.com/media/fa81dc6ce48140400472675f53e8dc1f)

The helper accepts a file name, looks in the app-specific external files directory, copies the DEX or JAR into the internal code cache, and loads it with \`DexClassLoader\`. It also supports a \`self\` mode to load the app APK itself for demonstration.

That is a good teaching example because the trust boundary is visible. A file that originates outside the app’s trusted code path is copied into a place where it can be executed with the app’s privileges. Even though the demo trims path input down to a file name, the larger policy problem remains: the vulnerable build accepts runtime code from a location the secure build should not trust.

The hands-on version is straightforward. Build a minimal \`dev.training.dynamic.Hello\` class, convert it to DEX, push it into the app-specific external directory, and enter \`dynamic.dex\` in the vulnerable UI. If the class resolves, the app executes code that was not part of the original build.

That is exactly why “we only use \`DexClassLoader\` for one small internal feature” should make you slow down. If runtime code loading is genuinely required, it needs strong provenance checks, strict allowlisting, and a threat model that survives hostile input. Most apps are better off not doing it.

## What I Would Check in a Real Review

The lab examples are small, but the review questions transfer directly to production apps.

I would start with the APK itself, not only the source tree. Search the decompiled output for URLs, API keys, feature flags, tokens, and words like \`secret\`, \`private\`, \`debug\`, \`admin\`, and \`internal\`. Then inspect \`assets/\`, \`res/raw/\`, and bundled configuration files. If sensitive material is there, the app has already lost control of it.

After that, I would look for client-side decisions that are too valuable to trust locally. Premium access, account state, fraud checks, feature entitlements, and backend authorization should not depend on a branch inside the APK. The client can collect signals and improve the experience, but the server should make the decision that matters.

Finally, I would search for runtime loading APIs and plugin-like behavior. \`DexClassLoader\`, \`PathClassLoader\`, native library loading, script engines, and downloaded configuration that changes behavior all deserve a closer look. The question is not only whether the code compiles. The question is who controls the bytes that eventually run.

## A Better Secure Pattern

The safer pattern is usually less exciting than the attack.

Do not put long-lived secrets in the app. Treat obfuscation as friction, not as secrecy. Move sensitive authorization to the backend. If the app needs to prove its own identity to a server, combine normal authentication with signing identity checks, Play Integrity or another attestation signal where appropriate, and server-side enforcement.

For dynamic code, the default answer should be no. If there is a real business need, keep the loading path narrow, verify provenance cryptographically, avoid writable external locations, and fail closed when validation is missing or ambiguous.

## Closing Thoughts

The reverse-engineering lab works because it stays grounded. It does not pretend obfuscation is useless, and it does not pretend obfuscation is enough.

The first line of defence is more basic: do not ship secrets in the APK, do not treat assets as hidden storage, do not rely on patchable client checks for decisions that matter, and do not execute untrusted code at runtime unless you can defend the entire loading path.

If the vulnerable APK gives away the useful parts before anyone even opens smali, the reversing was not too advanced. The build was too generous.
