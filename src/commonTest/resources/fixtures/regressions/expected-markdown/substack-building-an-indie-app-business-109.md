### Shipping every Monday, fighting Google Play, and writing down the $100k goal.

[Sebastian Röhl](https://substack.com/@sebastianroehl)

Aug 23, 2026

Welcome back to another issue of my weekly indie log! Small confession first: the last issue reached you on Saturday because I honestly thought it was Sunday. That’s the level of scatterbrain we’re working with here. This week the machine kept rolling anyway: HabitKit 1.16.1 is out, 1.17.0 is approved and waiting, 1.17.1 is already finished, the website got a new home, and my AI assistant moved into my second brain. Let me tell you about it.

## 🛠️ Development Corner

**The release train made its second Monday stop.** HabitKit 1.16.1 went live on Monday, with the revamped “Compact List”, a lot of bug fixes and performance improvements. Second Monday release in a row, and I handed 1.17.0 (the Quit Habits update from #108) in for review on the same day. It’s approved and ready (on Apple’s side), and the plan is to release it early next week, three Monday releases in a row would be the dream. But first I have to push the 1.16.1 phased release on Android to 100%, because handing in the next build would halt its distribution, class Play Store quirk. And 1.17.1 is already finished too: you can fast-switch between the view modes with a swipe gesture now, and I finally added reordering habits via drag and drop. That one was blocked forever because it redraws the list a couple of times, and only now that the performance is under control I can play around with things like that.

**Google Play is testing my patience again.** While a phased release is running, I can’t hand in the next production build at all without stopping it. So a weekly release train and a seven day rollout window simply don’t fit together on Android. Lately reviews take up to a full week over there too, so my Monday plans are honestly Google’s decision, not mine. It blows my mind how such a big company makes so many questionable decisions in their most important developer-facing UI. And I’m saying that as someone who uses App Store Connect every week, so the bar is not exactly high.

**The redesign feedback threw me a curveball.** I’m getting lots of great feedback on the recent changes, the task list grows faster than I can work it off, and I’m honestly in full monk mode right now. But one complaint keeps coming up: for some people the sizing of the UI feels off since the redesign. The old HabitKit used a really weird package that scaled the whole UI down on smaller devices. I got rid of it, and for those users the jump is big enough to complain about. On most modern devices the app looks much better now IMO, but I also understand people who don’t like big changes to their favorite app. I’m still looking for a good solution, and I’m not sure yet if there is one, or if I just have to roll with it.

![Image](https://substackcdn.com/image/fetch/$s_!5_zU!,w_1456,c_limit,f_webp,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2F040619e1-c8e2-4a95-8bef-2352e3e7031a_1198x247.png)

But there are also people who love the recent changes:#

![](https://substackcdn.com/image/fetch/$s_!A54C!,w_1456,c_limit,f_webp,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2F367c6a5b-932e-44c4-8974-64b7311f79f6_1200x318.png)

**The new HabitKit website is done, and it runs on a boring stack.** Remember the Dan Go newsletter mention from #108? It had a second, less fun effect: so many people clicked the link that they drained ALL my Netlify credits, and I had to upgrade my subscription just to keep the site online. A newsletter mention should be a good day, not a hosting bill. So this week I rebuilt the whole website with good old Laravel, deployed on my own Hetzner VPS with Ploi. No serverless magic, no credit meter, just a server that doesn’t care how many people show up. The whole rebuild took me two days. I’m super happy with the new look, honest 10/10, much more modern, with more testimonials and the media outlets HabitKit was featured in. By the time you read this, [the new version should already be live](https://habitkit.app/). I also want to beef up the blog section, and I’m playing with an idea for “HabitKit Stories”, where I interview people who improved their life with the app. More on that when it’s real.

![](https://substackcdn.com/image/fetch/$s_!KfaZ!,w_1456,c_limit,f_webp,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Ff6edeb86-f795-4ef1-b9cb-82df2ca4d0fa_2008x2024.png)

**Grok Bot moved into my second brain.** I hooked Grok Bot up with my Obsidian vault this week. Now my daily briefing, my todo tracker and my support email triage bot all read from the same knowledge base I write in every day. The first attempt taught me a lesson though: I tried to let the bot build its own system, and that was the wrong way around. The bot has to adapt to YOUR workflow, not invent a new one. Since I fixed that, it’s been one of the easiest AI experiences I’ve had. No tinkering, no config files, a completely different world compared to my Open Claw setup a couple of months ago. Fun fact: it also dug through my journal to prepare the notes for this issue. One thing I still don’t hand over is the actual app development. My style is too iterative, I want to see every change live and call the shots after every move of the AI. For that I’m currently coding with Grok 4.6 in Cursor on the “Extra High Fast” setting, and I can’t deny the results are great. Not as smart as Fable I feel, more like the last Opus 4.x, but for fast iterating it’s a really cool experience.

**The ratings rabbit hole.** HabitKit is still #3 for “habit tracker” in the US App Store, behind Finch, and this week I finally understood why. Ariel from Appfigures posted an explanation: it’s all about ratings. Finch has an incredible DPR (downloads per rating) of 13, meaning they only need 13 downloads to receive a new rating, and they collect way more ratings than everybody else. So my keywords are fine, my ratings velocity is the bottleneck. That’s why I refreshed my rating prompt system in 1.17.1, which I literally haven’t touched since I set it up three years ago. I hope I can beat them again someday, but I won’t lie, with numbers like that I wouldn’t be surprised if they grab #1 at some point.

![Image](https://substackcdn.com/image/fetch/$s_!VCP8!,w_1456,c_limit,f_webp,q_auto:good,fl_progressive:steep/https%3A%2F%2Fsubstack-post-media.s3.amazonaws.com%2Fpublic%2Fimages%2Fa6166d66-9890-418c-9f3c-3b3f4560d172_2168x1208.jpeg)

**FocusKit 1.8 is fighting through review.** FocusKit is still in the passenger seat, but it moved: I tested 1.8, prepared the assets and handed it in for review this week. Apple answered with a rejection, so sorting that out landed on top of the weekend list. The usual review dance.

## 💡 Indie Insights

**Write your goal down, then say it out loud.** You’re more likely to reach a goal when you write it down. And you’re even MORE likely to reach it when you share constant progress updates with someone. That’s the whole magic behind building in public: you share your goals for the week, you post progress, you write it all up at the end, and hundreds of people see if you keep pushing or not. I recently decided that it’s time to show up on X again, and this time it sticks. I’m sharing my journey there daily, wins and fails, and connecting with some awesome people. So let me use all this accountability properly and say the big one out loud: I will take my app business to $100k per month. There, it’s written down AND published. I used to believe I could never build a popular app that makes real money, and that belief turned out to be wrong, so I stopped trusting my limiting beliefs. Reach for the stars: if you aim for Mars, you’ll at least come along the Moon.

**Not every side quest is noise.** Recently I told you how proud I am of my noise detector, killing new product ideas faster and faster. This week I found the exception to the rule. Instead of starting new products with my leftover AI tokens at the end of the week, I now use that budget to build mini tools that only serve me. First candidate: bookkeeping. My current solution (SevDesk) is clunky, expensive, has no bulk upload, and its invoice detection misses a lot, it costs me time and nerves every month. Collecting receipts at the end of every month annoys me deeply. So I’m building a small Git-based, AI-native bookkeeping helper for exactly one user: me. The difference to a side product is everything. A product wants marketing, support and a roadmap. A tool just has to save me a few hours every month, and honestly, it’s a super fun project on top. There is a bigger thought hiding in here: less custom apps, more AI integrations. Keep the data in simple, text-based files an AI can operate on, and replace the UI with chat. The bookkeeping helper works exactly like that, and the next candidate is already in my head: letting Grok Bot track my workouts and tell me when it’s time to up the weights. An AI personal trainer, no app required.

## 🎯 Goals for Next Week

Keep the train rolling:

- Release HabitKit 1.17.0 early in the week (Quit Habits, three consecutive releases in a row if Google plays along)
- Fix the FocusKit 1.8 rejection and get it released
- Decide what to do about the UI sizing feedback
- First working version of my little bookkeeping helper (leftover tokens only!)

That’s it for this week. Thanks for reading, and I’ll see you in the next one!
