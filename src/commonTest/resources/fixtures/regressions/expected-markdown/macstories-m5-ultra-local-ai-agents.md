![The M5 Ultra Mac Studio.](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789994617403-abb772eab6.png)

The M5 Ultra Mac Studio.

For the past few days, I’ve been testing the ([currently](https://forums.macrumors.com/threads/mac-studio-with-m5-ultra-chip-and-512gb-of-ram-launching-in-october.2487936/)) top-of-the-line **M5 Ultra Mac Studio with 256 GB of RAM**.

I’ll cut to the chase: the M5 Ultra Mac Studio is a dream machine for local AI agents. This computer makes it possible to run personal assistants powered by local models with great performance and no additional cloud costs. If you’ve been skeptical of testing [OpenClaw](https://openclaw.ai/) or [Hermes Agent](https://hermes-agent.nousresearch.com/) with local models because they’d never be even remotely *near* the intelligence and speed of cloud ones, this Mac will change your mind about that.

Since last Thursday, I’ve been comparing this Mac Studio to its predecessor, the [M3 Ultra with 512 GB of RAM](https://www.macstories.net/notes/testing-deepseek-r1-0528-on-the-m3-ultra-mac-studio-and-installing-local-gguf-models-with-ollama-on-macos/), as well as my own desktop gaming PC with an RTX 5090 inside. For its size, price, thermal performance – not to mention Apple’s approach to unified memory – the M5 Ultra Mac Studio has fundamentally changed how I think about models running locally and what they can enable now. A 5090, of course, still has an edge over the M5 Ultra thanks to its [higher memory bandwidth](https://www.nvidia.com/en-us/geforce/graphics-cards/50-series/rtx-5090/). But considering the sheer size of my PC build, as well as its heat and noise, I would prefer an M5 Ultra Mac Studio any day. It also happens to be a Mac, with an operating system that looks nice and doesn’t suck, plus a vibrant app ecosystem. (Windows fans, *I’m sorry*, but Microsoft software will never get my sympathy.)

### Supported By

## Astropad Workbench

![](https://cdn.macstories.net/images/uploads/2026/09/19/workbench-original-1789826192842-d4ab3c3d8f.png)

Remote desktop for AI agents and headless Mac minis. Free for 20 mins/day.

As I’ll explore in this article, running the latest [Qwen3.8-Flash-Next model](https://qwen.ai/blog?id=qwen3.8-flash-next) on the M5 Ultra Mac Studio has been so *nice* and fast, I’ve made it my default in both [Open Minis for iOS](https://openminis.app/) and Hermes Agent. That’s right: the personal assistants I use the most – [more than Siri AI](https://www.macstories.net/reviews/open-minis-is-the-ios-agent-i-wish-siri-ai-could-be/), in fact – are now entirely powered by a model running locally on a Mac Studio. Furthermore, thanks to the M5 Ultra’s faster GPU and higher memory bandwidth, these agents start responding more quickly, stay fast at larger context windows, and can run long, multi-turn loops without slowing to a crawl as the session grows. Because of this, I’ve also been using local models in the Codex app on my Mac – either as main threads or subagents orchestrated by GPT-6 Astra – and I’ve had a great experience doing so.

> The personal assistants I use the most are now entirely powered by a model running locally on a Mac Studio.

![Local subagents running in Codex on the M5 Ultra Mac Studio.](https://cdn.macstories.net/images/uploads/2026/09/21/cleanshot-2026-09-21-at-115112-am2x-1789984316312-7cd018d228.png)

Local subagents running in Codex on the M5 Ultra Mac Studio.

I should note upfront that **I’m not an AI developer** by trade: I do not train or fine-tune models. I’m **a tinkerer** at heart, and I’ve been playing around with local AI models [for over a year at this point](https://www.macstories.net/notes/notes-on-early-mac-studio-ai-benchmarks-with-qwen3-235b-a22b-and-qwen2-5-vl-72b/). This summer, I went all-in on local AI usage for a big project I was working on, which I will explain in the following section.

My goal with this article is to provide you with a mix of two things: numbers and visualizations based on the (many) tests I’ve run over the course of four days, and an explanation of my practical use cases for local AI applied to my workflow and how I get things done for MacStories.

Let’s dive in.

### Why Local AI?

Let’s address the elephant in the room first: why bother with local AI at all when cloud frontier models are better and often faster?

It’s a fair question. You need expensive hardware to run these models, and by the time you’ve repaid your investment, you could have used the most expensive Anthropic subscription for several years, still saved money, and got better performance in return.

Different people will have different answers to this question. Some might say they use local models because of privacy: they’d rather rely on local intelligence for sensitive data and documents than upload anything to an external cloud. Others might argue that it’s simply *cool* – and I do not disagree. For some, it’s a work-related task: if you’re an AI developer, it makes sense to have a great local setup for training your own adapters or fine-tuning models.

For me, the journey into local AI has been characterized by a mix of the “ *cool, why not?* ” factor of it all as well as considerations about privacy and costs.

As I will share later this week with [Club MacStories members](https://www.macstories.net/plans/), my research and writing setup for the [iOS and iPadOS 27 review](https://www.macstories.net/stories/ios-and-ipados-27-review/) this summer has been powered and made possible by local AI. Back in June, I created an internal app, called **Desk**, to organize hundreds of notes, sessions, PDF documents, and clipped webpages related to iOS and iPadOS 27, as well as chapters of the review. By the end of the process, the project consisted of 310 documents. In Desk, a team of agents – all based on [DeepSeek V4 Flash](https://huggingface.co/deepseek-ai/DeepSeek-V4-Flash), plus [olmOCR](https://github.com/allenai/olmocr/tree/main) for PDFs – ran 24/7, for 99 days, to perform the following tasks:

- Transcribe my favorite WWDC sessions (using [`summarize`](https://github.com/steipete/summarize) plus LLM processing)
- Extract features of iOS and iPadOS 27 from clipped webpages, sessions, PDF guides, and my own notes
- Cross-reference features across different sources, and keep track of which features belonged to which chapter of the review
- Extract features and bugs from screenshots I uploaded
- Work with the Notion API to organize everything across multiple databases

![One of the views of Desk, the app powered by the Notion API and local AI I used for my iOS 27 review.](https://cdn.macstories.net/images/uploads/2026/09/21/cleanshot-2026-09-14-at-92634-am2x-1789977211491-1b4f85d442.png)

One of the views of Desk, the app powered by the Notion API and local AI I used for my iOS 27 review.

![The local AI agent runs in my custom Desk app.](https://cdn.macstories.net/images/uploads/2026/09/21/cleanshot-2026-09-14-at-92409-am2x-1789977221599-40da9a6f83.png)

The local AI agent runs in my custom Desk app.

When I started working with this setup in early June, I quickly realized that relying on the OpenAI or Anthropic APIs for this kind of always-on, persistent background task would be…cost-prohibitive, to say the least. So I pivoted to local AI, and the result is the [iOS and iPadOS 27 review you can read on MacStories](https://www.macstories.net/stories/ios-and-ipados-27-review/). It was all written by me, the old-fashioned human way. But the entire research stack, deep-linking between notes, and keeping track of new features and betas were all performed by my agents, running locally on the Mac Studio, for a total cost of $0.

If you don’t think that’s neat, or a powerful concept to explore, then this article probably isn’t for you – and I understand. Dealing with these models is fiddly, and it’s not something I would ever recommend to someone who (rightfully) just wants to pay $20 to use Claude Cowork. This kind of setup is, by definition, the bleeding edge of AI workflows at the moment.

If you fall on the other end of the spectrum, though, and if you think this kind of stuff is neat…let me tell you: the M5 Ultra Mac Studio is a massive leap in performance for local models powered by [MLX](https://github.com/ml-explore/mlx), and I have a few examples to prove it.

### A Leap for Prompt Processing and Generation

As you may have seen from the [announcement](https://www.apple.com/newsroom/2026/08/apple-introduces-m6-and-m5-ultra-for-a-big-leap-in-performance-and-ai-compute/) and [my initial coverage](https://www.macstories.net/notes/the-potential-of-m6-and-m5-ultra-for-local-ai-on-macos/), the M5 Ultra Mac Studio looks identical to the M3 Ultra model it replaces, but it comes with an all-new Apple silicon architecture that uses UltraFusion to connect two dual-die M5 Max chips to form a quad-die architecture, which is a first for the Apple ecosystem. As far as local AI workloads are concerned, there are two areas we have to pay attention to (and which I have been following since my coverage of the [M5 iPad Pro for local AI](https://www.macstories.net/stories/ipad-pro-m5-neural-benchmarks-mlx/) last year): GPU and memory bandwidth.

The M5 Ultra has a next-gen GPU with 80 cores, each with a Neural Accelerator that grants it up to 4.5× the peak GPU compute for AI compared to the M3 Ultra. As for memory, Apple’s unified memory architecture still tops out at 512 GB as before (although that model will come out in late October), but its bandwidth has jumped from 819 GB/s to 1.2 TB/s, or 50% higher than the M3 Ultra.

With these numbers in mind, I started testing the M5 Ultra against the M3 Ultra with 512 GB of RAM and my RTX 5090. I’ll share more details on testing below, but the short version is this: with the M5 Ultra, you spend considerably less time waiting for a model to read your prompt and begin generating a response; and when it does start answering, text appears much faster than it used to on the M3 Ultra. These two improvements alone make the machine viable for modern agentic loops that require fast iteration with a model and, as a result, larger context windows.

![](https://cdn.macstories.net/images/uploads/2026/09/21/studios-01-flash-time-to-first-token-light-2400x1350-1789985471080-9a9b138dfa.png)

![](https://cdn.macstories.net/images/uploads/2026/09/21/studios-03-flash-generation-speed-light-2400x1350-1789985474159-2828e5ffe2.png)

In my day-to-day experience with agents running on the M5 Ultra, these improvements to token prefill (or how quickly a prompt can be processed) and token generation are the changes I noticed immediately. When comparing a model running on the M3 Ultra and M5 Ultra side by side with Open Minis on iOS, the M5 Ultra was **~70% faster on average than the M3 Ultra** at generating a response. As we’ll see later, having a model such as Qwen3.8-Flash-Next clear 100 tokens/second on short prompts and still write at 60 to 85 with 64K to 256K of context behind it is no joke, and it enables the kind of agentic back-and-forth between you and the model that feels *great* to use, particularly when tool calls are involved.

![Using a local model as my default in Open Minis for iOS. Pictured above: a long-running project, subagents, and local image generation powered by Qwen-Image-2.1, also running on the M5 Ultra.](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789984709908-a88cd13994.png)

Using a local model as my default in Open Minis for iOS. Pictured above: a long-running project, subagents, and local image generation powered by Qwen-Image-2.1, also running on the M5 Ultra.

However, I was more impressed with the performance gains in token prefill. When you use agentic assistants such as Hermes or Codex, a model receives a whole block of instructions that include things like the system prompt, user personalization and session memories, skill and MCP descriptions, and more. Some agents are better than others at trimming the instructions they send, but, generally, whenever you use a modern agent, you’re not starting with an empty context window. Because of this, I’ve never been able to consistently use local models with this new wave of agents: they would work, but I’d stare at an empty screen and a loading indicator for a while before the model would start generating a response. And on every turn of the loop, performance would get worse (because of the larger context of the session), and I’d wait some more time.

In my tests, **prompt processing is up 150% on average** from the M3 Ultra – a ~2.5× improvement from my previous setup. This change alone makes local models solid choices in apps like Open Minis and Hermes Agent. When I ask Flash-Next on the M5 Ultra to get my tasks for the week with [RemCTL](https://github.com/viticci/remctl), I don’t have to wait around for the agent to process my prompt and Open Minis’: in just a few seconds, it gets to work by reasoning, performing tool calls, and so forth. And when I’m working on a large project, such as the voxel Colosseum demo below, the model is able to process multi-turn loops quickly, dispatch and coordinate subagents, and do it all at 60 to 85 tokens per second as the thread grows longer.

![This interactive Colosseum demo was entirely created by Flash-Next running on the M5 Ultra Mac Studio, managed via Open Minis and its harness on iOS.](https://cdn.macstories.net/images/uploads/2026/09/19/shortcut-upload-1789785128288-a93d697eba.png)

This interactive Colosseum demo was entirely created by Flash-Next running on the M5 Ultra Mac Studio, managed via Open Minis and its harness on iOS.

To use local models from the Mac Studio in Open Minis for iOS, I created a local server in front of the OpenAI-compatible API exposed locally by [oMLX](https://github.com/jundot/omlx). It’s served via Tailscale to my iPhone and works wonderfully.

<svg viewbox="0 0 120 120" xmlns="http://www.w3.org/2000/svg"><circle clip-rule="evenodd" cx="60" cy="60" fill-rule="evenodd" r="60"></circle><path d="m44 89v-5h13.2v-28.6h-13.1v-5h19.1v33.6h12.8v5zm10.8-53.1c0-2.8 2.2-4.9 5-4.9s5 2.1 5 4.9-2.2 5-5 5-5-2.1-5-5z" fill="#fff"></path></svg>

I’m a big believer in assistants that can agentically perform tasks in addition to answering questions, but in order to feel nice to use, they have to be fast. Over the past few months, I’ve tested several “boutique” cloud providers with Open Minis: [Inco](https://inco.ai/blog/inco-platform-aa/), which serves Kimi K3 at over 300 TPS; [Cerebras](https://inference-docs.cerebras.ai/models/qwen-3.8-27b), with Qwen3.8-27B at a whopping 1,800 TPS; and the likes of [Fireworks](https://fireworks.ai/) and [Baseten](https://www.baseten.co/), each breaking the 150 TPS barrier. All of those providers feel extremely good to use in Open Minis and Hermes, but they are expensive (I burned through $20 of Inco credits in literally 10 minutes last week), and, of course, all my data is going…*somewhere* when I use them. When I fire up Open Minis with Flash-Next and the collection of Apple CLIs I’m creating, everything stays local, inside a computer I can see and reboot whenever I want.

![Cadu, an \[upcoming iOS client for Hermes Agent\](https://cadu.bot/), running live voice mode with \[Qwen3-TTS\](https://qwen.ai/blog?id=qwen3tts-0115) and Qwen3.8-Flash-Next as the underlying chat model, with the M5 Ultra as the server.](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789984129391-f27b218248.png)

Cadu, an [upcoming iOS client for Hermes Agent](https://cadu.bot/), running live voice mode with [Qwen3-TTS](https://qwen.ai/blog?id=qwen3tts-0115) and Qwen3.8-Flash-Next as the underlying chat model, with the M5 Ultra as the server.

Most importantly: a model like Flash-Next can be “small” enough to run at higher [quantizations](https://en.wikipedia.org/wiki/Large_language_model#Quantization) on a 256 GB M5 Ultra (I can run 5-bit entirely in RAM; 6- and 8-bit can offload their [n-gram tables](https://huggingface.co/blog/Blackroot/what-the-engram) to SSD with [this new architecture](https://qwen.ai/blog?id=qwen3.8-flash-next)) but also intelligent enough to sustain long threads and multiple agentic tool calls.

![Text generation at different quants.](https://cdn.macstories.net/images/uploads/2026/09/21/quant-02-prose-generation-speed-light-2400x1350-1789985592098-e0cb8ee032.png)

Text generation at different quants.

For my taste, 5-bit quantization hits the sweet spot on this version of the Ultra with a balance of intelligence, performance, and memory consumption. But I already know that, if I ever get to test a 512 GB M5 Ultra, I’d be *really* interested to measure performance of the 8-bit quant without SSD offloading.

I have not spent much time tinkering with offloading coding tasks for my various projects to a local model, but I’ve done a few interesting experiments. With this kind of performance, and especially given the ability to stack up to three concurrent Flash-Next sessions with subagents in [oMLX](https://github.com/jundot/omlx) with 256 GB of RAM (more later), I can now realistically consider handing off simpler coding tasks to a local model and have frontier cloud ones review their work. For instance, I was able to set up Qwen3.8-Flash-Next in Codex, which lets me use a local model with the Codex harness. This means that I can let a main GPT model orchestrate local subagents, have Flash-Next coordinate its own subagents, or even just use the model from my phone with Codex Remote on iOS.

![Using a local model on the M5 Ultra from Codex Remote.](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789985181537-e337f14264.png)

Using a local model on the M5 Ultra from Codex Remote.

![I do not usually rely on image generation, but for the sake of this review: the M5 Ultra chip is an official Apple asset; the wallpaper behind it was generated by Qwen-Image-2.1 locally on the M5 Ultra in 180 seconds, with peak RAM usage of 78 GB.](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789985709731-5a2b0dadf8.png)

I do not usually rely on image generation, but for the sake of this review: the M5 Ultra chip is an official Apple asset; the wallpaper behind it was generated by Qwen-Image-2.1 locally on the M5 Ultra in 180 seconds, with peak RAM usage of 78 GB.

I’m curious to read more on this topic from actual developers who are getting an M5 Ultra soon. With open-weights models now outperforming on *consumer hardware* what was considered “frontier” ~10 months ago, and with performance on an M5 Ultra now making agentic coding feasible, I think we’re going to see some fascinating experiments from the MLX community very soon.

### M5 Ultra vs. RTX 5090

As you’ll see from the visualizations later in this article, NVIDIA’s RTX 5090 is still faster than Apple’s M5 Ultra despite its “meager” 32 GB of VRAM, for two different reasons.

Prompt processing speeds are dictated by compute: the model reads the whole prompt in one giant [matrix multiplication](https://en.wikipedia.org/wiki/Matrix_multiplication), which is exactly the job [NVIDIA’s Tensor Cores](https://www.nvidia.com/en-us/data-center/tensor-cores/) were built for. Apple’s new Neural Accelerators (one in each of the M5 Ultra’s 80 GPU cores) narrow the gap, but can’t close it. On a 6,000-token prompt, the M5 Ultra read at ~1,700 tok/s; the 5090 delivered a staggering ~3,000 with the Qwen model I tested in LM Studio. Token generation, on the other hand, is bandwidth: the model writes one token at a time and pulls the entire model back out of memory for each one, so the 5090’s 1.79 TB/s against the M5 Ultra’s 1.2 TB/s gives it a steady ~25% lead at every prompt size. What the 5090 doesn’t have is memory: at 256K, the 5090 only finishes with an 8-bit attention cache. 32 GB of VRAM only goes so far.

There are, however, two problems with this comparison. First, while the 5090 does still edge out the M5 Ultra with smaller models, its lack of a unified memory pool means that I’m limited to the 32 GB of VRAM in the GPU if I want to run a model at blazing-fast speeds. The moment I want to run anything exceeding 32 GB (such as the aforementioned higher Flash-Next quants), the 5090 must offload model layers over PCIe to (much slower) system RAM, and that’s no way to live.

![I tested a different Qwen model for the comparisons between Mac and PC.](https://cdn.macstories.net/images/uploads/2026/09/21/pc-02-generation-speed-mac-vs-pc-light-2400x1350-1789985589719-63395429c0.png)

I tested a different Qwen model for the comparisons between Mac and PC.

Second, my gaming PC is massive compared to a Mac Studio that fits on my desk – and I have a [compact build](https://uk.pcpartpicker.com/list/Yzpzh9) with a Lian-Li A3 case. Not to mention how loud and hot it gets when I’m running local models at high context windows: when I walked into my office after some benchmarks had run, it was uncomfortably warmer compared to the rest of my apartment. By contrast, the “diminutive” Mac Studio on my desk was warm to the touch, but it was also appreciably quieter than my 5090, the fans were not spinning as fast or loudly, and, most important, it allowed me to run larger models such as GLM-5.3-Flash locally with decent performance thanks to Apple silicon’s unified memory. In my day-to-day use, when I was running [Flash-Next oQ4e](https://huggingface.co/Jundot/Qwen3.8-Flash-Next-oQ4e-mtp) all the time, I could never hear the fan of the Studio on my desk unless I placed my ear directly on top of the computer.

Judging by the progress Apple has made in recent years, I wouldn’t be surprised to see an that outperforms the memory bandwidth of a 5090 in the near future. But that’s a story for another time.

### A Note on Testing

Lastly, before we jump into raw numbers and charts: how did I test everything?

Automated tests were conducted with a testing harness I built with GPT-6 Astra, which coordinated multiple instances of Codex across my M3 Ultra and M5 Ultra Mac Studio, as well as my PC with the Codex app for Windows and Computer Use. On macOS, I chose oMLX (version 0.7.0.dev2) as the local backend for MLX models, and ran [Qwen3.8-Flash-Next-oQ4e-mtp](https://huggingface.co/Jundot/Qwen3.8-Flash-Next-oQ4e-mtp/tree/2615fc0e976e65c2f3b55daca3a948f1cdc5b9f8), [GLM-5.3-Flash-MLX-mixed-4\_8bit](https://huggingface.co/pipenetwork/GLM-5.3-Flash-MLX-mixed-4_8bit/tree/d43ea8b407ce4e9c25e6ac9baec3feab70d9f5f3), and [Qwen3.8-27B-oQ4e-mtp](https://huggingface.co/Jundot/Qwen3.8-27B-oQ4e-mtp/tree/04dc5509edd8670fc78cc8c2f74bf9b77b1f2acc) on macOS Golden Gate 27.0 for the majority of my tests. On Windows, I used LM Studio and [Qwen3.8-27B-GGUF](https://huggingface.co/lmstudio-community/Qwen3.8-27B-GGUF/blob/5a7da681f60570ab5b439a587e912d2e5eddb582/Qwen3.8-27B-Q4_K_M.gguf) with CUDA 12 runtime and with all 66 layers offloaded to the GPU for the full-GPU tests, plus separate tests splitting the model between GPU and system RAM.

Alongside separate experiments with Open Minis’ native subagents, I used a custom testing harness to measure concurrent requests and workflows involving a lead model and multiple helpers, with oMLX serving the Mac models and LM Studio serving the Windows model.

Numbers were collected by Astra over the course of four days, and later visualized by Claude Fable 5.1 and Opus 5 using [Anthropic’s upcoming Projects feature](https://claude.com/blog/projects-redesigned), which I was able to test early when working on this story. The interactive visualization was built with pure HTML and CSS based on MacStories’ style, and it includes comments and annotations by yours truly.

![Claude’s upcoming Projects feature.](https://cdn.macstories.net/images/uploads/2026/09/21/cleanshot-2026-09-21-at-105608-am2x-1789981016357-603c7f78d7.png)

Claude’s upcoming Projects feature.

My goal with the following interactive widgets was not only to help you understand the numbers more clearly, but also to visualize what the stats mean in practice. I’m quite happy with the widgets that approximate what different tokens per second feel like, since that’s a metric that’s often tricky to visualize. I hope these animated charts will be more useful than regular “static” ones you’ve probably seen elsewhere (which are also included below).

### Visualizing the M5 Ultra

## Flash-Next and GLM-5.3 on Two Mac Studios

Let’s start with the comparison I care about the most: the M5 Ultra against the M3 Ultra. In these tests, I used the same models, prompts, and oMLX build. The only difference: the M5 Ultra Apple sent me has “only” 256 GB of RAM.

[View interactive chart: Two Models at Different Prompt Sizes](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#two-models-at-different-prompt-sizes) — Choose model and prompt size to compare results.

[View interactive chart: Tokens per Second](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#tokens-per-second) — Tokens per second once the model starts generating a response. Higher is better.

[View interactive chart: Time to First Token (TTFT)](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#time-to-first-token-ttft) — TTFT measured with 64K, 128K, and 256K prompts, cold cache. Tested the same prompt again, but cache warm.

[View interactive chart: Does a Bigger Context Slow It Down?](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#does-a-bigger-context-slow-it-down) — Flash-Next writing the same 512-token answer after 4K to 256K tokens of background text. Tokens per second; higher is better.

[View interactive chart: Watch Them Write](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#watch-them-write) — A simulation of what token-per-second numbers from above feel like.

## Flash-Next at Four Quantizations

While I focused on the 4-bit oQ4e build of Flash-Next for the majority of tests in this review, I also put it against the 5-, 6- and 8-bit builds on both Mac Studios. All four ran in a separate session, with three runs each, so the 4-bit numbers here differ a little from the figures above. More bits means a bigger (and more precise) model. The M3 Ultra Mac Studio with 512 GB of RAM holds all four in memory. The M5 Ultra has 256 GB: oQ4e and oQ5e fit, and oQ6e and oQ8e only run with their embedding tables offloaded to SSD, which is how they appear in every figure below.

[View interactive chart: How Much Memory Each Quant Takes](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#how-much-memory-each-quant-takes) — Peak memory of the oMLX process while answering, with one model loaded. The scale represents the M5 Ultra’s 256 GB.

[View interactive chart: Does More Precision Cost Speed?](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#does-more-precision-cost-speed) — Tokens per second once the model starts writing. Pick prose or code. Higher is better.

[View interactive chart: Reading a 256K Prompt at Four Precisions](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#reading-a-256k-prompt-at-four-precisions) — Time to the first token after a 256K-token prompt, cold cache. Every quant, both Macs, one clock.

## Charts

I’ve also put together some classic line charts, drawn from the numbers measured in these tests. Click any one of them to open it.

[View interactive chart: The Numbers, Plotted](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#the-numbers-plotted) — Speed, latency and total time as the prompt grows. Click a chart to open it full width.

[View interactive chart: Four Quants, Plotted](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#four-quants-plotted) — The quantization test as plain charts. Click one to open it.

## M5 Ultra vs. RTX 5090

The following tests are based on my gaming PC build: an RTX 5090 with 32 GB of VRAM, 96 GB of system RAM, and the same Qwen3.8 27B running in LM Studio on Windows. For these tests, I used a different model than the one from my Mac comparisons above.

[View interactive chart: Enter the PC](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#enter-the-pc) — Qwen3.8 27B on my 5090, the M5 Ultra, and the M3 Ultra, answering the same prompt. Pick a size.

[View interactive chart: TPS Across Mac and PC](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#tps-across-mac-and-pc) — The 6,091-token prompt from the tests above: how fast each machine reads it, how fast it writes, and how long you wait.

[View interactive chart: TPS at Long Contexts](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#tps-at-long-contexts) — Qwen3.8 27B writing after 64K, 128K, and 256K tokens of context, on both Studios and the PC. Tokens per second; higher is better.

[View interactive chart: Watch All Three Write](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#watch-all-three-write) — The answer to the 6,091-token prompt on each machine, chunk by chunk, in real time.

## Subagents and Concurrency

This is the part I was most curious about. Here’s what happens when three requests arrive at once and when a lead model hands work to helpers.

[View interactive chart: Three Requests at Once](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#three-requests-at-once) — One, two, or three simultaneous Flash-Next requests on the two Studios, about 6.5K tokens each. How long until every answer is done?

[View interactive chart: A Lead and Three Helpers](https://www.macstories.net/stories/m5-ultra-mac-studio-review-the-dream-mac-for-local-ai-agents/#a-lead-and-three-helpers) — On the PC, a lead model splits a ledger three ways, the subagents get to work, and the lead combines their replies.

### The M5 Ultra for Local AI Agents

![](https://cdn.macstories.net/images/uploads/2026/09/21/shortcut-upload-1789994673319-8e51bf172f.png)

As should be clear at this point, the performance gains of the M5 Ultra are real, and they show how Apple’s investment in custom silicon and its unified memory architecture is paying dividends for tinkerers and developers.

Despite my tests, I feel like I’ve barely scratched the surface of what’s possible with the M5 Ultra and its 256 GB of RAM. As more developers and open-source maintainers get their hands (and agents) on the M5 Ultra, I’m sure we’ll see more optimizations in quantization to allow even larger models to run with superior performance on this computer. For instance, I didn’t even have time to test [DwarfStar](https://github.com/antirez/ds4) – a fascinating project (made in Italy!) that is making it possible to run local frontier models on all kinds of Mac configurations with even less memory; nor did I have time to check out [Inco Splash](https://inco.ai/blog/splash/), a new inference engine designed for Apple silicon and specific models. Likewise, I didn’t have time to test [Exo](https://exolabs.net/), whose [RDMA](https://developer.apple.com/documentation/technotes/tn3205-low-latency-communication-with-rdma-over-thunderbolt) implementation should (in theory) allow me to split and distribute inference across M3 Ultra and M5 Ultra via Thunderbolt 5, all while running an OpenAI-compatible server in front of it to serve an API for local agents.

And, of course, I can’t even begin to imagine what the high-end M5 Ultra with 512 GB of RAM will allow in terms of scaling up models capable of running locally. I hope to be able to test it eventually, too.

At the end of this experiment, I have a simple, tangible result: the M5 Ultra lets me run local agents with incredible performance, with less time spent staring at a blank screen and everything happening on a single, compact, cool, and quiet machine on my desk.

This would have seemed impossible a couple of years ago. But here we are.

Supported by: Remote desktop for AI agents and headless Mac minis. Free for 20 mins/day.

![Club MacStories](https://www.macstories.net/wp-content/themes/macstories4/images/logo-shape-gold.svg)

[Join](https://www.macstories.net/club/?utm_source=ms&utm_medium=web)

### Access Extra Content and Perks

Founded in 2015, [Club MacStories](https://www.macstories.net/plans?utm_source=ms&utm_medium=web-inline) has delivered exclusive content every week for nearly a decade.

What started with weekly and monthly email newsletters has blossomed into [a family of memberships](https://www.macstories.net/plans?utm_source=ms&utm_medium=web-inline) designed for every MacStories fan.

Learn more [here](https://www.macstories.net/plans?utm_source=ms&utm_medium=web-inline) and from our [Club FAQs](https://www.macstories.net/club-faq).

**[Club MacStories](https://www.macstories.net/plans/club)**: Weekly and monthly newsletters via email and the web that are brimming with apps, tips, automation workflows, longform writing, early access to the [MacStories Unwind podcast](https://www.macstories.net/unwind/), periodic giveaways, and more;

**[Club MacStories+](https://www.macstories.net/plans/plus)**: Everything that Club MacStories offers, plus an active Discord community, advanced search and custom RSS features for exploring the Club’s entire back catalog, bonus columns, and dozens of app discounts;

**[Club Premier](https://www.macstories.net/plans/premier)**: All of the above *and* AppStories+, an extended version of our flagship podcast that’s delivered early, ad-free, and in high-bitrate audio.

Learn more [here](https://www.macstories.net/plans?utm_source=ms&utm_medium=web-inline) and from our [Club FAQs](https://www.macstories.net/club-faq).
