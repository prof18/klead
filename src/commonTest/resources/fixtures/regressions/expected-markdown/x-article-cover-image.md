![Article cover image](https://pbs.twimg.com/media/HM0mwgebIAAODU7?format=webp&name=large)

Software factories have been all the rage these last few weeks. So I built one.

Here's what I learned, how it works, and how you can build one in an afternoon.

## My Goals

It needed to be really simple, something that just worked and didn't require a bunch of babysitting. Ideally, it would run entirely on my existing Claude subscription.

It sounds naive, but essentially unless it integrates easily into my existing workflow, it's not going to be something that sticks around. I don't want to build this and have to change all my existing habits just to use this new system. I know from experience that if that happens, it won't work for me long term and will fade into irrelevance.

So instead of attacking this as one big effort, I split it up into two distinct phases:

1. Pre-triage - figuring out the stuff "to do"
2. Implementation - actually "doing" the stuff

In the center is Linear, the source of truth of what work needs to be done.

Every software factory needs to have this centralized repository of work to be done, whether that's GitHub issues, Linear, or something else. It should be what you already use so it's easily extensible for any system to add work for either agents or humans to pick up and complete later.

![](https://pbs.twimg.com/media/HM0pweaaAAAA68P.jpg)

*Linear with an auto-generated issue via System Health Check loop*

The pipeline has intentionally defined seams that make the process of building a software factory actually manageable:

1. Create work (loops w/ MCPs)
2. Store work (linear)
3. Complete work (SDLC agent)

By splitting it up, you don't have to build both the pre-triage and implementation sides at once (and I actually recommend against it to avoid over-engineering). Linear makes the interface between what "to do" and what we're currently "doing" well defined and easy to extend.

## Pre-Triage Step

In the above diagram, the work progresses from left to right. The left side is where work's created and contains any number of systems (either humans, agents, or APIs) that adds to what needs to be done. All outputs created in this pre-triage step is put into our Linear.

![](https://pbs.twimg.com/media/HM0nHtUbEAAofJu.jpg)

*A few loops that run in a Claude cloud environment*

In this step, we have three main loops:

1. System Health Check Loop (i.e. a bug finder) - this loop runs daily at 5am and is connected to a few MCP servers: Posthog for error tracking; Vercel for system diagnostics; and Linear for creating issues.
2. UX Improvements and Customer Feedback Loop - this loop runs weekly on Monday at 9am, and scans every new piece of feedback, every customer support chat from Intercom/Fin, and all Posthog session replays. Session replays are a goldmine. You can see where rage clicks happen or where users are struggling and confused.
3. Churn Analysis Loop - this runs daily at 6am and investigates every customer that clicked cancel in the last 24 hours. It pulls payment and user data from Stripe (including their email/location), and then looks at this customer's session replay in Posthog to see what they were doing before they cancelled. We pull their usage from Supabase to see if it was a wrong ICP, never experienced that "wow" moment, or hit bugs. The report is sent to Slack and the agent creates (or comments on) issues if they are related to customers churning to bump priority.

To actually run these agents consistently, I kept it simple and used Claude Code Cloud Routines.

No over-engineering with a Hetzner VPS, a caffeinated mac mini, etc.. It's by far the easiest way I've found to leverage your existing Claude Code subscription and create always on agents that can be triggered on a schedule or via a webhook event. And to all the people saying "but what about vendor lock in!!" - it's literally just some text. Feel free to copy the prompt to wherever you want, I just wanted the easiest and cheapest thing to set up that works reliably.

And another reason to use Claude Code Cloud Routines is that Anthropic's cloud environments have close parity with Claude running locally.

![](https://pbs.twimg.com/media/HM0o3b2aUAAD6oL.jpg)

*Cloud Claude Routines*

Obviously they don't have your env vars (which can be added to the cloud environment setup, if needed) but if you set up an MCP connection via the Desktop App's connectors, both your local Claude cli and remote Claude can use them, unlike Codex. This was the killer feature for me and is what makes these pre-triage agent loops work so well. It also makes it easy to run a bunch of sessions in parallel without worrying about your laptop's memory.

If you just implement this pre-triage step, you'll still get a lot of leverage even if you don't build the next step where the work is completed.

Start small with a single loop, try it out, refine it, then add more. Figure out what routines you can create today that put new, high-quality work into your existing issue tracker and then do whatever you currently do today for implementation.

## Implementation Step

The most basic version of step two is to give your agent an issue ID and say to go complete it. This is what most people already do tday when working with agents, however, it probably isn't what you want if you're reading this, because you're now the bottleneck in the loop, kicking off an agent directly in a session.

Instead what I did was build a way to trigger remote Claude Code sessions directly from Linear.

![](https://pbs.twimg.com/media/HM0nd8oawAAc-KL.jpg)

*Going from linear issue to Claude*

It works like this:

1. First, identify how you want to trigger a new Claude session. For us, we add a 'auto' label to a Linear issue that kicks this new task off.
2. Then, Linear sends a webhook event to our internal webhook API service (a newly-created internal Hono app) that parses the event and then forwards the correct information to the Claude Routine.
3. Finally, this lightweight API service makes a POST request to Anthropic that triggers the Claude routine with an initial prompt.

Using a 'auto' label as the trigger allows us to control automatic execution of new Claude sessions.

By default, we do not have pre-triage steps include these 'auto' labels and require a human to add the label that triggers the agent to start work (keeping the human-in-the-loop). That's why we call step 1 the pre-triage step, because something is still figuring out what to actually work on, either via human involvement or some agent looping and watching new issues being added and kicking off new implementation sessions.

However, because adding a label is also easily done by an agent, we could have pre-triage loops create new issues with the 'auto' label by default, which kicks off a new Claude session. Using a label becomes a very flexible way to start new implementation work either automatically by an agent or by us humans.

![](https://pbs.twimg.com/media/HM0nq8baoAAwAKl.jpg)

*A lightweight routing service to kick off Claude sessions*

Because Linear supports webhooks, the whole "push when a label is added" works. The only downside is we need to have a public API that routes accepts these webhooks and formats the events and payload data to trigger Claude Code. This new service also adds the correct authentication headers which is why you can't have Linear trigger the routine directly.

Alternatively, you could have a scheduled routine that starts every so often and tries to implement any new issues not in progress with the auto label.

For the actual routine prompt, I recommend building a reusable skill that walks through the entire SDLC, named something like /implement or /do, that makes it easy to just say " **/do ISSUE-NNN** ", which documents how to correctly go through the steps of fetching issue context, implementing the work, verifying in browser, creating the PR, and watching for comments.

Then the Claude routine prompt, triggered by the Linear event, can just have a very simple prompt. Here's mine:

> Retrieve the provided issue and use the /do skill to implement the requested change and create a pull request. Only fetch the exact issue provided. If it's already complete or in progress, stop.
>
> The issue reference does not appear in this message — it arrives as a separate follow-up message wrapped in a \`\<routine-fire-payload>\` tag, sent immediately after this one, in the same session. Wait for that message before deciding whether an issue was provided. Only conclude the issue doesn't exist, or that none was given, after checking for it in the \`\<routine-fire-payload>\` message and confirming the referenced issue truly does not exist.
>
> When you start work, comment on the issue, set it to "In Progress", and update the issue with anything meaningful as you progress. Always prefix Linear issue comments with \[Claude\].

So at this point you have parallel Claude Remotion sessions triggered via API, implementing issues, opening PRs (and hopefully verifying their work via Playwright or Agent Browser).

It's essentially a full software factory that is fully observable, runs while you sleep, and works entirely on your Claude Code subscription.
