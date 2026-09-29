![article banner (priority)](https://marcinmoskala.com/kt-academy-articles/promotion/run_blocking.png)

Traditionally, Java and Kotlin projects were based on blocking calls. By blocking calls I mean functions that block caller thread when they wait for something, for example, when they wait for a network response. One of the most important rules of Kotlin Coroutines is that we should not make blocking calls on suspending functions (unless we use a dispatcher that allows blocking calls, like `Dispatchers.IO`).

```text-base
// Incorrect: blocking call in a suspending function
suspend fun getUser(): User {
    val response = api.getUser() // blocking call
    return response.toDomainUser()
}

// Correct: Using withContext(Dispatchers.IO) to make a blocking call in a suspending function
suspend fun getUser(): User = withContext(Dispatchers.IO) {
    val response = api.getUser() // blocking call
    response.toDomainUser()
}
```

But how to go the opposite way? How to turn suspending calls into blocking calls? For that we use `runBlocking`!

## How `runBlocking` works

`runBlocking` starts a coroutine on the thread that called it, and blocks this thread until the coroutine completes. So run blocking is essentially synchronous, because if we called it multiple times, the second call will not start until the first one finishes. As a synchronous coroutine builder, `runBlocking` returns the result of the coroutine it started.

```text-base
fun main() {
    log("Starting main")
    runBlocking {
        log("Starting first runBlocking")
        delay(1000)
        log("Finishing first runBlocking")
    }
    val result: String = runBlocking {
        log("Starting second runBlocking")
        delay(1000)
        "ABCD"
    }
    log("Second runBlocking finished with result: $result")
}

fun log(message: String) {
    println("[${Thread.currentThread().name}] $message")
}
// [main] Starting main
// [main] Starting first runBlocking
// (1 sec)
// [main] Finishing first runBlocking
// [main] Starting second runBlocking
// (1 sec)
// [main] Second runBlocking finished with result: ABCD
```

Since `runBlocking` starts a scope, it awaits completion of all coroutines started in it. This means that it awaits completion of all child coroutines. That is why the below program will not finish until all three async coroutines finish. To show how `runBlocking` can be used to define a result, I also made this program return `0` from `main` function.

```text-base
import kotlinx.coroutines.*

fun main(): Int = runBlocking {
    launch { delayAndPrintHello() }
    launch { delayAndPrintHello() }
    launch { delayAndPrintHello() }
    println("Hello")
    0 // result from main
}

suspend fun delayAndPrintHello() {
    delay(1000L)
    println("World!")
}
// Hello
// (1 sec)
// World!
// World!
// World!
```

`runBlocking` behavior might remind you `coroutineScope`, which is no coincidence, as they both start synchronous coroutines, but `runBlocking` is blocking, and `coroutineScope` is suspending. That means completely different usages, we only use `coroutineScope` in suspending functions, while we should never use `runBlocking` in suspending functions. That also means `coroutineScope` builds relationship to its caller, and is always in the middle of a hierarchy of coroutines, while `runBlocking` starts a new hierarchy of coroutines.

## The practice of using `runBlocking`

In properly implemented coroutine-based projects, that use properly designed coroutine-friendly libraries, we should nearly never need to use `runBlocking`. If we use it often in a project, that is considered a code smell. However, there are legit cases where `runBlocking` is useful, and even necessary. There are also cases where `runBlocking` should not be used. We will also cover those cases where `runBlocking` used to be necessary, but now it has better alternatives. Let's see them now.

## Where to use `runBlocking`

`runBlocking` should be used where we need to start a coroutine and block the current thread until it finishes. It means it can be used when:

- We need to await the result of a coroutine.
- We can block the current thread.

A popular Android example is setting an interceptor in Retrofit client to attach token to network call. Getting a token might require making a network call, so we need to start a coroutine to get it. At the same time interceptor needs result to proceed. This interceptor is started on a pool from Retrofit, so its calls can be called. That makes it a perfect place for using `runBlocking`.

```text-base
class AddTokenInterceptor: Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { getToken() }
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

On backend systems there are sometimes cases where we need to block the current thread to await coroutine completion, for instance for our tools to correctly measure this process execution time, or when we need to call some blocking script and get its result.

```text-base
@MeasureExecutionTime
fun runDataMigrationScript() = runBlocking {
    val sourceData = readDataFromSource()
    val transformedData = transformData(sourceData)
    writeDataToTarget(transformedData)
}
```

Those are rare cases, and most backend projects should not need to use `runBlocking`. That is unless they have some legacy code that is based on blocking calls. Consider the following `UserService` that is used all around our application to get manage users. We already migrated it to suspending calls, but we still have some legacy controllers and services that are based on blocking calls. To avoid necessity to rewrite all of them, we can provide blocking alternatives for our suspending functions. Such alternatives can be implemented by just wrapping suspending functions with `runBlocking` (you might also consider using some dispatcher).

```text-base
class UserService(
    private val userRepository: UserRepository,
) {
    suspend fun findUserById(id: String): User = userRepository.findUserById(id)

    // Blocking alternative for legacy parts of our application
    fun findUserByIdBlocking(id: String): User = runBlocking {
        findUserById(id)
    }

    // ...
}
```

That is probably the most important use of `runBlocking`, as a bridge from blocking to suspending worlds. Some libraries use define blocking alternatives for Java uses.

```text-base
suspend fun readDataFromSource(): Data {
    // ...
}

fun readDataFromSourceBlocking(): Data = runBlocking {
    readDataFromSource()
}
```

## Where not to use `runBlocking`

There are some cases where `runBlocking` should not be used. Beyond all, `runBlocking` should never be used directly in suspending functions. `runBlocking` blocks the current thread, and you shouldn't make blocking calls in suspending functions (unless you use a dispatcher that allows blocking calls, like `Dispatchers.IO`). In such cases `runBlocking` is most likely not needed anyway.

```text-base
// Incorrect: runBlocking in a suspending function
suspend fun getToken() = runBlocking {
    // ...
}

// runBlocking is most likely not needed
suspend fun getToken() {
    // ...
}
```

`runBlocking` shouldn't be used in functions that do not need to await its result. If you just need to start a coroutine, it is generally better to start an asynchronous coroutine using `launch`.

```text-base
// Incorrect: runBlocking used where we do not need to await result
fun startBackgroundProcess() = runBlocking {
    doSomething()
}

// Correct: Using launch to start an asynchronous coroutine
fun startBackgroundProcess() {
    backgroundScope.launch {
        doSomething()
    }
}
```

We should also be careful to not use `runBlocking` on threads that shouldn't be blocked. That is especially a problem on Android, where blocking the main thread will cause the application to freeze.

```text-base
// Incorrect: runBlocking on the main thread
fun onClick() = runBlocking {
    userNameView.test = getUserName()
}

// Correct: Using launch to start an asynchronous coroutine
fun onClick() {
    lifecycleScope.launch {
        userNameView.test = getUserName()
    }
}
```

On backend, it might cause trouble if we use it inside `synchronized` blocks. One trick is to use `launch` to implement a callback function. However, it is generally better to redesign the code to use suspension instead of blocking calls, and to use coroutines-friendly tools (what we will discuss in the *Synchronizing coroutines* lesson).

```text-base
// Possibly incorrect: runBlocking inside synchronized block
synchronized(lock) {
    // ...
    val user = runBlocking { getUser() }
    // ...
}

// One solution: use launch to implement a callback
fun getUser(callback: (User) -> Unit) {
    backgroundScope.launch {
        val user = getUser() // suspending call
        callback(user)
    }
}
synchronized(lock) {
    // ...
    getUser { user ->
        // ...
    }
}
```

## Outdated `runBlocking` uses

`runBlocking` was traditionally used to wrap `main` function body. Its properties are perfect for that: it starts a coroutine, so it can call suspending functions or start other coroutines, and it blocks the thread until the coroutine finishes, so we can be sure that the program will not finish before all those processes complete.

```text-base
fun main(): Unit = runBlocking {
    val user = getUser() // suspending call
    println("User: $user")
}
```

`runBlocking` can still be used this way, however in most modern cases we prefer to use suspending `main` function that was introduced Kotlin 1.3. Such functions under the hood is wrapped with a blocking builder similar to `runBlocking`.

```text-base
suspend fun main() {
    val user = getUser() // suspending call
    println("User: $user")
}
```

The key difference is that `runBlocking` sets a dispatcher, making all its children coroutine operate on the same thread it is using. Suspending main does not set a dispatcher, so its children coroutines by default operate on different threads. This change was introduced, as single-threaded dispatcher used by `runBlocking` often caused unexpected behavior.

```text-base
fun main(): Unit = runBlocking {
    println(Thread.currentThread().name)
    launch {
        println(Thread.currentThread().name)
    }
}
// main
// main
```

```text-base
suspend fun main(): Unit = coroutineScope {
    println(Thread.currentThread().name)
    launch {
        println(Thread.currentThread().name)
    }
}
// main
// DefaultDispatcher-worker-1
```

The second traditional use of `runBlocking` was in tests. It was used to wrap test bodies so that we could call suspending functions and start coroutines in them. Nowadays, we rather use `runTest` from the `kotlinx-coroutines-test` library, which is a more powerful and flexible alternative to `runBlocking`. It allows us to control time, generate background scope, and track exceptions on child coroutines. `runTest` will be discussed in the *Testing coroutines* lesson.

```text-base
class UserRepositoryTest {
    val userRepository = InMemoryUserRepository()
    val userService = UserService(userRepository)

    @Test
    fun testGetUser() = runTest { // previously runBlocking
        // given
        userRepository.hasUser(UserEntity("1234", "John Doe"))

        // when
        val user = userService.getUser("1234")

        // then
        assertEquals("John Doe", user.name)
    }
}
```

## Summary

- `runBlocking` is a blocking coroutine builder that starts a coroutine and blocks the current thread until it finishes.
- `runBlocking` is a bridge from blocking to suspending worlds, and it is used to start coroutines in places where we need to block the current thread until the coroutine finishes.
- If you need to use `runBlocking` often in your project, it is a code smell. In properly designed coroutine-based projects, it should be used rarely or not at all.

### The author:

[![Marcin Moskała](https://lh3.googleusercontent.com/a-/AOh14GgT-nGHTlbxHpiPyUUhgruiheIEyBVEsCDWNdW0xA=s96-c)](https://kt.academy/user/marcinmoskala)

[Marcin Moskała](https://kt.academy/user/marcinmoskala)

Marcin Moskala is a highly experienced developer and Kotlin instructor as the founder of Kt. Academy, an official JetBrains partner specializing in Kotlin training, Google Developers Expert, known for his significant contributions to the Kotlin community. Moskala is the author of several widely recognized books, including "Effective Kotlin," "Kotlin Coroutines," "Functional Kotlin," "Advanced Kotlin," "Kotlin Essentials," and "Android Development with Kotlin."

Beyond his literary achievements, Moskala is the author of the largest Medium publication dedicated to Kotlin. As a respected speaker, he has been invited to share his insights at numerous programming conferences, including events such as Droidcon and the prestigious Kotlin Conf, the premier conference dedicated to the Kotlin programming language.

## Comments
