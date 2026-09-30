A rewrite this size wasn’t affordable before agents. Here’s what porting the Copilot agent runtime to 800,000 lines of production Rust actually took.

![Copilot appears against a decorative background with scattered green squares.](https://github.blog/wp-content/uploads/2026/01/generic-github-copilot-logo-stripe.png?w=1600)

The [GitHub Copilot CLI](https://github.com/features/copilot/cli/), [GitHub Copilot app](https://github.com/features/ai/github-app), and [GitHub Copilot SDK](https://github.com/github/copilot-sdk) are all backed by the Copilot agent runtime, an agentic harness that can be embedded into applications and services. It was originally written in TypeScript on Node.js and the V8 JavaScript engine for what is now the [GitHub Copilot cloud agent](https://docs.github.com/copilot/how-tos/use-copilot-agents/cloud-agent) (CCA), and the runtime stayed on that stack as the runtime and its capabilities grew rapidly.

That has now changed. Using the GitHub Copilot app and the Copilot CLI, we completely rewrote the runtime into more than 800,000 lines of production Rust. AI agents wrote most of the code, spanning 128 pull requests that landed in main and shipped incrementally rather than waiting for a single cutover at the end. The few inevitable regressions were discovered and fixed quickly along the way, while the performance of the runtime improved by orders of magnitude. A project that would have taken a whole team of developers a year or two before agents was now completed primarily by a single developer, in only a few months, all while the rest of the team continued to greatly expand the runtime’s capabilities and reach.

## Why we needed to port

The Copilot agent runtime isn’t just the engine behind the Copilot CLI. It backs a growing set of Microsoft, GitHub, and ecosystem solutions, for each of which AI support is, architecturally, a shell around the same runtime plus whatever customizations that solution needs. This includes not only the GitHub Copilot CLI and the GitHub Copilot app, but also the latest releases of VS Code, Visual Studio, CCA, Copilot Code Review ([CCR](https://docs.github.com/copilot/concepts/agents/code-review)), Copilot Cowork, Copilot Studio, and Excel and Outlook and PowerPoint and Word and… it goes on.

These are very different products, and none of them wants to or should need to implement everything that goes into a production agent harness. They want all of the intelligence, security, reliability, and performance, and they want it shared so that a fix in one place fixes it in all of them. Most of the products listed in the previous paragraph initially implemented their own agent loop, but have since replaced it with the GitHub Copilot SDK, which is the entry point to the Copilot agent runtime. Doing so enables them to focus on their core business value and leave the details to the runtime. That’s all the more important given the pace of the industry and the employed agent loop needing to stay always best-of-breed in the face of intense competition.

So, shared runtime, good. The problem was the nature of the thing being shared.

If we look at the CLI, it’s logically a terminal UI (TUI) on top of an agent loop. As it happened, the whole stack was implemented in TypeScript, using Node.js as the framework and V8 for the execution engine, with Ink and React for UI. That’s a respectable choice for a TUI application; TypeScript and Node.js are broadly accessible and enable very rapid application development. And for the needs of a console application, the performance implications in terms of startup, responsiveness, throughput, and memory consumption are also reasonable. They are, unfortunately, much less reasonable when you think about that implementation being used in other environments, with other constraints, with demands for things like fast startup and excellent server density due to low memory overhead.

The architecture of the CLI and its runtime also contributed to challenges here. The whole industry is running extremely fast, and in that context, really bright people make decisions for delivery speed and market reach. The Copilot CLI was initially written and shipped quickly, and in doing so, the TUI and the runtime were fairly intertwined rather than separated into discrete layers. Then when an SDK was needed for programmatic access to that runtime, without clear separation of the layers, a pragmatic decision was made to layer the SDK on top of the CLI, even though logically you’d expect the inverse architecture. Rather than only being accessible via commands provided by the user at the command line, the CLI was updated with a mode where it could be run headless, reading similar commands from stdin and writing responses to stdout. A JSON-RPC protocol could then be used to marshal function calls from an external process to and from the CLI. The SDK could then be embedded in arbitrary consuming programs, which would spawn a CLI process to host the agent loop out-of-process, with the SDK calling functions in the remote process via this JSON-RPC mechanism. Neat. Fast to get out the door. Flexible. But not great for the performance (startup, memory, throughput) and reliability of those consuming applications. Creating a new `CopilotClient` from the SDK meant spawning another process:

```wp-block-code
const client = new CopilotClient();
await client.start(); // spawns the CLI as a subprocess
const session = await client.createSession({
    /* ... */
});
```

The process would need to launch and host Node and V8. It meant parsing the significant amount of JavaScript produced from the TypeScript code in the CLI, generating bytecode for it, and potentially optimizing hot code in later JIT tiers. It meant all the memory overhead associated with V8. It meant inheriting Node’s threading model, which by default pushes us towards a model of all CPU-bound work being serialized. And it meant forced out-of-process communication just to make function calls. It meant every SDK consumer, in every language, ships Node.js or a bundled binary containing V8. It meant the C#, Python, Go, Java, and Rust SDKs all paid for a whole second language runtime per client, on the order of 100 MB of working set minimum, for a runtime their application otherwise had no use for. It meant every event, every message, and every abstracted session file system read and write was pushed across a process boundary. It meant a crash in Node took the session with it. And it meant anyone deploying this had, at a minimum, two processes to supervise, monitor, and debug.

Instead, we wanted a runtime:

- that does not include the TUI, that’s its own library the TUI and other applications and services can be properly layered on top of cleanly.
- implemented in a language with minimal dependencies and minimal overhead.
- implemented in a way that it can be cleanly embedded in-process rather than being forced out-of-process.
- implemented in a language with top characteristics around performance and scalability and reliability.
- implemented in a language that’s great for interop, such that it can be used cleanly by all six Copilot SDK language versions (C#, TypeScript, Python, Rust, Go, Java) with that stack’s foreign function interface (FFI) mechanism.
- implemented with a tool chain that provides a more modern security posture, with less supply chain risk and greater support for correct-by-construction code.

For all those reasons, as well as softer reasons (such as team experience and industry direction), we chose Rust. This is in no way a claim that every large TypeScript program should become Rust. Our requirements emphasized embedding through a C ABI, low startup and steady-state overhead, and predictable resource use. Rust made those goals possible, at the expense of other complications, e.g. we had to represent lifetimes and shared state explicitly (the lifecycle regressions discussed later highlight the implications of that). The right target language legitimately varies from application to application.

There were then two key related tasks undertaken:

1. Separating the TUI-specific code from the runtime, so that the former is layered strictly on top of the latter, and more specifically layered strictly on top of the SDK’s public surface area. Today, the CLI still calls directly into runtime internals in several places; moving it fully onto the SDK’s surface area is ongoing work.
2. Porting that runtime layer to 100% Rust, resulting in a pure native binary exposing a C ABI for in-process consumption by all the language front-ends and a stdin/stdout-based or socket-based server for when out-of-process is still desired.

This post primarily covers the second: porting the runtime to Rust.

![The Copilot runtime architecture before and after the Rust rewrite, showing the old SDK-to-CLI process boundary and the new in-process and out-of-process hosting paths.](https://github.blog/wp-content/uploads/2026/09/architecture-before-after.svg?resize=1024,512)

## What it looked like before

The initial porting plan in early May 2026 estimated the runtime at roughly 130,000 lines of TypeScript. For scoping purposes, this initial measurement was reasonably accurate, but, as it turned out, also wildly misleading, in two key ways. Concurrent with porting:

1. Pieces still wrapped up in the TUI layer were being pushed down to the runtime layer. Entire components and significant percentages of code initially ignored in the estimates were then later considered relevant to porting.
2. Pull requests contributing significant amounts of new TypeScript were constantly raising the amount of TypeScript in the repo. Tens of agentically assisted developers merging hundreds of pull requests per week.

Everything factored in, I estimate approximately 430,000 lines of production TypeScript ended up passing through the port. Those same factors also made it hard to see progress along the way: until close to the end, production TypeScript volume appeared to be holding relatively steady, if not increasing slightly, as porting kept pace with incoming work.

This is confused further because there was also incoming Rust code, separate from the port, over the timeframe; early in the porting effort, incoming code was more likely to be dominated by TypeScript, whereas later in the effort, it was more likely to be dominated by Rust.

![TypeScript fell to zero while production Rust rose to approximately 830,000 lines and Rust unit tests to approximately 469,000 lines.](https://github.blog/wp-content/uploads/2026/09/runtime-line-history.svg?resize=1024,563)

During the port, the runtime took in ~300,000 production lines of TypeScript and shed ~430,000, while ~1,200,000 production Rust lines entered and ~365,000 left. In other words, the apparent stability of the TypeScript line in the above graph was actually hiding significant amounts of TypeScript churn.

## In-place porting strategy

That chart also highlights an important aspect of how the port was done: in place.

There are two main approaches to a rewrite of this scale:

1. **Big bang.** The new Rust runtime is developed as a complete alternative and then swapped in all at once when it’s ready. Such a big-bang cutover has two variations. a. **Stop the world.** Everyone ceases other work on the `main` branch while the rewrite happens, with the rewrite being done in `main`. b. **Parallel development.** The rewrite happens in a feature branch while work continues in the main branch, with the rewrite constantly trying to keep up with and merging in changes from the main branch.
2. **In place.** This is done as a component-by-component port, where the runtime is incrementally rewritten one piece at a time. Such an in-place approach also has two variations. a. **Atomic replacement.** Each piece is flipped atomically from TypeScript to Rust, with interop between the remaining TypeScript and the new Rust providing continuity. Over time, less and less of the production runtime is TypeScript, and more and more is Rust, until one day, there’s no more TypeScript, only Rust. b. **A/B.** Rather than deleting components as they’re ported, both the TypeScript and the Rust components are maintained as hot-swappable options, with the TypeScript being deleted once confidence has plateaued.

We went with option 2a, for a variety of reasons:

- **No one experiences work stoppage.** The main branch continues to be active. Every developer not directly involved in the port gets to keep on keepin’ on, impacted only when a pull request they may have in flight for a prolonged period of time happens to touch code that gets ported concurrently, in which case they need to rebase and have their agents help port just their in-flight changes.
- **The runtime’s main branch is always shippable.** Each pull request replaces the existing TypeScript implementation with a thin shim that calls into Rust, and deletes the old code in one atomic change. The new code is immediately exercised, in-situ.
- **The rewrite is incremental and reviewable.** Each pull request ports a single component or slice, so the scope of change is smaller and the diff is easier to review, whether by a human or by agents or both.
- **Most ports are reasonably small and self-contained**, minimizing drift from concurrent pull requests. In some cases, where TypeScript components were too large, they could first be refactored into more easily ported components.
- **All existing end-to-end tests**, across the CLI and SDK, run against the new Rust code at every step, giving us confidence and lots of validation. If a pull request caused a required test to fail, it didn’t land.

We also shied away from the option 2b variant that involved maintaining multiple versions of the same component concurrently. With hundreds of pull requests being merged into the repo per week for the last several months, the codebase is constantly evolving, and quickly. Having two different versions of the same code in two different languages and using two different sets of dependency libraries adds a ton of complexity. Some of these components aren’t perfectly isolated, either; while some are logically standalone with simple APIs for accessing them by the rest of the system, others have significant tendrils, and making that graph hot swappable per component is a nightmare. The subsystems that would possibly benefit the most from a cautious parallel cutover are exactly the ones where parallel is hardest. For example, session orchestration isn’t a pure function you can call two different versions of with an `if`/`else` on some experimentation flag. It owns mutable state, drives callbacks in both directions, and threads through nearly every other subsystem, so “run both and compare” would mean maintaining two divergent copies of the component that holds the conversation’s state and services, and praying they stay in sync across hundreds of concurrent edits. The coupling that makes a component hard to port is the same coupling that makes it near impossible to shadow without risking introducing more regressions than it avoids. The benefits of being able to swap in this manner are primarily about gaining confidence, which we could do in other ways.

Validation also happened through incremental rollout. With a big-bang cutover approach, we would hold everything in a long-lived branch, port the whole runtime, and cut over once. That means consumers experience every ported line at once, including all regressions that slipped through in-repo testing. Rolling out portions of the change incrementally, two components here, one component there, enabled us to get a last-mile of validation in deployed builds with real consumer usage (most often first-party within Microsoft and GitHub) but while keeping the risk of regression to a minimum. Over the roughly fourteen-and-a-half-week porting window, `main` shipped 135 releases, inclusive of 100 pre-release versions and 35 stable versions, averaging around 1.3 releases per day. Roughly 1.3 port pull requests opened per day, as well, such that each release carried a small and knowable set of ported components (we generally tried but didn’t always succeed in shipping ports in a pre-release first). In a trailing seven-day npm sample, pre-release versions accounted for only 10.5% of downloads, indicating that initial exposure was relatively limited while we monitored feedback channels for signals of things breaking and quickly turned around fixes in the next pre-release. Reported issues were more easily correlated with known recent changes, and more easily root caused and quickly fixed. In this manner, doing the porting incrementally over a longer period of time was actually a feature rather than a hindrance (i.e. faster is not always better). By August 21, the runtime was 100% production Rust: 832,378 lines of production Rust and 468,689 lines of Rust unit tests, in addition to 174,675 lines of E2E TypeScript tests. The separate GitHub Copilot SDK repository added another ~130,000 lines of E2E test code across Node.js, Python, Go, C#, Rust, and Java.

![Timeline from May 12 through August 21 showing 128 port pull request merges sized by changed lines and 135 public CLI releases; the largest port changes cluster near completion of the port.](https://github.blog/wp-content/uploads/2026/09/port-fix-release-timeline-1.svg?resize=1024,397)

## Getting going

Before going all in, we gained confidence and proved things out. We started with two pull requests that established the Rust workspace, toolchain, lint rules, CI, build pipeline, and coding instructions, and then introduced the runtime crate plus code generation and interop patterns while porting a collection of pure-logic primitives chosen specifically because they had no I/O or shared state and already had strong tests. Only after those landed did the first primary port pull request take three side-effect-free helpers through the full process. These functioned as shipping pilots, turning assumptions about repository layout, FFI, packaging, testing, and review into conventions the subsequent much larger ports would then reuse. Basically, we tested the machinery end to end. The plan continued by ordering the work from the leaves inward, with pure helpers, content exclusion, shell utilities, and session filesystem operations establishing the translation and testing pattern. Stateful subsystems followed, and tools, hooks, model clients, and MCP built on those pieces. Session orchestration (by far the most coupled and least naturally parallel part of the runtime) would come near the end.

![Timeline of 128 landed port pull requests from May through August, progressing from small foundational components to larger orchestration and session work.](https://github.blog/wp-content/uploads/2026/09/pr-timeline.svg?resize=712,1024)

| Period | Pull requests | Median changed lines |
| --- | --- | --- |
| May 1–15 | 8 | 3,250 |
| May 16–31 | 2 | 9,421 |
| Jun 1–15 | 40 | 5,073 |
| Jun 16–30 | 31 | 8,253 |
| Jul 1–15 | 10 | 9,514 |
| Jul 16–31 | 14 | 28,159 |
| Aug 1–15 | 19 | 13,861 |
| Aug 16–30 | 4 | 99,445 |

The early ports, small leaf components, moved quickly. But larger subsystems didn’t move in one atomic step; MCP support, for example, progressed through seven dedicated pull requests, while tools progressed via a six-part series and then needed additional work to move orchestration and retire the remaining TypeScript. Hooks, auth, telemetry, plugins, settings, and persistence followed similar paths.

In effect, the useful unit of porting wasn’t always “a component.” It was often a wave through regions of related behavior: first move the pure logic, then move state ownership, then move orchestration, then remove fallbacks, and finally simplify the Rust after the temporary interop was gone.

## Interop

There are two main layers in this port involving interop:

1. **Temporary internal interop.** Any time a function was ported to Rust, that function needed to be invocable from whatever TypeScript was calling the initial TypeScript function. Similarly we needed to enable Rust functions to invoke TypeScript callbacks. This interop need is an implementation detail and extremely fluid. As the Rust internal surface area grows, so too do the number of TypeScript shims needed, as they’re 1:1 with whatever Rust methods need to be called from TypeScript. As those callers get ported to Rust, that existing layer of shim is deleted, and a new layer is put in place. Eventually we reach the public entrypoints into the runtime library, and the shims evaporate.
2. **The SDK surface.** All of the SDK libraries need to be able to sit on top of the runtime and expose its functionality. In the pre-port world, this was done by having the runtime exposed via a bidirectional JSON-RPC layer, with the SDK sending function call requests as JSON-RPC method call payloads, the runtime parsing the request and invoking the relevant API, then sending back the result via the same transport for the SDK to parse and return. The inverse direction also exists; the runtime needs to be able to call back to the SDK client, for example for hook notifications and permission demands, which surface in the SDK clients as callbacks using whatever language feature is considered idiomatic (e.g. delegates in C#).

We achieved (1) via the `napi` Rust crate from the napi-rs project, which exists to build Node native addons in Rust. You annotate a function with `#[napi]`, and a napi-rs macro generates the N-API registration glue that makes the function callable from JavaScript, plus a TypeScript declaration for it in a generated `index.d.ts`. A synchronous Rust function becomes an ordinary JavaScript function, an `async fn` becomes a JavaScript function returning a promise, and structs annotated `#[napi(object)]` become plain objects on the other side.

Traffic has to move in both directions, too. Many ported components temporarily depended on something that hadn’t been ported yet, so Rust needed to call back into TypeScript, for example a tool implementation in Rust asking the still-TypeScript model layer for inference, or raising a hook, or requesting a permission decision for a command it wanted to run. napi-rs handles this with “threadsafe functions,” which let Rust code running on a Tokio worker thread invoke a JavaScript callback back on Node’s main thread. Node installs the callback once, Rust holds it and calls it whenever it needs to go the other way. Every one of these is temporary by construction: the callback exists only because the thing on the other end is still TypeScript, and it gets deleted when that thing is ported.

![Temporary Rust N-API exports and their TypeScript call sites grew during the incremental port, then declined as callers moved to Rust and the temporary interop surface disappeared.](https://github.blog/wp-content/uploads/2026/09/interop-surface.svg?resize=1024,546)

The temporary seam peaked on August 3, with 2,019 internal N-API exports and 3,356 TypeScript call sites. At completion, the runtime was entirely Rust, therefore no internal interop: 0 temporary internal N-API exports and 0 TypeScript call sites remained. (I mentioned earlier that the CLI still has some internal access to the runtime we’re working to remove; those exports aren’t counted here.)

The second interop layer, the SDK surface, is the permanent one of the two. The Copilot SDK ships for six languages: TypeScript, Python, Go, C#, Java, and Rust. All of them speak the same bidirectional JSON-RPC contract, and originally all of them reached it the same way: spawn the Copilot CLI in headless mode as a subprocess, and talk to it over a pipe or a socket. That remained the default during the port. It also meant an SDK consumer in any language ships or locates a full Node implementation, pays a process hop on every event and every message, and supervises two processes instead of one.

Porting the runtime to Rust is what makes the other option viable. The shipped `runtime.node` is an ordinary platform shared library (the `.node` extension is the Node.js native-addon convention; underneath it is a `.dll`, `.so`, or `.dylib`), and it now presents two front doors onto the same engine. There’s the napi door, which a Node process loads as a native addon; that’s the CLI’s path (today… in the future, the intent is it’ll go through the SDK path fully). And there’s a C ABI door, which any language can load into its own process and call through FFI. The same in-process runtime is selected through each language’s native interop mechanism:

| SDK | Native bridge | In-process client selection |
| --- | --- | --- |
| C# | P/Invoke | `new CopilotClient(new CopilotClientOptions { Connection = RuntimeConnection.ForInProcess() })` |
| Go | `purego` | `copilot.NewClient(&copilot.ClientOptions{Connection: copilot.InProcessConnection{}})` |
| Java | JNA | `new CopilotClient(new CopilotClientOptions().setConnection(RuntimeConnection.forInProcess()))` |
| Python | `cffi` | `CopilotClient(connection=RuntimeConnection.for_inprocess())` |
| Rust | `libloading` | `Client::start(ClientOptions::new().with_transport(Transport::InProcess)).await?` |
| TypeScript | `koffi` | `new CopilotClient({ connection: RuntimeConnection.forInProcess() })` |

The Rust rewrite and the choice between in-process and out-of-process hosting are separate dimensions. The completed Rust runtime supports both: it can run inside the SDK consumer’s process or behind the existing JSON-RPC server boundary. Those in-process entry points are currently opt-in while we gain confidence in sharing a process, and therefore a failure boundary, with the consuming application. Everything above the transport remains the same SDK API: sessions, events, tools, permissions, and callbacks do not care whether their JSON-RPC bytes crossed a pipe or a function call.

The interesting thing about the second door is its size. It has just 19 exported functions: four for server lifecycle, four for session registration and configuration, eight for connections, and three for the embedded host. Behind those functions, the shared contract currently contains 364 dispatch routes: 340 are callable by SDK consumers, while 24 run in the other direction as runtime-to-SDK callbacks. The napi door is much larger, needing functions for every one of those dispatch routes. The C ABI door is dispatch-based: API methods don’t get exports at all, but rather travel as JSON-RPC bytes written into a connection, and results, events, and server-to-client requests come back on host-supplied callbacks. Adding, changing, or removing an API method touches the engine’s dispatch table but never touches the ABI. An SDK binds those 19 entry points once and reaches the entire, still-growing API surface dynamically through them.

Which raises the obvious question: why is there still JSON-RPC in a call that no longer crosses a process boundary?

The answer is that it made in-process hosting a drop-in rather than a rewrite. Every SDK already had a working JSON-RPC client, with framing, request and response correlation, and handlers for the server-to-client direction. Mounting FFI as one more transport underneath that client moves the byte path from a pipe or socket to a function call and leaves everything above it untouched. Six SDKs got in-process hosting as an additive, opt-in transport, with the existing ones unchanged. Had we instead defined a typed C function per API method, every SDK would have needed a second binding layer, every new API method would have needed six more bindings, and the ABI would have become a binary compatibility surface we had to version.

We also still need JSON-RPC for runtimes that genuinely are remote, whether across a subprocess boundary or over TCP. Keeping the same protocol in-process means maintaining one bidirectional API and dispatch system rather than JSON-RPC for remote connections plus a second per-method FFI surface for local ones. That’s a real tradeoff rather than a no-brainer decision. We avoid the process hop, but we still pay JSON-RPC overhead on every call. For inference-dominated workloads, that serialization is generally small potatoes compared to the model round trip. It is still measurable in high-throughput local workloads, but not enough today to justify duplicating hundreds of methods across six SDK bindings. And it’s a decision we can easily revise later should the performance need present itself. The payload encoding is a private detail of the two ends, and swapping JSON for something denser like MessagePack would not change a single declared export. Typed per-method exports can also be added later for hot paths, calling the same engine and the same handlers, without replacing the byte channel, which would remain the substrate for streaming, server-to-client requests, and the long tail of rarely called methods where a bespoke export buys nothing.

## What the session data shows

Nearly every figure in this post comes from one of two sources. The first source is the GitHub history of the private `github/copilot-agent-runtime` repository: pull requests and their diffs, review comments, CI runs, etc. The second source is the agent session logs. The runtime (and thus the CLI, app, etc.) writes a structured event log for every session it runs: one JSON object per line, appended as the session happens. Those logs can include prompts, commands, command output, file paths, and potentially secrets surfaced by tools, so they must be handled as sensitive data. The log is local to the machine where the session ran; remote-session features can also upload it when enabled, subject to product settings and organizational policy.

Here is a summary of the data across all the constituent porting pull requests:

| Metric | Count |
| --- | --- |
| Events | 12,760,995 |
| User messages | 31,247 |
| Assistant messages | 1,385,214 |
| Hook start and end events | 6,438,562 |
| Tool starts | 1,857,409 |
| Compilation commands | 23,096 |
| Test commands | 19,485 |
| Rebase commands | 2,496 |
| Commit commands | 7,410 |
| Push commands | 5,554 |
| Completed compactions | 5,116 |

Those 31,247 user-role messages aren’t 31,247 prompts I personally typed; they include skill instructions, automated merge ticks, cross-session messages, and child-agent traffic, beyond the roughly 2,600 I typed or spoke, about one in 12. Similarly, the 1,385,214 assistant messages include subagents and tool-oriented messages, not just text shown to me in a conversational UI. The corpus contains 68 distinct event types and 67 distinct tool names; 1,130,921 tool calls, 61%, came from subagents rather than the main session thread.

The count doesn’t say why I was inserting myself ~2,600 times. For that, I had Copilot assign one primary intent to each of the human-authored messages in the session log corpus.

![Of 2,639 human-authored messages, 31.0% focused on review, testing, and CI; 17.4% challenged technical or design decisions; and 15.0% pushed for completeness.](https://github.blog/wp-content/uploads/2026/09/user-message-intents.svg?resize=1024,538)

The first three buckets account for 63% of my interactions. Only ~40 were recognizable session kickoffs from me, as that largely happened by me first creating a chat to explore the next horizon and then asking that chat session to create actual porting sessions for each desired slice. My role was less “assign a task and wait” and more “operate the control loop”: inspect the result, challenge technical decisions, enforce quality gates, and push when an agent treated an intermediate stopping point as the finish line. Human judgment was still very heavily involved even though agents were doing “the work.” My involvement just moved upward… instead of being responsible for writing syntax, I was responsible for framing the problems, defining boundaries, choosing strategies, adjudicating exceptions, and overall ensuring everything was moving in a good direction.

### It’s all about caching

LLM providers typically charge one rate for input tokens (what you send to them) and another rate for output tokens (what they send to you). Billing is often done by tokens because they are a useful approximation of the computational work required to do the inference: for each input token, the model must read it, incorporate it into its internal representation, and use it as part of the computation that determines the next token. However, providers often support caching the results of those computations, such that if an identical prefix of a prompt has already been processed, the provider can reuse intermediate computations from the cache rather than recomputing them from scratch. That reduces the cost of processing those tokens, and that savings can be passed along to the consumer. As such, input tokens are often advertised with multiple rates, including a rate for input tokens that are read from the cache.

The discounts are steep! Often providers bill cache hits at a 90% discount, so for example a provider might charge $2.00 for 1 million input tokens but only $0.20 for 1 million cached input read tokens. In other words, you really, really want to maintain good prompt caching so that your bill is an order of magnitude smaller.

The data from the porting effort shows we did well here. The prompt-cache hit rate was 96.22%: cache reads divided by all input-side token volume (cache reads plus cache writes plus fresh input). Cache writes were 3.07%, and fresh input was 0.71%. This is not an accident. GitHub Copilot shapes the agent loop specifically to preserve a long and stable prefix (the system prompt, then the tool definitions, then the accumulated conversation), so each turn appends to context the model has already processed. The expensive part of the context is paid for once and then re-read at an order of magnitude less monetary cost on every subsequent call. It’s also the reason the economics of long autonomous sessions hold together at all. A three-hundred-hour port that re-read its entire growing context from scratch on each of tens of thousands of calls would cost a different order of magnitude than what we saw. Agent harness developers spend a great deal of energy trying to avoid breaking the prompt cache, and model vendors routinely ship new features to help them do so.

![Prompt-cache composition across the porting sessions: 96.22% cache reads, 3.07% cache writes, and 0.71% fresh input.](https://github.blog/wp-content/uploads/2026/09/prompt-cache-composition.svg?resize=1024,333)

Compaction tells a complementary story. Across the port sessions, GitHub Copilot compacted context automatically 5,116 times (the moments when a session had filled its context window and summarized itself in order to keep going). The single sessions-infrastructure port pull request compacted 647 times over its many-day lifespan, while one small port never compacted once. Sustained multi-hundred-hour autonomous work is only possible because the agent can recycle its working memory over and over without losing the thread. Every one of those thousands of summarizations was a point where a lossy handoff could have quietly derailed the port, and mostly didn’t. Copilot’s subagents also greatly factor into minimizing compaction. Each subagent gets its own context, so a parent session can effectively ask a question, have a subagent go off and exert a fair amount of context in computing the answer, and then report just the answer back to the parent. The parent’s context needn’t be impacted by all of that intermediate information.

The “mostly didn’t” above is visible in the session logs. I had Copilot pair each successful compaction with the work surrounding it when at least 20 tool calls existed on both sides. That produced ~4,000 comparable windows. The mix of what the agent did in the 20 tool calls before compacting looks similar in scale to what it did after compacting (exploration 46.5% before and 48.1% after, mutation 8.4% before and 6.0% after, validation 4.7% before and 4.0% after, failures 1.0% before and 1.5% after). If compaction were regularly dropping the thread of thought, we’d expect the after side to be visibly re-orientation-heavy, with a spike in reading and a collapse in editing while the agent rediscovered where it was and what it should be doing. Instead, there’s only a mild shift in that direction.

### Yes, static analysis helps

There’s a popular meme that Rust is an unusually good target for AI-generated code because Rust’s strict compiler catches what the model gets wrong. The session logs let us test that theory, at least for tasks that look like this porting effort.

Direct validation-command results captured 8,678 occurrences of `rustc`‘s error codes. The four largest diagnostic families cover 84%:

- 37%: Name and import resolution, dominated by `E0425` (“cannot find value in this scope”)
- 22%: Missing methods or fields
- 14%: Type mismatches
- 11%: Unsatisfied trait bounds

Every one of those is ordinary wiring: a name output slightly wrong, a signature that didn’t line up, a field that had been renamed, an abstraction left unimplemented. These are the kinds of mistakes bulk translation easily and accidentally produces and exactly the ones a compiler catches very quickly.

But note what’s absent from that list: anything truly specific to Rust. Every one of those four categories is bread-and-butter static typing, and a C# or Java or Go compiler would catch all of them just as well, several of them with friendlier diagnostics, and all of them a great deal faster. If this is the argument for pointing agents at Rust, it’s really an argument for pointing them at any statically typed language. A strongly typed compiler and/or a language with excellent static analysis and linting is genuinely a good fit for this work, with the agents using it as a fast feedback loop. Across the 4,478 direct `cargo check` runs for which the stricter result matcher captured an outcome, 87.1% came back clean, which is what you get from editing in small increments and recompiling constantly.

In contrast, ownership, borrowing, and lifetime errors combined were only 1.7% of coded diagnostics. The borrow checker, the thing that dominates every conversation about Rust being hard, was a quiet background presence. The compiler spent almost all of its erroring energy on boring mechanical mistakes.

### Agents like reading

We can also examine the corpus of session events for tool call data, and from that extract some interesting observations about how agents spend their time.

| **Tool** | **Calls** | **Median** | **Measured hours** |
| --- | --- | --- | --- |
| `powershell` | 630,423 | 3 s | 2,833.9 |
| `view` | 590,988 | 0 s | 621.7 |
| `rg` | 281,783 | 1 s | 408.4 |
| `grep` | 126,483 | 1 s | 115.3 |
| `apply_patch` | 53,715 | 0 s | 17.0 |
| `edit` | 40,591 | 1 s | 24.1 |
| `read_powershell` | 36,728 | 90 s | 1,203.9 |
| `task` | 13,080 | 274 s | 2,329.0 |

My first takeaway here is that the agents spent far more time gathering evidence than changing code. Across the displayed file-reading and search tools versus the editing tools, they did 10x as much exploration as mutation. Reading files, searching the repository, and running diagnostic commands dominated; edits were a comparatively small effort. The popular image of AI spewing code is almost backwards; at this scale, the work looked much more like iterative investigation, inspecting the current state, forming a hypothesis, making a targeted change, rinsing and repeating.

Delegation amplified that pattern. Subagents were used primarily to fan out exploration across independent questions, while the main agent was more likely to own the edits and integrate the answers. That’s a useful division of labor for this kind of project: many contexts can investigate in parallel, but keeping mutation closer to the coordinating agent reduces conflicting changes and preserves a coherent implementation strategy.

The shell traffic also shows how much of autonomous software work is state management. Read-only Git inspection was the most common command pattern because the agents were constantly asking, effectively, “where am I?” They were looking for what had changed, what a rebase had done, what another session had landed, and how far a branch had drifted from a rapidly evolving `main`. That orientation work enabled many long-running efforts to operate against the same moving codebase without blindly overwriting one another.

Looking inside the shell-tool traffic, the most common command families make that balance between orientation and validation even clearer:

| **Command family** | **Calls** | **Median** | **Measured hours** |
| --- | --- | --- | --- |
| `git` inspect | 300,530 | 2 s | 608.1 |
| `git` other | 89,865 | 3 s | 243.1 |
| search | 85,482 | 2 s | 147.6 |
| `pnpm test` | 13,852 | 22 s | 219.1 |
| `pnpm lint` | 9,757 | 29 s | 177.0 |
| `cargo test` | 8,437 | 120 s | 364.2 |
| `git commit` | 7,410 | 11 s | 39.7 |
| `cargo fmt` | 5,223 | 18 s | 77.2 |
| `cargo check` | 4,492 | 120 s | 176.9 |
| `pnpm build` | 3,630 | 180 s | 215.6 |
| `cargo clippy` | 2,115 | 135 s | 107.4 |
| `git rebase` | 2,496 | 7 s | 9.9 |
| `cargo build` | 566 | 104 s | 20.3 |

### Model selection

GitHub Copilot lets a single session change models mid-conversation and lets different sessions run different models, so model choice became a per-slice decision. Two different kinds of model decision show up in the logs. On the main thread, the one driving each port, we chose the model and the reasoning effort. Inside a session, when the agent spun up subagents or subsessions to explore or to grind through a bounded task, the orchestrating model chose those models.

![Weekly model mix across primary porting sessions, concentrated in a small number of models and shifting over the course of the port.](https://github.blog/wp-content/uploads/2026/09/weekly-model-mix.svg?resize=1024,512)

For subagents, the model mix looks a bit different, with an agent rather than a human optimizing for throughput and cost rather than for the hardest judgment calls. The subagents it spawned most often ran on Claude Opus 4.8, GPT-5.6 Sol, Claude Haiku 4.5, and GPT-5.5, followed by Gemini 3.1 Pro and Claude Opus 5. However, at least at the time the ports were happening, three frequently used agent definitions pinned their model choice (`explore` and `task` to Claude Haiku, `research` to Claude Sonnet), so a significant part of that volume was determined by the choice of subagent rather than by choosing a model separately.

![Weekly model mix across subagents spawned during the port, with volume concentrated in Claude Opus 4.8, GPT-5.6 Sol, Claude Haiku 4.5, and GPT-5.5.](https://github.blog/wp-content/uploads/2026/09/weekly-subagent-model-mix.svg?resize=1024,512)

## Working with agent fleets

The GitHub Copilot app’s support for visualizing active pull request sessions, the status of each, and easily switching between them made it ideal for managing lots of concurrent work inherent to the port. But one of the things that really made it shine was its ability for sessions to interact with other sessions.

A session can create other sessions, and it can message other sessions while they run. Each session, parent or child, gets its own worktree, its own branch, and its own agent loop; it’s separate from the session that spawned it rather than something running inside it. That’s different from a subagent, which runs inside the parent’s own workspace and hands its answer back into the parent’s context. Both are useful constructs for different things.

As an example of how a session might create other sessions, one of the hardest ports was for the `session.ts` file. This file had grown organically to be ~30,000 lines of TypeScript. It represented the backbone of a session and effectively spanned horizontally across the whole runtime, touching and being touched by practically every component, sitting at the center of state, events, tools, models, hooks, persistence, and entrypoint access. As a result, I left it for close to the end of the porting process, working up from the bottom of the stack across all the verticals until they all dead ended at `session.ts`. The porting session that took it on did not start by just diving in and writing Rust. It spent its first fifty-six minutes reading, with 122 tool calls before it created anything, building a picture of what the file actually owned and where the seams were. Only then did it start delegating, logically splitting up the file and delegating slices to subsessions. Across the whole 25-hour run, it made 222 shell calls, 205 file views, and 197 ripgrep searches of its own, on top of everything its child sessions did.

![Fifteen child sessions nested under the session that spawned them.](https://github.blog/wp-content/uploads/2026/09/session-fanout.png?w=373)

That’s 15 child sessions, each one a separate branch with its own worktree and a separate agent, all created implicitly by the parent session at the top. The parent session created them in seven waves over about three hours: the first wave created five, the second another two about twenty minutes later, then another pair twenty minutes after that, then singles and pairs spread out over the next two hours.

Model choice was made per slice: 10 of the 15 ran on GPT-5.6 Sol and five on Claude Opus 4.8. All 15 were started in GitHub Copilot’s autopilot mode, which lets a session pursue an objective without stopping for approval at each step. The median kickoff prompt was about 1,100 characters, long enough to carry the ownership boundary and the constraints, but short enough that the child session had to work out the approach itself. I prompted the parent agent, and then the parent agent, not a human, wrote those kickoff prompts to each child session.

Alongside those 15 subsessions, the same parent session also employed five subagents: three `explore` agents fired in parallel with the first wave, one `code-review`, and one `rubber-duck`. These subagents explored questions, feeding back to the parent answers it needed in its context before it could decide what to do next. The subagents allowed the parent to get deeply thought out answers without needing to spend its own context window on deriving them.

In contrast, child sessions went to work on the actual porting, the work that produced a diff and needed isolation from the other parallel porters. The child sessions’ work touched 140 distinct files in the repo, 120 of which were touched by exactly one session. The 20 contended files were all hubs, such as `session.ts` itself. But each session was working in its own worktree, and so was able to proceed undisturbed by its siblings. The parent, of course, paid for that in coordination. It spent a good deal of effort communicating with its child sessions, acting as an information broker, polling their state 60 times and sending 89 coordination messages. When the child sessions each announced their completion, the parent cherry-picked their commits into its own branch and resolved the conflicts. These were not particularly clean merges, either, and the parent agent spent a decent amount of time reconciling the edits.

We can see that on a timeline, which shows the parent session and most of its child sessions.

![Timeline of the session.ts port showing one 25-hour parent session using five subagents and spawning 15 child sessions in seven waves.](https://github.blog/wp-content/uploads/2026/09/session-ts-fanout.svg?resize=1024,695)

Note those large gaps. I was traveling while working on this port, and I had to close my laptop at various points. (I subsequently changed my workflow to incorporate cloud-based virtual machines I could remote into.)

These parallel child sessions had a significant impact on that laptop. For a while, the concurrent porting was going swimmingly. Then all 15 concurrent agents on one machine each tried to build and test, and my poor laptop ground to a halt. I prompted to the parent, asking it to relay to its child sessions that they must all stop building and testing. The parent relayed that constraint outward, and they thankfully killed their builds and proceeded to work with minimal CPU activity. I subsequently updated my standing instructions that subagents and subsessions should avoid large builds and test runs while porting, instead deferring that to be done only by the parent agent.

Later I took that one step further and made an otherwise ordinary chat session into a build scheduler for eight independent porting sessions. The prompt was embarrassingly simple: send every open session a policy to avoid CPU-intensive building and testing where possible, require it to ask this session for permission when a build was necessary, and act as a gate, handing out the ability for one session at a time to build. Basically I turned the chat session into an agentic mutex. The gate kept an explicit owner and queue and granted one lease at a time through the same cross-session messaging mechanism the sessions already used to coordinate code. Sessions that asked for and were denied the lease often waited by doing other work in the meantime, like picking off things from its todo list.

![One chat session acting as the build resource gate for eight porting sessions.](https://github.blog/wp-content/uploads/2026/09/build-gate.png?w=1698)

This `session.ts` port was also involved in one of the coolest, saddest, and certainly most unexpected interactions I witnessed during the whole runtime port. As I mentioned, we did the port primarily bottom-up, which is why `session.ts` that effectively sits on top of every other component was one of the last components ported. The only thing consistently above `session.ts` are all of the entrypoints into the runtime, namely the public functions that are exposed from the SDK and that show up in the previously discussed dispatch table. There are hundreds of these. And while I know that many of them immediately call into `session.ts`, I wanted to get a jump on the porting, and so after launching the `session.ts` session, I launched a session to port all of the entrypoints. I told it to stop at the `session.ts` boundary. I figured there may be a bit of throwaway work and some amount of effort or number of tokens needed in a rebase, but that it would accelerate the overall porting. Then I went to bed. And then… they found each other.

My kickoff prompt for the entrypoints session did tell it that the session port and six component ports were running concurrently, as I wanted it to know its boundaries and what it should avoid porting to avoid as many conflicts as possible. Apparently my prompting had the opposite effect. Just over four minutes in, having inventoried the ingress paths and presumably formed a view of how much they overlapped, it invoked an app built-in `orchestrate` skill, whose purpose is coordinating work across sessions. From there:

1. The entrypoints session enumerated every active session and sent messages to the ones with perceived overlap.
2. The `session.ts` session answered with a 2,001 character inventory titled “Concrete overlap on `stephentoub-port-session-to-rust`“
3. The entrypoints session read the `session.ts` session’s worktree to confirm what it had just been told (trust but verify, I guess).
4. The entrypoints session asked the `session.ts` session whether it was ready to reconcile its 760 file diff.
5. The `session.ts` session basically told it to get lost: “Not ready to commit/integrate.”
6. The entrypoints session proceeded to ask the same question three more times, and each time it got back the same answer from the `session.ts` session.
7. At which point the entrypoints session decided it didn’t care what the `session.ts` session thought and simply reached into its worktree and grabbed all of the other session’s changes and merged them into its own.
8. Then both sessions went on their merry way.

A few things I took from this interaction:

1. **It’s important to be explicit about intent.** The kickoff prompt named the other running sessions so this one would know what to leave alone. But I didn’t make that “leave alone” part explicit, so instead of blocking the agent from doing something, I ended up encouraging it to do it. I needed to be much more explicit in my intent and guidance.
2. **Whatever you make available is something an agent may decide applies.** The `orchestrate` skill ships in the GitHub Copilot app and describes itself as being for running independent workstreams in parallel. Nothing in my prompt mentioned it. The model discovered its own situation, matched it against that description, and loaded it. The set of capabilities you expose is the set of behaviors you might get, including in situations you never pictured.
3. **Peers need a tiebreaker.** Neither session could compel the other. When the `session.ts` session said it was not ready to integrate four separate times, that refusal carried no weight, so the session willing to act unilaterally won by default. Parallel sessions over adjacent code need a designated coordinator, or they need a human, and these had neither.
4. **“Run autonomously” needs an exception for decisions that reach outside your own branch.** I really meant “don’t wake me up over design details.” It heard (not unreasonably) that annexing a peer was in scope. Again, I should have been more explicit in my guidance.
5. **The root cause here was me.** I partitioned this work top-down and bottom-up at the same time, and the two directions met in the middle at the single most connected file in the codebase. I was too greedy to make forward progress. Everything above follows from that.

This whole interaction was, thankfully, an interesting outlier. Across the whole runtime porting effort, most of the leaf component ports were straightforward single-session tasks. The larger subsystems often involved multiple subsessions and subagents. How those pieces participated over the course of a port varied significantly, though.

The port of model orchestration, the layer that actually talks to the providers, provides a good example of one pattern. Its main session ran 42 wall-clock hours and started 126 subagents. At its busiest, 22 were working at the same time. However, the majority of the time, it was only the main agent, and then now and again it would spawn a significant number of subagents for a window of time.

![The 42-hour model orchestration port generated most code in its first 12 hours, followed by a distinct validation and review phase involving 126 subagents.](https://github.blog/wp-content/uploads/2026/09/port-model-orchestration.svg?resize=1024,717)

Three things in that picture stand out. First, effectively all of the code generation was done in the first 12 hours; the next day of work after that is all validation. Second, the color of the bottom row shifts left to right, from predominantly blue and green (reading, building) to predominantly blue and orange (reading, reviewing); that makes logical sense, but it’s neat to see it in practice. Third, that example, and more generally this pattern, has very clean separation between phases. The extension-runtime port is a counter-example.

![The 88-hour extension runtime port interleaved reading, writing, building, and reviewing throughout most of the session rather than separating them into phases.](https://github.blog/wp-content/uploads/2026/09/port-extension-runtime.svg?resize=1024,717)

It took 88 hours instead of 42 and had very different structure:

- **Writing and reviewing overlap significantly.** Whereas in the previous example, the work was very waterfall (first code generation, then review), here reviewing starts long before writing stops, and both proceed and overlap for most of the duration of the effort.
- **The bottom row is all over the place color-wise.** Where model orchestration shifts from green to orange as it moves from writing to checking, this one is the same mixture of reading, building, and reviewing from beginning to end. The middle half of the reading calls is spread across a 49-hour span, the writing across 33 hours, and the reviewing across 27, inside an 88-hour session. Every category is spread across most of the run.
- **Idling is deferred to the end.** The fleet works nearly continuously for the first 56 hours.
- **The proportions still match.** Writing Rust is 2% of tool calls here against 1% there, reading 44% against 57%, reviewing 23% against 27%. The two sessions agree on what the work to be done is, just not on when it happens.

Those blank slices at the end are also a visual representation of a problem becoming more and more common in this agentic coding era: waiting for approval. Someone on the team and/or an agent reviews the code and leaves feedback, there’s a brief period of activity where the agent addresses the feedback and drives CI to green again, and then more waiting, rinse and repeat, until eventually we get the dopamine-inducing stamp of approval.

These two examples each represent a dominant pattern. About a fourth of the sessions look more like the model orchestration session, while three quarters look like the extensions runtime one. The clean progression through phases was the exception; the common case was the agent planning, writing, and reviewing all the way through.

## Code review at scale

Much of that focus on review visible in the previous graphs was due to my explicit prompting. I created a simple prompt-as-a-skill I called `rust-rebase-review` (in addition to a general Rust coding skill we have merged in the repo). Due to the fast rate of incoming changes, many of which conflicted, I was frequently rebasing. Via custom instructions, I encouraged the harness to invoke this skill at appropriate points in the process, and also manually invoked it from time to time. The prompt evolved a bit over time, but it was a variation of this:

```plaintext
Squash into a single commit, then rebase on the latest in origin/main, resolving all conflicts, and force push. As part of rebasing, pay extra special attention to anything that has changed, been added, been removed, and ensure that logic is all ported over to the corresponding Rust code correctly. Always do the rebasing yourself / in the main agent; do not spawn a subagent for it.

Then enter a review/fix loop where you launch a subagent per opus 5, gpt-5.6-sol, and grok 4.6.

- That subagent should do a line-by-line comparison of the old TypeScript and the new Rust, confirming behavioral equality.
- Look for anything introducing any kind of incompatibility; our goal is to move this code into Rust with as close as is possible to 100% the same semantics. If you hit anything questionable, ask me about it.
- We want to ensure we're writing as efficient and idiomatic Rust code as we can; look for opportunities to simplify, to use routines like from the memchr crate to optimize searches instead of open-coded loops, avoid unnecessary allocation, use traits for reuse and loose coupling, etc.
- Ensure that all defunct TypeScript code (e.g. code that has been fully ported, tests that are now no longer necessary because they're duplicative, unnecessary napi shims, etc.) has been deleted.
- Ensure that we've ported as much code as possible, e.g. if there's any TypeScript remaining in touched files and that TypeScript is more than just a shim, that's a red flag. If new TypeScript that's not just a super thin shim is being added, that's a red flag. Look for any callers of TypeScript shims to see whether those callers can instead be ported to Rust, pushing the boundary as far as reasonably possible. Our goal is to soon get to 100% Rust in the runtime layer.
- Validate that no E2E tests have been deleted or changed. Such changes are an indication of a porting bug.

If a review surfaces issues, validate them, and then if there are any to fix, fix them, and iterate to do another full review. Continue iterating with reviewing/fixing until all reviews come back clean. After every set of changes in response to review feedback, commit and push so that CI validation runs concurrently with subsequent reviews.

Don't bother running full test suites; that'll be handled in CI. Try to minimize CPU-consuming efforts to the bare minimum, as we'll likely have many operations happening concurrently.
```

With the frequent rebasing, it’s easy for incoming changes to get lost accidentally. But we found an unexpected benefit to the in-place atomic swap: by deleting the TypeScript at the same time as we were adding the corresponding Rust, we were implicitly creating conflicts with rebase-induced incoming changes to that TypeScript: one branch changing it and the other deleting it. This guaranteed we’d notice changes to already ported code, rather than needing to rationalize for every incoming line whether it was something that might have been touching previously ported code.

My own reviews were of course only a portion of the agentic review being performed. In addition to CCR running on every commit, the team has multiple dedicated code review bots, each with their own approaches and prompts, running on every commit and providing detailed feedback. All of this would end up in comments on the pull requests that would then need to be addressed. Thankfully, handling all of that agentic feedback can also be handled agentically (mostly).

For my review, I focused on architecture, design, conventions, and approach. The agents did the exhaustive old-versus-new comparisons; tests and static analysis checked mechanically enforceable properties; human reviewers concentrated on architecture, API contracts, risk, and any suspicious places surfaced by those other layers.

I chose the destination architecture, decided what behavior mattered, partitioned the work, resolved ambiguous trade-offs, judged the evidence, manually reviewed high-risk areas, reviewed agentic responses to feedback, and made the final merge decisions. The agents changed the amount of code one engineer could supervise. They did not remove the need for an engineer who understood the system and could vouch for the direction, the guardrails, and the release.

## Automating the inner loop

The GitHub Copilot app was central to this work. It manages lots of concurrent active sessions, making it easy to switch between them and carry along with each all the relevant paired context (associated terminal windows, browser windows, canvases, and so on). The feature that mattered most here is agent merge:

![The GitHub Copilot app's Agent Merge panel tracking review feedback, CI, conflicts, and merge readiness.](https://github.blog/wp-content/uploads/2026/09/AgentMerge.png?w=946)

Agent merge is a loop built into the app (the CLI has it as well with `/pr auto`). On a timer or in response to external stimuli (like notifications from GitHub of CI completion or a review comment), the app will look to see what has changed. If a review comment has been left, it’ll invoke the agent to decide whether to reject the comment or to accept and address it (and respond, noting it’s automation that’s responding). If a test fails, it’ll download the logs, investigate the failure, and fix the bug. If a conflict occurs, it’ll invoke the agent to merge or rebase. Effectively, it automates the loop we as human developers all do, driving our pull requests to “green”, getting sign-off, and eventually merging.

Agent merge handled every single porting pull request. In most cases we stopped short of the actual “merge” part, however. The agent would fix all CI failures, address and respond to all comments, and make sure all conflicts were resolved. Before merging, I’d spot check what the agent actually did, and in particular how it addressed feedback. Did I disagree with any of its responses to reviewers? Was the high-level direction of applied fixes desirable and sound? For these ports, I typically left that last entry unchecked.

That last checkbox mattered more than once. In one merge loop pass, the port had deleted one of the functions exposed to the SDK. Our repo’s schema compatibility CI leg did exactly its job and failed. The agent’s response was to apply the repo’s `schema-break-ok` automation label, which is the escape hatch to make the check pass. In reviewing the pull request before merge, I asked the obvious question: “What is the schema break? You added the `schema-break-ok` label to the pull request; why is it ok?” It wasn’t. The method existed on `main`; the port had simply lost it. I called it an unacceptable regression and told the agent to bring it back fully in Rust. Twenty-one seconds later, the waiver was removed, and the method was restored with a native Rust implementation.

We responded to failures both locally and globally, fixing the individual cases but then also fixing the system so that they’d be less likely to recur. We continually evolved the instructions fed to the coding and review agents to further reduce the chances of these same issues happening again in future porting pull requests. We turned session logs into evals. And in some cases, we actually used the lessons learned to improve the runtime itself, via tweaks to prompts or tool descriptions or how autopilot operated.

## Two migrations in one

A language rewrite is almost never only a language rewrite. Every library the runtime leaned on had to be replaced, too, and unlike in the Rust code we wrote and owned, those replacements weren’t ours to make faithful. Some were the same idea under a different name. Some took several crates to cover what one npm package had done. A few had no acceptable off-the-shelf answer at all and had to be written by hand (by agent).

The CLI and runtime are currently in the same repo, sharing a `package.json`. Over the course of the port, we removed ~60 npm dependencies because they were only being used by runtime code that was ported to Rust. This is a lower bound for removals, because there were packages replaced for the runtime but that were still needed by the CLI. For example, `zod` is a TypeScript schema declaration and validation library that both the CLI and the runtime were using. With the Rust port, the runtime now uses a combination of `serde`, `schemars`, and `jsonschema` to satisfy the same purposes, yet `zod` is still in the manifest for the CLI’s sake.

There were a bunch of examples where one npm package became one crate doing the same job. `js-tiktoken` became `tiktoken-rs` with the same `o200k_base` encoding. `ignore` became the crate of the same name with the same gitignore semantics. `minimatch` became `globset`, `fast-myers-diff` became `similar`, `dompurify` became `ammonia`, and `github/keytar` became `keyring`.

In other cases, we weren’t able to replace packages with crates one-to-one. Instead one package became several crates, or several collapsed into fewer. This is where the majority of the dependency work went. Eight `opentelemetry/*` packages became four crates plus a hand-written tracker state machine and file exporter. Three web-content packages, `mozilla/readability`, `linkedom`, and `turndown`, became two crates, `readability` and `htmd`. `sharp`, `image-size`, `file-type` became `image` and `imagesize`. And so on. There were also five cases where we replaced an npm package entirely with a completely custom implementation.

## How much `unsafe`?

Another question people ask about agent-written Rust is how much of it quietly opted out of safety guarantees. Rust’s safety is a property you can turn off with a keyword, so an agent that hits a borrow it can’t satisfy has an obvious escape hatch. Within the entire runtime crate, we now have 158 `unsafe` blocks, across only 36 files (alongside them are 26 `unsafe fn` declarations, 26 `unsafe extern` blocks, and nine `unsafe impl` trait implementations). Importantly, every single one of these is about interop with external components.

| Why the unsafe block exists | Blocks | Share |
| --- | --- | --- |
| C ABI boundary | 51 | 32.3% |
| Windows API | 49 | 31.0% |
| POSIX / libc | 46 | 29.1% |
| SQLite C API | 7 | 4.4% |
| Dynamic library loading | 4 | 2.5% |
| Process environment | 1 | 0.6% |

![Donut chart of 158 unsafe blocks, all at external boundaries: C ABI, Windows API, POSIX and libc, SQLite, dynamic library loading, and process environment mutation.](https://github.blog/wp-content/uploads/2026/09/unsafe-boundaries.svg?resize=1024,427)

The C ABI blocks are the front door SDK hosts come through, so they receive raw pointers and lengths from a caller the Rust compiler has no control over. The Windows and POSIX blocks are system calls: registry reads, credential handshakes, process trees, `sysconf`. SQLite is a C library. Dynamic library loading is `dlopen`, which cannot be safe by construction if for no other reason than because the symbol you resolve might not be the function you expected. The process environment `unsafe` block exists because Rust 2024 treats mutation of process-global environment state as unsafe in a multi-threaded process. Every one of those `unsafe` blocks marks a place where the guarantees Rust makes genuinely end: the thing on the other side is a C function, a syscall, a pointer from a foreign runtime, or process-global host state.

The useful property is that `unsafe` makes all of these places in the Rust code we own auditable. The equivalent code in the TypeScript runtime crossed exactly the same boundaries, through Node’s C++ internals and native npm packages, and nothing in our source marked where the checked world stopped. Of course, that’s not a complete inventory of every safety boundary in the delivered system: dependencies, build tools, C libraries, safe wrappers, and incorrectly specified FFI contracts can still contain or expose unsafety.

The details of where `unsafe` is used are interesting, but I’m more interested in where it isn’t used. It’s not used in the model clients, in the MCP layer, in the agent layer, or in the prompt layer. And not one of the known regressions from the porting effort involved an `unsafe` block.

## The regressions

Porting code is easy. Making it correct is hard. And with a codebase as large and as complicated as the Copilot agent runtime, regressions are to be expected.

By September 14, 2026, we’d traced dozens of known port regressions, all fixed. Most were correctness bugs, with a smaller set of performance regressions. Against the ~832,000 lines of production Rust written from scratch.

Of course, not all of those regressions shipped. Some were caught just by developing in the repo. Others appeared in a pre-release but were fixed before making it to a stable release. And some reached a stable release, often after being unnoticeable enough to make it through one or more rounds of pre-release usage undetected.

![Known correctness regressions grouped into five recurring failure modes: incomplete migration, state and lifetime, behavioral contract mismatches, host boundaries, and incorrect test oracles.](https://github.blog/wp-content/uploads/2026/09/regression-taxonomy-1.svg?resize=1024,350)

This isn’t zero, of course, and I’m 100% sure there are more than the ones we know about. These are the ones we noticed or that were reported, but a migration this size absolutely shipped a few more that have been quiet enough that nobody has hit them yet. As with bugs in general, I expect we’ll continue to discover a trickle of corner-case regressions as the further reaches of the stack are exercised aggressively in the wild.

The absolute number also isn’t all that important. What’s more important is the “why” all of these happened, so that we can learn from the issues and avoid repeating them in the future. Nearly all of the correctness regressions land in three large families: the new code implemented a different behavioral contract; state, ownership, or lifetime behavior changed; or some part of the migration was omitted, only partially applied, or lost in rebasing. A smaller set came from requirements at the host or interop boundary, and even from tests that confidently validated the wrong behavior. Those families become more concrete in a handful of recurring patterns:

**Ambiguous semantics.** Several regressions came from behavior the source language left implicit. TypeScript has one `number` type; Rust requires choosing among several, including whether a value can be fractional. And the agents guess wrong. Fields that were conceptually integers became `f64`s, so Rust serialized values such as `42.0` instead of `42`: strongly typed SDKs like Go and C# couldn’t unmarshal a repo ID into an `int64` and rejected a hook timestamp and task duration. In the other direction, an agent declared `timeToFirstTokenMs` as `i64`, but the streaming path emitted values such as `5446.712845`, making written sessions unreadable and unresumable. A subtler case wasn’t about types: `event.error || "Unknown error"` became `.unwrap_or("Unknown error")`. JavaScript’s `||` replaces an empty string; Rust’s `unwrap_or` preserves it, so an empty subagent error remained empty. Oops.

**Ambient behaviors.** Another recurring source was behavior JavaScript or Node supplied invisibly. Quota code used `toLocaleDateString`, which inherits the host time zone; Rust needed that zone passed explicitly. But although `Intl.DateTimeFormat().resolvedOptions().timeZone` is typed as `string`, it can return `undefined`, which napi couldn’t convert to a Rust `String`, breaking model-list loading. Elsewhere, moving an environment-variable read from before an `await` to after it meant a host change during the wait could alter the result. A native-addon loader called `process.report.getReport()` only to identify the platform, but on Windows that honored `_NT_SYMBOL_PATH` and could spend minutes downloading PDBs before rendering the CLI. Other ambient inputs included the working directory, repository identity, `PATH`, and session authentication. Moving ownership into Rust required deciding when to capture each one, how to carry it, and when to refresh it.

**Only porting half of a pair.** Several of the regressions came from paired operations falling out of sync. A turn-cap check updated the native registry’s abort state but didn’t cancel the in-process model loop, allowing one more request to escape. Elsewhere, task completion was persisted and emitted but not projected into active session state, so Autopilot continued after completing the task.

**Blocking the main thread.** The CLI still drives the Rust runtime from Node’s single-threaded event loop, so synchronous work across the napi boundary freezes the UI. `/chronicle reindex` parsed hundreds of session files this way, blocking rendering and input for nearly a minute. Making the export `async` and moving the work to a blocking thread-pool thread fixed it. An audit found several more potentially blocking entry points, leading to a standing rule: napi exports that do real work must be asynchronous and, when necessary, use `spawn_blocking`.

**Flashing windows.** On Windows, spawning a child process without `CREATE_NO_WINDOW` briefly opens a console window. The Node.js runtime had monkey patched process spawning to add the flag, hiding the requirement from the porting agent; the Rust replacement omitted it. An audit fixed two additional spawn sites, though those were preexisting omissions rather than port regressions, and we added the rule to the copilot instructions.

**Lifecycle management.** The largest cluster involved lifecycle, disposal, ownership, ordering, or races. Moving state into Rust often left TypeScript holding an opaque handle to an instance stored in a native table. Unlike an object reference, that handle can outlive the instance. One hook disposed mid-request orphaned a `tool_use` block, wedging the conversation because the model API required a matching result. A shell canceled between “announced” and “started” leaked an orphan that kept the session active. A sandbox toggle updated one generation counter but not its native twin, leaving the shell stuck “reconfiguring.”

**Overlooked features.** Another group involved features the port simply missed. One port omitted SDK callbacks and deleted their end-to-end test, prompting a new rule that agents must not change E2E tests without explicit consent. A session abort kept its native half but lost the in-process cancellation needed to interrupt a turn waiting in a tool. The SDK’s ability to replace built-in tool search depended on enablement, the model-facing description and schema, and routing execution to the SDK callback. The port dropped all three (yay for consistency?), silently replacing one consumer’s natural-language search with regular-expression search.

**Different libraries with different opinions.** Several more regressions came from replacing JavaScript libraries or APIs with stricter Rust equivalents. At the time, the Rust MCP SDK (`rmcp`) responded to malformed JSON-RPC input while the TypeScript SDK didn’t. Against a server that answered errors with more malformed output, that politeness became an infinite loop that hung startup. Related libraries in different ecosystems rarely behave identically.

**Ships passing in the night.** A handful of regressions came from branch drift or rebasing. In a repo with hundreds of pull requests per week, a session open for days accumulates constant changes and conflicts. The port required thousands of rebases; even a very high success rate leaves failures.

**And the slowpokes.** The last group was functionally correct but slower. Some regressions lost existing efficiencies such as memoization, fully asynchronous waiting, or bounded log streaming. Others added migration-specific overhead at the Rust-TypeScript boundary: redundant serialization, locking, polling, unbounded native concurrency, and native-to-host crossings. These weren’t later optimization opportunities; each was a degradation introduced by the port. One read-only scan deep-copied a 260 MB event log instead of borrowing it. Under sustained event traffic, another implementation completed only a tiny fraction of the requested channel flushes while retaining an asynchronous handle per event until V8 exhausted its heap.

Dozens of regressions sounds like a lot. But in a port that generated more than 800,000 lines of code, frankly I’m surprised and pleased we didn’t encounter an order of magnitude more. We can also get a sense for whether these were broadly “felt” by looking at trends in issues in the public [github/copilot-cli](https://github.com/github/copilot-cli) and [github/copilot-sdk](https://github.com/github/copilot-sdk) repos. Here we’ve classified issues opened from January through August as quality related when they carried a bug label or when their title used common failure terms such as “bug,” “regression,” “crash,” “hang,” “timeout,” “broken,” or “incorrect.” The levels were essentially unchanged from before the porting work as compared to during and after it:

| **Repository** | **Jan–Apr, before the rewrite** | **May–Aug, during / after the rewrite** |
| --- | --- | --- |
| [`github/copilot-cli`](https://github.com/github/copilot-cli) | 22.9% (454 / 1,982) | 23.7% (354 / 1,496) |
| [`github/copilot-sdk`](https://github.com/github/copilot-sdk) | 36.2% (190 / 525) | 32.3% (135 / 418) |

That’s not an availability metric or an exact count of escaped defects… as with the regressions themselves, there can be a ton of variability in what an issue represents, its scope, and so on. But it does provide a useful check: despite the extraordinary volume of product change, the product-facing issue channels didn’t show a meaningful quality concern spike during the migration.

## If it compiles, it compiles

Earlier I referred to a popular meme that Rust is great for AI-generated code because of its strict compiler. There’s another related and popular Rust meme, that if the code compiles, it’s correct. Nope. Our known regression list provides a good response to that: every regression in the corpus was merged to `main`, which means it successfully compiled. The compiler accepted the buggy versions because, as far as the compiler was concerned, every one of them was valid Rust.

The compiler can prove that an `f64` is used consistently. It cannot know that a repository ID must be serialized as an integer or that a timestamp serialized with a trailing `.0` will be rejected by every strongly-typed SDK at the other end of the wire. The compiler can prevent unsynchronized data races in the code it can see, but it can’t prevent a perfectly synchronized state machine from encoding the wrong states. A queue can be protected by locks and still let two senders each conclude the other will drain it. Events can move safely between threads and still arrive in the wrong order. A synchronous napi function can be memory-safe and still block Node’s main thread for a minute. Compilation is also almost by definition unable to detect something that isn’t there. The compiler can’t object when a rebase quietly removes a guard and its test, or when a process spawner forgot the Windows flag that suppresses console window pop-ups. It also had no opinion about cloning a 250 MB event log on every read. A compiler checks whether the program you wrote is internally coherent. It cannot check whether you wrote the whole program, preserved the old contract, called things in the right order, met the host’s unwritten requirements, or did the work at an acceptable cost.

That’s in no way an argument against Rust’s compiler. As with any statically-typed language, the compiler eliminated a huge class of mechanical mistakes and gave the agents an exceptionally useful inner loop. But “if it compiles, it’s correct” is useful only as a joke.

## Perf, perf, and more perf

So what did we buy with all of this? The port was deliberately behavior-preserving. It did not set out to redesign algorithms or fix bugs; in fact, I repeatedly pushed the agents away from opportunistic optimization because changing language and behavior at the same time makes it much harder to know which one broke you. A key goal of the rewrite, however, was indeed performance and scalability (in addition to reliability and other factors). When folks asked me why did I set out to rewrite the runtime in Rust, my answer was often along the lines of “I didn’t set out to move to Rust, I set out to move away from Node.js and V8”. That move has indeed significantly moved our needle on performance.

I benchmarked several scenarios for the runtime via the C# SDK before and after the port (the TypeScript, Python, Go, C#, Java, and Rust SDKs all reach the same engine through the same transport architecture). The baseline against a pre-port build of the SDK and CLI, with the TypeScript runtime hosted by Node and reached over stdio. The August 21 result uses the Rust runtime, both as an out-of-process server and loaded in-process through FFI. This is an end-to-end comparison of the delivered systems rather than an attempt to isolate the effect of the language change; other changes landed during the same period, so the numbers need to be taken with some grains of salt.

Each timed turn went to a deterministic chat completion server running on localhost, producing a fixed, small response. In other words, these numbers deliberately remove model inference and network latency. They measure the part we changed: client startup, process launch, session creation, event handling, persistence, teardown, etc.

| **Scenario** | **May 12** | **Aug 21 out-of-process** | **Aug 21 in-process** |
| --- | --- | --- | --- |
| Client, session, one turn | 5.25 s | 1.33 s (4.0x) | 292 ms (18.0x) |
| Resume 32-turn session | 5.64 s | 1.52 s (3.7x) | 264 ms (21.4x) |
| Ten concurrent client lifecycles | 12.34 s | 4.18 s (3.0x) | 742 ms (16.6x) |
| 1,000 one-turn session lifecycles | 132.52 s | 22.53 s (5.9x) | 20.93 s (6.3x) |

![From May 12 to August 21, creating a client and session, completing one turn, and tearing them down improved from 5.25 seconds to 55.3 milliseconds in-process, throughput rose from 7.55 to 120.0 sessions per second, and ten-client memory fell from 1,383 MB to 126 MB.](https://github.blog/wp-content/uploads/2026/09/performance-1.svg?resize=1024,452)

The “client, session, one-turn” number is the easiest one to feel. It’s creating a client, creating a session, doing a single turn, and tearing everything down. A large part of the out-of-process overhead came from starting Node, initializing V8, and loading, parsing, and generating bytecode for the JavaScript application produced from the TypeScript code before the first turn could begin. The Rust runtime removes those Node/V8 and JavaScript-loading costs.

The “1,000 one-turn session lifecycles” pressure test represents a step function change in what we can build. It uses a single shared client and measures running 100 concurrent pipelines, each creating a session, taking a complete model turn, disposing the session, and doing that ten times in sequence. The pre-port TypeScript CLI completed 7.55 of those lifecycles per second. Rust out-of-process completed 57.45. Rust in-process completed 120.0.

That is a workload-specific result; the Rust runtime is not universally “15.9x faster.” But it is exactly the workload server hosts care about: many independent sessions sharing one runtime. And wall-clock time isn’t hiding the work on another core. In a separate resource-sampling pass over the same 100-by-10 workload, the pre-port process tree consumed 312 seconds of aggregate CPU. The Rust configurations consumed about 110 seconds. That is CPU capacity the host can spend on more sessions.

Memory tells the same story, with the usual warning that memory metrics are annoyingly easy to misuse. Looking at resident private memory added during the ten-client batch, the pre-port process tree peaked 1,383 MB above baseline. Rust out-of-process peaked at only 247 MB. And Rust in-process at 126 MB, an order of magnitude less. While these numbers of course will differ from use to use and machine to machine, they get to the core goal: a service can host far more clients and sessions on the same machine before memory, process count, or CPU become the limiting resources.

The best part is this is the baseline port. Much of the implementation still consists of TypeScript-shaped algorithms faithfully rendered in Rust. We haven’t yet done the broad redesign work that the new ownership model, concurrency model, and in-process architecture enable. Getting an in-process client through creation, a complete one-turn session, and teardown in about 55 milliseconds, pushing a shared client to 120 one-turn session lifecycles per second, and cutting the measured ten-client memory delta by 91% before that optimization work begins is a very good place to start.

## What the port cost

So… how much did all this cost, monetarily? Drum roll, please….

My token spend for all of the porting work was ~136.3 billion total tokens, including ~130.6 billion cached input read tokens, ~4.2 billion cached input write tokens, ~900 million fresh input tokens, and ~600 million output tokens. The monetary bill for all those tokens came to ~$120,000.

Of course, these tokens weren’t spending themselves. The cost for using a significant amount of a developer’s time guiding these agents should also be factored in. However, I wasn’t devoting all of my time just to this project. Agentic development is a lot of “hurry up and wait;” submit a prompt, let the coding agent do its thing, check in on it from time to time to possibly steer it, but in the meantime until it completes, do other things. That means developers are no longer working on just one coding task at a time; they’re overlapping that waiting to enable working on many things concurrently. During the porting window, these Rust porting PRs represented ~20% of my PRs across all repos to which I was contributing. If we wave our hands and estimate that the share of pull requests approximates the share of my time, that works out to roughly three weeks dedicated to the port.

In other words, the rough bill for the porting effort was about $120,000 in attributed token spend plus three weeks of a developer’s time.

That said, the end-to-end Rust migration was not 100% a single developer; it’s been a team effort. [@stevesandersonms](https://github.com/stevesandersonms) provided the design and implementation for `napi-oop`, the temporary out-of-process interoperability layer, as well as five of the six SDK FFI implementations, with [@edburns](https://github.com/edburns) providing the sixth. [@roji](https://github.com/roji) implemented the packaging for the SDK to ship and utilize the Rust binaries correctly. [@caarlos0](https://github.com/caarlos0) has been helping to split the Rust code into many small subcrates in order to help with build times that started to become problematic, while [@criemen](https://github.com/criemen) helped with improved asset caching to speed up CI and local builds. [@devm33](https://github.com/devm33), [@examon](https://github.com/examon), [@MRayermannMSFT](https://github.com/MRayermannMSFT), [@dereklegenzoff](https://github.com/dereklegenzoff), and others have helped with countless pull request reviews and approvals. And everyone contributing to the copilot-agent-runtime repo has been supportive and accommodating as the world has shifted beneath their feet.

## Lessons learned

The experience reinforced a few lessons that apply beyond this rewrite. Here are some things we’d carry into a next time:

- **The goal needs to be clearly and fully stated.** Our early instructions were too vague. “Port XYZ component to Rust” got read as only the hot paths, or only the logic, and the agents repeatedly treated I/O and orchestration as being out of scope. Once we were explicit that the end state was a native binary built from a 100% Rust codebase, with no execution environment left for TypeScript even if we wanted one, they got much better at driving autonomously to that goal.
- **End-to-end tests are absolutely, unequivocally critical.** With one exception, all of the regressions that involved missing features, and many of the others, were due to lack of sufficient end-to-end tests. For any port of this nature, you must have tests that can be used to validate the correctness of the port, and those tests can’t themselves be re-written during the porting, or else you lose your oracle. Our initial plan for this porting effort called this out, and called out that we needed to significantly improve our E2E testing posture before starting on the port. We did that, but we didn’t do it enough. We’re sure that if we’d added even more E2E tests before starting the port, really focusing on ensuring most meaningful behaviors were covered by them, we would have had fewer regressions than we did along the way.
- **Protect the oracle from the agent.** The agent changing an implementation cannot also be allowed to silently redefine correctness by weakening a test, updating a snapshot, raising a compatibility baseline, or applying an escape-hatch label, at least not without oversight. Keep the behavioral contract independent where possible, put sensitive guardrails behind separate ownership or approval, and layer checks with different failure modes so that one mistake isn’t enough to ship a big regression.
- **Translate first, redesign second.** Preserving behavior and existing algorithms kept the number of variables moving at once manageable. Once the old implementation and the transitional scaffolding are gone, ownership, concurrency, and performance can be redesigned against a stable baseline. I veered away from this a few times, out of restlessness or difficulty saying no to a peer or a conviction that this case was different, and in hindsight I regret every one of them. Each cost more regressions, more time, or more tokens than staying the course would have.
- **Turn repeated failures into future successes.** AI agents will wander off track: when they do, learn from it. When a failure mode shows up twice, it belongs in standing instructions, a reusable skill, an eval, a protected baseline, or the harness itself.
- **The developer inner loop matters more with agents in the loop, not less.** For us as developers, we live and breathe in our inner loop, how quickly we can make a change, build, test, iterate. We get frustrated when the tooling part of that takes too long. And it’d be forgivable to think that doesn’t apply when agents are doing the lower-level work. It does. More so. AI agents do the thinking and writing code parts of these tasks incredibly quickly, but they still need to build and they still need to test. And the proportion of the time they build and test actually increases as they spend less time thinking and writing and more time in fast validation inner loops. Spend some time up front optimizing the inner loop, and optimizing it for multiple things happening at the same time (e.g. as if you’re working on multiple tasks in multiple worktrees at the same time). You will thank yourself later for the investment.

## What’s next?

It worked. The execution runtime that was entirely TypeScript in May is now entirely Rust in August, and it shipped to real users continuously the whole way rather than landing as one terrifying cutover at the end.

I didn’t simply ask an AI agent to “port this whole codebase from TypeScript to Rust.” Even if that’s where we’re heading as an industry, we’re definitely not there yet. Instead, agents made an entire category of project feasible. A rewrite producing hundreds of thousands of lines of production Rust in a live system, done in place, in `main`, by one engineer supported by a team, is not a proposal that would have been accepted before agents. It would have needed a whole team and a year or two, it would have competed against every feature that team could have shipped instead, and it would have lost (and, honestly, should have lost). Agents moved the price to where the project became tenable.

The port itself is complete: the runtime’s production implementation is 100% Rust, and the temporary internal TypeScript/N-API seam is gone. There is still plenty of work we desire to do, though: further improving the build system and developer inner loop, cleaning up translated structures, redesigning around Rust’s ownership and concurrency models, and pursuing further performance wins. The port was a translation, deliberately so (prompted as such), and we kept behavior as close to 100% identical as possible, rather than opportunistically fixing additional bugs, rearchitecting components, or further improving performance and scalability (beyond what came implicitly from the rewrite). Much of the code at the micro level is idiomatic Rust, but at the macro level there’s a good deal of initially TypeScript algorithms wearing Rust’s syntax. Revisiting those decisions now that the constraints underneath them have changed is where the interesting wins will be.

I’m most excited about what the port makes possible. The SDK can be loaded directly into a host process in any of six languages, with no Node.js or V8 in the dependency chain, and no second process to supervise, which is the single most common piece of friction we’ve heard from partners adopting the SDK. A runtime instance that costs a fraction of what it used to means a host can run far more concurrent sessions before it runs out of machine. And the runtime can now go places Node.js was never going to follow it, across the spectrum of cloud to desktop to device to embedded systems. None of that is the finish line. It’s the base we now get to build the future of GitHub Copilot on top of, and after three months of watching agents rewrite the very engine that runs them, I’m looking forward to seeing just how far it goes.

Happy coding!

## Written by

![Stephen Toub](https://avatars.githubusercontent.com/u/2642209?v=4&s=200)

### [Stephen Toub](https://github.blog/author/stephentoub/)

[@stephentoub](https://github.com/stephentoub)

Stephen Toub is a Distinguished Engineer at Microsoft.
