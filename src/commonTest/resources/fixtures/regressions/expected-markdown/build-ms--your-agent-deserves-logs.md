I’ve had a bug in Plinky since launch that I hadn’t been able to fix until a few weeks ago. Every so often a paying user would see a banner asking them to upgrade to Plinky Pro, even though they’d already paid. Beyond it being a subpar user experience, I felt particularly bad because I was annoying someone who’d actively chosen to support my work. I tried quite a few fixes, including some approaches that I’m not particularly proud of — but none of them did the trick.

Rather than trying another fix, I decided to see what would happen if I let Codex take a shot at the problem. As Codex was working I made a connection I’d never made before: whenever I hand my coding agent a few logs it gets dramatically better at debugging. LLMs are great at spotting patterns in large datasets, so I began to wonder — if 10 logs helped Codex figure out my bug, what would happen if I added 100, 1,000, or even 10,000?

### Agents Can’t See What You See

Anyone who’s used a coding agent has had this very annoying experience:

> You: Claude, please fix this \[elaborately described\] bug.
>
> Claude after churning for 10 minutes: I’ve fixed your bug!
>
> You: I just tried your fix and it doesn’t quite work, and now this other thing is broken.
>
> Claude: You’re absolutely right! Here’s why it doesn’t work, and here’s a sonnet that describes why you’re the most brilliant person I’ve ever met.

Why does this happen over and over again? Because agents can’t see what we see. They have no mental model of your app, no sense of coherence, and no inherent understanding of why they’re doing this work in the first place. You know why you’re solving a problem, but an LLM only knows what it was trained on and what lives in its context window.

We hand LLMs tools to fill in the gaps — transforming models into agents. One such tool is automated testing, and it’s a good tool, but it can only take you so far. When your agent writes tests for code that it doesn’t innately understand it has no reliable way to know whether those tests are valuable.

But agents aren’t dumb. We know that they are remarkably good at observing patterns and anomalies in large batches of data. That’s what our logs will be for — providing the agent a record of its work so it can pattern match against reality, rather than guessing what happened.

### What Are Logs For?

The first program any developer writes is [Hello World](https://en.wikipedia.org/wiki/Hello,_world). The syntax changes from language to language, but it’s always a variation on `print("Hello World")`. That line is a log at its simplest. We write a string, it shows up in the console, and we have confirmation that our code ran and did what we expected.

The more an app grows, the less it looks like `print("Hello World")`. Nobody sits in a terminal watching text scroll by for fun, so what are logs actually for? Debugging.

When a developer is trying to understand a problem they’ll litter the code with temporary print statements. Adding `print("Got here")` helps them understand the flow of their program, and `print("Synced links count:", links.count)` will make sure the data in their app matches their expectations. When you read these logs back in order, you can see exactly where your mental model diverges from how the program actually ran. That divergence is a clue, helping you track down the root cause of the bug.

### Letting Logs Do Your Work

With this understanding of logs, let’s come back to Plinky and my subscription banner bug. Before Codex began working on a fix, I asked it to log out every action and state change related to subscriptions, then run through the app a few times to generate lots of logs. We then handed those logs back to Codex, and asked it to find the root cause.

And it worked! Codex pored over an enormous pile of its own logs and found what I couldn’t. The fix was creating a state machine to represent every subscription state change — including a few potential states I’d never noticed in my year of debugging this problem by hand. This was a better solution than anything I’d come up with, and the moment I realized I was onto something bigger.

I began building an open source library to codify the pattern, and dogfooded it by logging every action and state change in Plinky. For the last month I’ve been handing Codex my bug reports, telling it to walk the code paths the user hit based on those logs, and having Codex fix the bug while I’m off doing something else.

This works just as well on a greenfield project as it does on a year-old bug. I recently built myself a Spotify client in an afternoon, almost completely autonomously. I bootstrapped the project with a set of milestones and used Codex’s [`/goal`](https://developers.openai.com/cookbook/examples/codex/using_goals_in_codex) to keep Codex running until it finished all of them. Codex churned for three hours until I could log into Spotify, play my music, and do everything else you’d expect from a Spotify client.

The reason it could do all of that unattended is that Codex wasn’t just writing code anymore. The logs we added helped Codex determine what worked and what didn’t in its own runs, even allowing me to watch Codex catch and fix its own bugs in realtime.

### From Bug To Library: Introducing Broadcast

This wasn’t a one-off result, and I’ve found the outcomes are so universally positive that this technique is something I now adopt in every project. That’s why I codified this practice into an open source Swift [^1] library called [Broadcast](https://github.com/mergesort/Broadcast) that integrates structured logging for humans and agents alike into any app.

What started as a few logs in Plinky became thousands. And when I confirmed this strategy works, it became a library, a skill, and copious documentation that integrates Broadcast into any app — giving your agent the debugging superpowers it deserves.

It still takes real thinking to build good software, but we’re designing the strategies and tactics that let agents go further and faster than we could on our own. Last year I was staring at logs and my debugger trying to figure out why a bug was happening, while today I hand those logs to an agent to let it find the root cause.

For Plinky, that means more features, built better, with fewer bugs. I spent a year unable to fix the race condition that caused some paying users to see upgrade banners, but Codex fixed it in an afternoon. It took weeks of work to lead to that afternoon though, because I had to give Codex the power to see what I couldn’t make out with my own eyes.

## Footnotes

[^1]: I’ll be porting this to TypeScript and any other language I use, but I wanted to get this right with good documentation before kicking that off.
