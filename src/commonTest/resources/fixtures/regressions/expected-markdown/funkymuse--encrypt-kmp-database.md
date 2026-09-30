SQLCipher + Firebase in KMP: When SPM import Symbol Conflicts Break Your Encryption; Properly encrypt your Room database in KMP

## The Starting Point

I know, long title… my creativity turned into lengthy title

I recently needed encrypted database storage in my Kotlin Multiplatform app using AndroidX Room. After some research, i found Paris Tsiogas’s excellent guide on [Encrypted Room Database in KMP](https://desquared.notion.site/Encrypted-Room-Database-in-Kotlin-Multiplatform-KMP-for-Android-iOS-25b9aac7b816801b9067c627602335c6) which walks through the full setup, Gradle dependencies, platform-specific implementations, and a custom `SQLCipherNativeDriver` that wraps `NativeSQLiteDriver` and applies `PRAGMA key` immediately after opening the connection.

The approach is clean: on Android you use `net.zetetic:sqlcipher-android` with `SupportOpenHelperFactory` and on iOS you link SQLCipher and wrap the native driver to inject the encryption key. The original guide uses CocoaPods, but my project uses Kotlin’s experimental [SPM import](https://kotlinlang.org/docs/native-spm.html) welp i integrated SQLCipher.swift via `swiftPackage` in `build.gradle.kts` instead. Worked great, everything compiled, database was encrypted on both platforms.

Then i added Firebase.

[![Firebase](https://funkymuse.dev/assets/img/encrypt_kmp_db/unencrypted_db_meme.jpg)](https://funkymuse.dev/assets/img/encrypt_kmp_db/unencrypted_db_meme.jpg)

## The Problem

My KMP project uses Kotlin’s experimental [SPM import](https://kotlinlang.org/docs/native-spm.html) (not CocoaPods) to bring in Firebase iOS SDK, GoogleSignIn, and SQLCipher.swift, the moment Firebase entered the picture, the iOS database stopped being encrypted. No crash, no error thrown, just a silently plaintext database, just like being left on seen by your crush.

```plaintext
SQLCipherTester.runChecks():
sqlite_version: 3.51.0
cipher_version: null
has SQLITE_HAS_CODEC: false
```

`cipher_version: null` - SQLCipher wasn’t active. The app was using Apple’s system sqlite3 instead of the SQLCipher.framework

A quick `xxd` of the database file confirmed it:

```plaintext
00000000: 5351 4c69 7465 2066 6f72 6d61 7420 3300  SQLite format 3.
```

Plaintext… the `SQLite format 3\0` header was right there, mocking me like a mocking framework.

## Root Cause: Firebase’s Transitive `-lsqlite3`

After way too much debugging, here’s what i found: Firebase’s iOS SDK (specifically `FirebaseAnalyticsWrapper` and a few other internal targets) declares `.linkedLibrary("sqlite3")` in its Package.swift. This tells the linker to link against Apple’s system `/usr/lib/libsqlite3.dylib`.

Both system sqlite3 and SQLCipher export identical symbol names `sqlite3_open_v2`, `sqlite3_prepare_v2`, `sqlite3_key`, etc. When the linker resolves these symbols in a static framework (which KMP produces with `isStatic = true`), the system `-lsqlite3` dylib takes precedence. i even tried reordering the SPM dependencies to put SQLCipher before Firebase, sadly didn’t help. The linker still resolved to system sqlite3.

The result: every `sqlite3_*` call in your app goes to Apple’s unencrypted sqlite3, even though SQLCipher.framework is linked and present. `sqlite3_key` either doesn’t exist (returns SQLITE\_MISUSE) or is silently ignored.

## What Didn’t Work

i went through several approaches before landing on the solution, well here’s what didn’t work:

**1. Using `@import SQLCipher` in an Objective-C bridge**

Created a local SPM package with C wrapper functions that called through to SQLCipher:

```objc
@import SQLCipher;

int sc_open_v2(const char *filename, sqlite3 **ppDb, int flags, const char *zVfs) {
    return sqlite3_open_v2(filename, ppDb, flags, zVfs);
}
```

This compiled fine but didn’t work, why? `@import SQLCipher` only affects **header lookup** at compile time but it tells the compiler which module map to use for type definitions. It does NOT affect symbol binding in static archives. The compiled `.o` file still contains unresolved references to `sqlite3_open_v2` which the linker resolves to whichever dylib provides the first system sqlite3.

`bridge_libversion` returned `3.51.0` (system sqlite3) instead of SQLCipher’s version. Two-level namespace binding is a runtime feature of Mach-O dylib references, not a compile-time feature of static object files.

**2. PRAGMA key instead of sqlite3\_key C API**

Tried `PRAGMA key = 'x''<hex-encoded-key>''';` thinking maybe the PRAGMA path would work differently. Same result the PRAGMA handler calls `sqlite3_key` internally, which still resolved to system sqlite3.

**3. Adding `-DSQLITE_HAS_CODEC=1` and `-SQLITE_HAS_CODEC=1` to cinterop**

This made `sqlite3_key` compile but didn’t change which library provided the implementation at link time.

## The Solution: dlopen/dlsym Bridge

[![here i go](https://funkymuse.dev/assets/img/encrypt_kmp_db/spaghetti_meme.jpg)](https://funkymuse.dev/assets/img/encrypt_kmp_db/spaghetti_meme.jpg)

The definitive fix is to bypass the link-time symbol resolution entirely using `dlopen` and `dlsym` at runtime (after too much searching I found that this is actually the best solution, I might be wrong tho)

The idea: instead of calling `sqlite3_*` functions (which the linker resolves to system sqlite3), explicitly load `SQLCipher.framework` by path and resolve every function pointer from THAT specific dylib. This is 100% immune (I wrote this while listening to Fear Inoculum by Tool so at the moment I heard the word “inoculuated”) to link-order conflicts.

### The Bridge Package

For this we are using the SPM import feature but with local packages, i created a local one (`sqlcipher-bridge/`) with three files:

**Package.swift** - depends on SQLCipher.swift to ensure the framework is resolved and embedded:

```swift
import PackageDescription

let package = Package(
    name: "SQLCipherBridge",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "SQLCipherBridge", targets: ["SQLCipherBridge"])
    ],
    dependencies: [
        .package(url: "https://github.com/sqlcipher/SQLCipher.swift.git", exact: "4.15.0")
    ],
    targets: [
        .target(
            name: "SQLCipherBridge",
            dependencies: [.product(name: "SQLCipher", package: "SQLCipher.swift")],
            publicHeadersPath: "include"
        )
    ]
)
```

**SQLCipherBridge.h** - declares `sc_*` wrapper functions with opaque types:

```c
#ifndef SQLCipherBridge_h
#define SQLCipherBridge_h

#include <stdint.h>

typedef struct sqlite3 sc_sqlite3;
typedef struct sqlite3_stmt sc_sqlite3_stmt;

int sc_initialize(void);
int sc_open_v2(const char *filename, sc_sqlite3 **ppDb, int flags, const char *zVfs);
int sc_close_v2(sc_sqlite3 *db);
int sc_key(sc_sqlite3 *db, const void *pKey, int nKey);
int sc_extended_result_codes(sc_sqlite3 *db, int onoff);
const char *sc_libversion(void);

// ... prepare, step, bind, column functions follow the same pattern

int sc_SQLITE_OK(void);
int sc_SQLITE_ROW(void);
int sc_SQLITE_DONE(void);
int sc_SQLITE_OPEN_READWRITE(void);
int sc_SQLITE_OPEN_CREATE(void);

#endif
```

**SQLCipherBridge.m** - the core: dlopen + dlsym for every sqlite3 function:

```objc
@import Foundation;
#include "SQLCipherBridge.h"
#include <dlfcn.h>

// Function pointer typedefs
typedef int    (*fn_open_v2)(const char *, void **, int, const char *);
typedef int    (*fn_close_v2)(void *);
typedef int    (*fn_key)(void *, const void *, int);
typedef const char * (*fn_libversion)(void);
// ... one typedef per sqlite3 function

// Function pointer storage
static struct {
    fn_open_v2    open_v2;
    fn_close_v2   close_v2;
    fn_key        key;
    fn_libversion libversion;
    // ... all other function pointers
} sc;

static int sc_ready = 0;

int sc_initialize(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        void *handle = dlopen("@rpath/SQLCipher.framework/SQLCipher", RTLD_NOW);
        if (!handle) {
            NSString *fw = [[NSBundle mainBundle].privateFrameworksPath
                            stringByAppendingPathComponent:@"SQLCipher.framework/SQLCipher"];
            handle = dlopen(fw.UTF8String, RTLD_NOW);
        }
        if (!handle) {
            NSLog(@"SQLCipherBridge: FAILED to load SQLCipher.framework: %s", dlerror());
            return;
        }

        #define LOAD(field, sym) sc.field = (fn_##field)dlsym(handle, "sqlite3_" #sym)
        LOAD(open_v2, open_v2);
        LOAD(close_v2, close_v2);
        LOAD(key, key);
        LOAD(libversion, libversion);
        // ... load all function pointers
        // Important note here: 'finalize' clashes with ObjC's finalize method, so the struct
        // field is named 'finalize_fn' and loaded with direct assignment:
        // sc.finalize_fn = (fn_finalize)dlsym(handle, "sqlite3_finalize");
        #undef LOAD

        sc_ready = 1;
        NSLog(@"SQLCipherBridge: Loaded SQLCipher.framework - sqlite3_libversion = %s",
              sc.libversion ? sc.libversion() : "unknown");
    });
    return sc_ready;
}

#define ENSURE_INIT() do { if (!sc_ready) sc_initialize(); } while(0)

int sc_open_v2(const char *filename, sc_sqlite3 **ppDb, int flags, const char *zVfs) {
    ENSURE_INIT();
    return sc.open_v2(filename, (void **)ppDb, flags, zVfs);
}

int sc_key(sc_sqlite3 *db, const void *pKey, int nKey) {
    ENSURE_INIT();
    if (!sc.key) return 21; // SQLITE_MISUSE
    return sc.key(db, pKey, nKey);
}

// ... every other function follows the same pattern
```

### The Kotlin Driver

With the bridge in place, the custom `SQLiteDriver` calls `sc_*` functions via cinterop. The open-key-then-use pattern is adapted from [Paris Tsiogas’ guide](https://desquared.notion.site/Encrypted-Room-Database-in-Kotlin-Multiplatform-KMP-for-Android-iOS-25b9aac7b816801b9067c627602335c6), but rewritten to use the `sc_*` bridge functions instead of calling `sqlite3_*` directly:

```kotlin
// imports from: swiftPMImport.YOURPACKAGE HERE.shared.sc_*

internal class SQLCipherDriver(
    private val rawKey: ByteArray,
) : SQLiteDriver {

    override fun open(fileName: String): SQLiteConnection = memScoped {
        val dbPointer = allocPointerTo<sc_sqlite3>()
        var resultCode = sc_open_v2(
            filename = fileName,
            ppDb = dbPointer.ptr,
            flags = sc_SQLITE_OPEN_READWRITE() or sc_SQLITE_OPEN_CREATE(),
            zVfs = null,
        )
        if (resultCode != sc_SQLITE_OK()) {
            throwSQLiteException(resultCode, null)
        }

        resultCode = sc_extended_result_codes(dbPointer.value!!, 1)
        if (resultCode != sc_SQLITE_OK()) {
            throwSQLiteException(resultCode, null)
        }

        // must be the first operation after open, per Zetetic documentation
        rawKey.usePinned { pinned ->
            resultCode = sc_key(
                db = dbPointer.value!!,
                pKey = pinned.addressOf(0),
                nKey = rawKey.size,
            )
        }
        if (resultCode != sc_SQLITE_OK()) {
            throwSQLiteException(resultCode, null)
        }

        val connection = SQLCipherConnection(dbPointer.value!!)
        connection.execSql("PRAGMA journal_mode = WAL;")

        connection
    }
}
```

The `SQLCipherConnection` and `SQLCipherStatement` implementations are modeled after AndroidX’s internal [`NativeSQLiteConnection`](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/) and `NativeSQLiteStatement` - same `SQLiteConnection` and `SQLiteStatement` interfaces, same contract, just calling `sc_*` bridge functions instead of `sqlite3_*` directly.

### Key Management

The encryption key is a 32-byte random passphrase, generated and stored per platform using `expect`/`actual`:

```kotlin
// commonMain
expect class DatabaseKeyManager {
    fun getOrCreatePassphrase(): ByteArray
}
```

On **Android**, the passphrase is generated with `SecureRandom`, encrypted with an AES-256-GCM key from Android Keystore, and stored in DataStore:

```kotlin
// androidMain
private fun generateAndStorePassphrase(): ByteArray {
    val passphrase = ByteArray(32)
    java.security.SecureRandom().nextBytes(passphrase)

    val keystoreKey = getOrCreateKeystoreKey()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)

    val encryptedKey = cipher.doFinal(passphrase)
    val iv = cipher.iv
    // store encryptedKey and iv in DataStore
    return passphrase
}
```

On **iOS**, it’s simpler - generate with `SecRandomCopyBytes` and store in Keychain:

```kotlin
// iosMain
private fun generateRandomPassphrase(): ByteArray {
    val bytes = ByteArray(PASSPHRASE_SIZE)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(null, PASSPHRASE_SIZE.toULong(), pinned.addressOf(0))
    }
    return bytes
}
```

Both platforms produce a raw `ByteArray` that gets passed to `SQLCipherDriver(rawKey = passphrase)` just feeding raw bytes straight into `sqlite3_key`.

### Verifying Encryption

i wrote a `SQLCipherTester` that runs in debug builds only, right after Room creates the database:

```kotlin
if (BuildKonfig.isDebug) {
    SQLCipherTester.verifyEncryption(dbPath = dbFile, passphrase = passphrase)
}
```

The tester opens its own connection through the same bridge, then runs three checks per [Zetetic’s testing guide](https://www.zetetic.net/sqlcipher/sqlcipher-api/#testing-encryption):

1. **`PRAGMA cipher_version`** - returns the SQLCipher version if encryption is active, null if system sqlite3
2. **`SELECT count(*) FROM sqlite_master`** - succeeds only if the key is correct
3. **File header check** - reads the first 16 bytes and verifies they don’t match `"SQLite format 3\0"` (plaintext header)

There’s also a `deleteIfPlaintext()` function that runs before Room opens the database, if an existing database file has a plaintext header (created before SQLCipher was enabled), it copies the data safely to a new backup file, it deletes the original file and lets Room recreate it encrypted, seeds the data from the backup file, deletes the backup file, of course we have few checks to make sure logic here makes sense, SQLCipher can’t retroactively encrypt an existing plaintext database.

### Gradle Integration

In `shared/build.gradle.kts`, the bridge is declared as a local SPM package inside the `swiftPMDependencies` block alongside Firebase and GoogleSignIn:

```kotlin
swiftPMDependencies {
    iosMinimumDeploymentTarget.set("16.0")

    localSwiftPackage(
        directory = project.layout.projectDirectory.dir("sqlcipher-bridge"),
        products = listOf("SQLCipherBridge"),
    )

    swiftPackage(
        url = url("https://github.com/firebase/firebase-ios-sdk.git"),
        version = exact("12.13.0"), // libs.versions.spm.firebase.ios
        products = listOf(
            product("FirebaseAnalytics"),
            product("FirebaseCrashlytics"),
            product("FirebaseRemoteConfig"),
            product("FirebaseMessaging"),
        ),
    )
    swiftPackage(
        url = url("https://github.com/google/GoogleSignIn-iOS.git"),
        version = exact("9.1.0"), // libs.versions.spm.google.sign.in.ios
        products = listOf(product("GoogleSignIn")),
    )
}
```

## Verification

After building and running, the console shows:

```plaintext
SQLCipherBridge: Loaded SQLCipher.framework - sqlite3_libversion = 3.51.3
```

And the tester output:

```plaintext
SQLCipherTester.verifyEncryption():
--- SQLCipher file verification for: .../Documents/rudio.db ---
bridge_libversion: 3.51.3
cipher_version: 4.15.0 community
schema_accessible: true
header_encrypted: true
--- SQLCipher verification complete ---
```

`cipher_version: 4.15.0 community` - SQLCipher is active. `header_encrypted: true` - file on disk is encrypted.

The hex dump confirms it:

```plaintext
00000000: 3eb4 bd32 d259 d59e e536 6abd 05fc e975  >..2.Y...6j....u
00000010: a88e 1167 c6b4 5114 c362 6da3 9440 e34f  ...g..Q..bm..@.O
```

Random bytes. No `SQLite format 3\0` header. `file` command reports `data` instead of `SQLite 3.x database`.

## Why Not Just Fix Linker Flags?

One common suggestion is to adjust `OTHER_LDFLAGS` or use `-force_load` in Xcode to ensure SQLCipher’s symbols take priority over system sqlite3. This can work in simpler setups, but in a KMP project with SPM import it’s fragile (but that’s my opinion, take it with a grain of salt, here’s why i think that, as a NON iOS developer):

- Link order in SPM depends on the dependency resolution graph, which can change when Firebase or any transitive dependency updates
- Every developer and CI machine needs the same Xcode project configuration, easy to miss during setup or get overwritten
- If Firebase changes its internal structure (adds or removes wrapper targets), the link order could silently shift and break encryption without any visible error

The dlopen approach is immune to all of this. It explicitly loads SQLCipher.framework by path at runtime, period. No link-order games, no fragile build system configuration.

## Key Takeaways

1. **`@import` doesn’t fix symbol conflicts in static archives** it only affects header lookup, not linker symbol resolution. Two-level namespace binding is a runtime Mach-O feature for dylibs, not compile-time for static objects.
2. **Firebase’s `-lsqlite3` is transitive and invisible** you won’t see it in your Podfile or SPM dependencies. It’s buried inside Firebase’s Package.swift in wrapper targets.
3. **`dlopen`/`dlsym` is the nuclear option that always works** when link-time symbol resolution is broken by conflicts you can’t control, runtime loading bypasses it entirely.
4. **Always verify encryption on disk** don’t trust that `PRAGMA key` succeeded just because no error was thrown. Check the file header with `xxd` and query `PRAGMA cipher_version` to confirm SQLCipher is actually active.
5. **The Desquared guide is solid** if you don’t have Firebase or other libraries that transitively link system sqlite3, the original `NativeSQLiteDriver` + `PRAGMA key` approach works perfectly. The bridge is only needed when you have symbol conflicts.

## References

- [Encrypted Room Database in KMP](https://desquared.notion.site/Encrypted-Room-Database-in-Kotlin-Multiplatform-KMP-for-Android-iOS-25b9aac7b816801b9067c627602335c6) by Paris Tsiogas my the starting point
- [SQLCipher API Documentation](https://www.zetetic.net/sqlcipher/sqlcipher-api/) - Zetetic’s official docs on sqlite3\_key, PRAGMA key, and testing
- [SQLCipher.swift](https://github.com/sqlcipher/SQLCipher.swift) - the SPM-compatible xcframework
- [AndroidX SQLite source](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/) - reference for NativeSQLiteConnection/Statement patterns
- [dlopen man page](https://developer.apple.com/library/archive/documentation/System/Conceptual/ManPages_iPhoneOS/man3/dlopen.3.html) - Apple’s documentation on runtime dynamic loading

This approach is used in my new passion project [Rudio](https://rudio.app/) - find free WiFi notspots around you.

It’s becoming hot again, stay hydrated, until next article…

This post is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) by the author.
