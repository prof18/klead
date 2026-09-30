![Article cover image](https://pbs.twimg.com/media/HQIPklhbkAAQ4s-?format=webp&name=large)

The amount of code being written today far outpaces our ability to review it. That part isn't controversial. What's more interesting is that we're visibly losing — the amount of AI slop in modern codebases right now is insane, and AI coding is obviously not going away. So which is it? Do we just embrace the slop, or is there a way to have our cake and eat it too?

I think there's a way out. But it requires giving up something most of us aren't ready to give up yet. Pick your lane:

![](https://pbs.twimg.com/media/HQIGpEPawAAkVEO.jpg)

*https://x.com/dzhng/status/2089763660260651318*

### Slop is not a model quality problem

Maybe in the days of GPT-4 and Sonnet-3 that is true, but it is no longer true today. SOTA models have more or less mastered writing code (note I'm drawing an explicit line between coding and engineering here, more on that later). Slop is what happens when generation is unbounded and verification is bottlenecked on a human reading the output.

Think about the loop. Code gets generated faster every quarter. The review step runs at a fixed human rate. When supply massively exceeds inspection capacity, inspection stops being a filter and becomes a formality. You skim. You approve. LGTM becomes the default, quality drifts toward whatever passes a skim. Anyone who has worked in a large team today has seen this.

****If the goal is still to read every line, we are bottlenecked by our own ability to review, and quality degrades toward zero.**** That degradation has a name and we're all already using it.

So the two obvious moves are both losers. Embrace the slop and you've accepted the degradation. Slow down generation to match your reading speed and you've thrown away the entire advantage. The only move left is to stop making human reading the bottleneck.

### It gets worse: the code is going to stop being readable

Right now we're in an awkward transition phase. AI still writes human-readable code: TypeScript, Python, things with names in them, mostly because it learned from us and we're still in the loop.

![](https://pbs.twimg.com/media/HQIROiMbQAAEUf_.jpg)

I don't think that lasts. I could easily see code getting less and less readable over time. Maybe it's written in Claude-speak. Maybe we invent new languages purpose-built for AI to write - far more token-efficient, optimized for a reader that isn't human. Maybe it just directly emits machine code.

The standard fix for too-much-code-to-read is **more AI to help us read the code**. But those reviewers are pinned to the artifact, and the artifact is exactly the thing going opaque. Every improvement in code generation makes their reviews less relevant and less helpful.

### I'm still extremely bullish on software engineering

As AI-pilled as I am, I still think software engineers will be around for as long as we have computers.

The reason is that the job was never writing code. Engineering is about solving problems. That's the actual difference between being a programmer and being an engineer - a programmer's output is code, an engineer's output is a solved problem, and code was only ever the medium we happened to solve it in.

If you believe that, then "the AI writes the code now" isn't an attack on the profession. It's a change of medium. The future of software engineering is figuring out the ****system**** by which you work with AI to solve business problems - what gets specified, what gets delegated, what gets verified, and how you know when to trust it.

That system is the deliverable now. Which brings us to the real problem: ****The entire SDLC assumes you can read the code****

Every process we have is downstream of that assumption. Pull requests. Code review. Approvals. Linters & Formatters, Style guides, Tabs vs Spaces. All of it is scaffolding around a human being able to open a file and understand what it does.

If that assumption breaks, then patching the process doesn't help. You need a different one.

### You already ship code you don't read

Simply look at your own supply chain.

Take any modern application and count the percentage of shipped code that anyone on the team has actually read. It rounds to zero. It has for at least a decade. Your dependencies, their dependencies, the four layers of packages under those; nobody read them, nobody is going to, and we all ship anyway. The black-box model isn't a scary future. It's the overwhelming majority of every stack running in production right now.

The same is true organizationally. A VP of Eng is responsible for everything their org ships. Developers report to team leads who report to them, and they may go years without reading a line of code. Responsibility never actually required reading - it required knowing the system was structured so that problems surfaced.

We already know how to be accountable for code we haven't read. We've been doing it for decades, at both the technical and organizational layer. The only genuinely new thing is that it's now reaching first-party code - the part we used to write ourselves and the part we thought we had any control over, and therefore the part we assumed we understood.

So what's the right approach?

### Treat the codebase as a black box

The way I've been thinking about this is to stop treating this as a code review problem. Treat it as an ****interpretability**** problem.

![](https://pbs.twimg.com/media/HQIMQTnagAE_nHx.jpg)

You structure the codebase into pieces that are extremely domain-specific, with well defined inputs and outputs. You attach sensors to each piece. You run the functions. You examine the outputs. And you build trust in the system over time by constantly ****interrogating**** it - with every new user journey, every edge case, every new thing a real user actually does.

You never read the implementation. You don't need to. You need to know that this piece, given these inputs, produces these outputs, and keeps doing so as everything around it changes.

The thing that makes this work is the seams. A large monolith (e.g. your typical full saas product) is too complex to be efficiently interrogated - there are way too many permutations of inputs and in many cases user journeys with no clearly defined boundaries of what "correct" even means. Domain specific pieces with well defined inputs and outputs are what turn an unreadable system into an ****inspectable**** one. The interface becomes the review surface.

But going black box is only defensible if the human-readable layer moves up rather than vanishing. Otherwise we're just shipping encrypted intent. Concretely, the artifacts that have to stay readable no matter what the code compiles down to:

- ****Invariants****: what must always be true of this piece, stated in a form you can check.

- ****Traces****: what actually happened on a real run, at the seams, in a form a human can follow.

- ****Attack surface****: what this piece is exposed to and what it's allowed to touch.

- ****Decisions****: every choice made where the spec was silent, which is the one I want to spend the rest of this on.

Some call these specs, although I don't see them as specs in the traditional sense, since they are living documents and contain a ton of architectural decisions logged during implementation. To avoid getting caught up in semantics let's just use the neutral word artifacts.

Over the past few months, building [@duetchat](https://x.com/duetchat) and a few other projects this way, I've started to converge on a process that actually holds up. The rest of this is that process.

### Behavior tells you what. It doesn't tell you why.

Sensors and invariants tell you a piece does what it says. They don't tell you that the agent silently picked optimistic concurrency, or invented a retry policy nobody specified, or decided two features should share a table. Those aren't defects — the tests pass, the outputs are correct, the sensors are green. They're ****decisions****, and they're what bites you three months later when you need to change something.

![](https://pbs.twimg.com/media/HQIMm3xb0AAiYIb.jpg)

So whenever I run a coding agent session, I explicitly prompt it (via a skill) to hand me a ledger of every decision it made where the spec was silent - plain language, ranked least-confident first. Where it guessed, what it guessed, what it would have asked me if asking were free. A two-day run produces tens of thousands of lines I will never audit, and maybe thirty decisions that actually determine whether the thing is right. I read the thirty. I push back on four.

None of this is new, by the way. It's what a good code review was always doing. When a senior engineer reviews a junior's PR, the valuable part was never the line-by-line nitpicking - it was catching that they'd chosen the wrong abstraction, or missed a case, or solved a different problem than the one on the ticket. Reviewing decisions instead of code isn't a downgrade from what we do today. It's what we were always trying to do, with the incidental part finally stripped away.

Small implementation detail: always make sure the auditor is a separate pass from the implementer (via an independent sub agent), because a model reviewing its own work is primed by its own intent and will rationalize. The audit never blocks and can't change code, because the moment it can fix things it starts optimizing for a clean report instead of an honest one.

Put together: ****intent going in, behavior coming out, and the implementation free to be as unreadable as it wants in between.**** Both surfaces survive the code becoming Claude-speak, or an AI-native language, or raw machine code. Neither one is the artifact.

It also answers the "why bother with TypeScript" question from the original thread. Eventually you don't. But you don't lose interpretability either - interpretability was never really living in the syntax, it was living in the intent, and source code was just the last place we bothered to write intent down. Move it up a level and the compiler target stops mattering.

Yes, this is basically "compiling" the spec down to code, and the usual objection is that a compiler is deterministic while AI is probabilistic. Fair - but zoom out far enough and notice what's actually being replaced. It isn't a compiler. It's the person who used to type the code, and they were never deterministic either. There has always been a probabilistic step between intent and artifact. We just called it a programmer, and we built review, tests, and staged rollouts around it precisely because it was unreliable. Those tools don't stop working because the probabilistic step got a thousand times faster.

Done right, arguing about which language or framework you use should end up feeling about as consequential as arguing about tabs versus spaces.

### The hard part is the slicing

Not "break the goal into tasks". You need ****independently verifiable pieces****: things you can build in isolation, instrument in isolation, and be wrong about in isolation. Those are the same boundaries you attach sensors to and the same boundaries that keep decision 12 from silently poisoning 13 through 30. One discipline, both surfaces.

![](https://pbs.twimg.com/media/HQIMvycbMAAr1lY.jpg)

And the harder problem sits one level back: knowing where the pieces even are. Before you can slice a goal you have to know its shape, and usually nobody does — not you, not the agent. That's where long runs actually fail. Not on hard problems. On problems nobody knew were there.

I treat it as fog of war. Scout before you plan: sweep the goal quadrant by quadrant for what's known, what's unknown, and what's a blindspot, handing back rendered options and decision tables to react to rather than asking someone to imagine. Carve the revealed ground into territories that can be taken independently. When a territory turns out to hide more map, re-slice it and scout again.

None of these passes need to be perfect. They need to be honest. Each iteration gets ****less wrong****, and less wrong compounded over a long run is how you get to done.

![](https://pbs.twimg.com/media/HQIClTHbIAAofiz.jpg)

*One unattended run pursuing a single goal for \*\*1 day, 16 hours\*\* - slicing, building, verifying, re-slicing. I reviewed the ledger, not the diff.*

### The loop, end to end

****Map the fog****: interview the idea quadrant by quadrant until you know what you're building.

****Codify****: write the spec. Mostly transcription and adversarial planning, since the decisions got made upstream where they were cheap.

****Build****: put the harness in loop mode and let the spec drive. Reviews fire per slice; the plan re-slices itself when the build proves it stale.

****Review the choices****: read the ledger, least-confident first, push back, let it re-audit.

![](https://pbs.twimg.com/media/HQIOM_zbMAA7_r_.jpg)

### What I'm actually claiming

The TLDR of this isn't that reading code is dead, there are still cases where you should read the code (although this is getting less over time) and you should \*\*definitely\*\* be able to understand any code and architectural decisions made by the AI on your behalf. AI coding is also not an excuse to be lazy and not learn engineering principles, in fact that's more important than ever.

The narrower claim is: if we keep making human reading of the artifact the verification step, slop is the guaranteed, and no amount of reviewing (AI or not) fixes it. Treating the problem as an interpretability problem is the way to solve this.

The two candidates that hold up for me are a black box with well-defined seams and sensors on it, and a structured record of what the agent decided and how sure it was.

That's what a software factory is, to me. Not a coding agent. A production line where intent and behavior are the inspected outputs, and the code is what falls out the end.

The skills that I use to run my own software factories are here (still work in progress):

[https://github.com/dzhng/skills](https://github.com/dzhng/skills)

Still curious what other tokenmaxxers think. I'm still trying to figure it out like everyone else.
