[The Sims 4](https://simscommunity.info/news/the-sims-4/)

The Sims 4 December Patch is **[coming on December 1st, 2026](https://simscommunity.info/2026/11/21/sims-4-update-december-qol-2026/)** as part of the ongoing Quality of Life Updates. The Sims Team hosted a dedicated Discord Q&A session where select game developers answered questions from players regarding the upcoming patch drop.

The team revealed – among Seasons, Events and Quality of Life updates – ***"CAS/Build Mode items celebrating disabilities and neurodivergence. Around 10 items total"***. This Sims 4 December Patch also marks the end of **[2026 QoL Roadmap](https://simscommunity.info/2026/02/24/sims-4-quality-of-life-roadmap-2026/)**, but worry not, the EA Community Manager reassured that there will be a new one at the start of 2027.

> We will have some updates about what is coming for The Sims 4 early next year but it wont lay out the whole year at once

Read the full summary of all important questions and answers shared for the upcoming Sims 4 December Patch:

## Table of Contents

![CONVERSATION 2](https://simscommunity.info/wp-content/uploads/2022/09/CONVERSATION-2-1024x758.png)

## The Sims 4 December Patch: Dev Q&A Recap

## Developer Introductions

**Morgan:** Sul sul! I'm Morgan, a Producer on the Sims 4. Today, I can answer any questions about our December update that's coming out in... December. It includes a handful of fixes, an update on crash fixes from October (plus a few new crash fixes), and a handful of free CAS/Build Mode items celebrating disabilities & neurodivergence (I'd like to keep those mostly a surprise). I'm also hoping to get feedback on an upcoming Quality of Life patch themed around Seasons, Holidays, and Events (like a party). We'll also go after bug fixes for things like Weddings or Getaways in this patch. We have our bugs tracked for this theme, and we have some pretty fun smaller features planned, BUT we want to know what QoL changes you'd hope to see. For example, in the food drop-in someone suggested moving "Open" to the front menu of fridges, which was a SUPER easy change. Or the suggestion of a tuning pass on food prices/calories. I can't promise anything today concerning feedback, but I will take notes and follow up with the team!

**Carlos-Maxis:** Also introducing myself, I'm Carlos, I'm a gameplay engineer on the Sims 4. A lot shorter than Morgan's but, happy to be here as well.

## December Update: Free Disability & Neurodivergence Items

**Question:** Will there be any more freebie items in this update?

**Morgan:** Yes, some CAS/Build Mode items celebrating disabilities and neurodivergence. Around 10 items total.

**Question:** Do you have any photos/previews of these 10 items, or will it be a December surprise?

**Morgan:** I'm looking at some photos now, but we want to keep it a surprise so you have something fun to see in the patch notes.

**Question:** Was any input from disabled and neurodivergent people taken into account with the new items?

**Morgan:** Yes, we worked with Disability Belongs®.

**Question:** With the disability rep coming, will it be possible for Sims wearing hearing aids to also wear earrings in CAS?

**Morgan:** Unfortunately, probably not since they use the same CAS part.

**Question:** Since you're introducing cosmetic items for disability representation, has there been any discussion around gameplay possibilities?

**Morgan:** It's something we've discussed before, but one of our larger concerns is that we don't want to be prescriptive about how certain disabilities or neurodivergences present. We'd much rather players tell the stories that are real to them and provide them the tools to do so, but we're always looking for ways to add those tools through traits or preferences, etc.

## December Update: Performance, Loading & Genealogy

**Question:** What specific changes were made to the marketplace, does it have something to do with storage? Is it possible to store maker kits on external hard drives?

**Morgan:** It doesn't have to do with storage, but rather how loading happens. The changes we made will also potentially improve load times.

**Question:** There's still an issue with certain kits not having items function for many players who purchased them through the marketplace. Is this being looked into?

**Morgan:** There are some improvements for this coming in December as part of that loading fix, but more fixes are currently in progress. It's one of the top issues we're tracking and working on.

**Question:** How did you optimize loading time for players with huge branching family trees? What's the secret?

**Carlos-Maxis:** The genealogy tree does a LOT of loops through the Sims so that their relationships are set correctly. A bug was causing a lot of extra loops, so we cleaned those up, and also cleaned it up so that some unintended relationships no longer appear when loading into a save.

**Morgan:** We've been looking a lot into performance lately, with more updates coming soon after the December update. We look at an area that takes a long time to load or causes lag, see what's happening in the background, then look for redundancies or ways to improve it. For Household Loading, we found redundancies in data being loaded around family trees; cutting those made loading quicker.

**Question:** Are there other areas of redundancy you'll be looking into after this patch?

**Morgan:** 100%. We know performance and load times are top community issues right now. We already have some fixes in testing for down the road that are really promising.

**Question:** Please don't break our family trees with the genealogy update.

**Carlos-Maxis:** There are 2 genealogy fixes. One is loading faster, the second is a bug fix around relationships changing when loading into a lot. I don't think either should break any of your family trees, so they should be safe.

**Question:** I'm 40-something generations in, I'll time it and see how much it helps.

**Morgan:** I would guess... that's going to help you with your load times.

**Question:** Would you consider a setting to turn off Sim culling? Culling past minimum LOD empties Sim profiles in family trees, and my 15-generation legacy can't be fully viewed because of a cap. It would be great to allow more connections to show.

**Morgan:** This would probably be very unhealthy for games.

**Carlos-Maxis:** I worry about save integrity with this. Each extra Sim adds so many relationships and other data that needs to be saved/loaded; we're already very memory-tight on consoles, and this could blow up savefile size!

**Question:** Is there a feature in the update that was the most difficult to implement but adds great value?

**Carlos-Maxis:** Some of the faster-loading ones have been a challenge. The codebase is BIG and there's a lot to go through.

**Question:** Any plans to improve Sim responsiveness? Sims react slowly, interactions are slow to cancel, and the game often drops actions from the queue.

**Morgan:** If interactions are dropping from the queue, please report them; those need to be fixed individually, since something is failing silently in the background. As far as lag goes, we're looking into performance improvements now. Some slowness is because there are so many interactions on an object, which is why we need to be judicious when adding new interactions or things stored within them.

## December Update: Conversations & Social Groups

**Question:** Will the "face each other more during conversation" fix also stop Sims from changing positions (standing up/sitting down) when talking?

**Carlos-Maxis:** It should. It has to do with multiple social groups trying to wrangle the Sim around. Those interactions should now do less rebuilding, and Sims should "stick" to the group they were originally in and actually face each other.

**Question:** How does a Sim know when to turn their head at who's talking in a group conversation? Does my Sim with the giant party hat get more attention?

**Carlos-Maxis:** That'd be funny! Sims know who they are in a group with, and the interaction knows which 2 Sims are interacting, so it lets them know to look at each other.

## Next QoL Patch: Seasons, Holidays & Calendar

**Question:** Could different households celebrate different holidays (e.g. Winterfest vs. a Sims equivalent of Hanukkah)? Is something preventing that?

**Morgan:** My first pack was actually Seasons, so it's been a WHILE, but I recall there was a technical reason we implemented it the way we did. I'll write down this feedback and follow up. I'm taking notes.

**Question:** Could another holiday be added to the standard Seasons calendar, e.g. one featuring the Flower Bunny?

**Morgan:** I'm not sure, but I can write it down and follow up as something we consider for the update!

**Question:** Are you planning to add more tradition activities (pottery, canning, archery, etc.)? And could holidays stop preventing us from selecting goals for events?

**Morgan:** We aren't looking to add new tradition activities, but we'd be interested in knowing where you feel existing traditions aren't properly supported.

**Question:** Will current traditions be updated to include more pack content (e.g. the fighting tradition including sword fighting)? Any plans to polish events from all packs, or only base game and Seasons?

**Morgan:** If we had suggestions for how you want existing traditions updated, we'd look into it. I haven't seen any suggestions on that though. Am copying down the ones you added.

**Question:** The Seasons costume tradition only lets you wear base-game costumes. Can you update the list so costumes from newer packs work?

**Morgan:** Let me follow up.

**Question:** Could we drag holidays around on the calendar instead of cancelling and remaking them?

**Morgan:** Idk, but great suggestion for us to follow up on.

**Carlos-Maxis:** We have some small changes to the Calendar planned (no details yet), but that could be a good improvement later down the road.

**Question:** More icons for holidays would be amazing.

**Morgan:** This might be one of the things we're looking at already. If so, it'd be ~12 new icons supporting regularly celebrated holidays around the world that don't already have good icon options. (I won't say which or promise, but we're looking at it.)

**Question:** Could we disable some default pop-up holidays (e.g. Prank Day, Neighborhood Brawl) in game options?

**Morgan:** You know, I feel this way too when I play. Let me add that to the list to follow up on!

**Question:** Could a holiday last more than one day (like Hanukkah)? And could Sims request multiple paid days off in a row, or unpaid time off?

**Morgan:** Idk, probably hard to do, but I'll write it down. Sometimes the team surprises me!

**Question:** The calendar still constantly shows the "new" notification icon. Can this be looked at?

**Morgan:** Following up on it!

**Question:** Did you fix neighborhood homes sometimes not displaying the right festival/event decorations?

**Morgan:** I don't remember this one, but I'll add it to my list to investigate.

**Question:** It would be nice to see other Sims' houses decorated with string lights around Winterfest.

**Morgan:** You should see this in game already, I believe.

**Question:** Customizable season lengths per season would be great.

**Morgan:** I don't see us making this customizable, but we are investigating adding a 3-week option as a potential thing we could go after.

## Next QoL Patch: Weather & Temperature

**Question:** NPCs die constantly from temperature while I'm not looking. Maybe a toggle for temperature deaths?

**Morgan:** I know we have some bugs tracked around this already; we'll investigate for this update.

**Question:** Could we disable certain weather for specific worlds (e.g. no storms in Brindleton Bay)?

**Morgan:** Probably a little too complex, but I can follow up on it.

**Question:** Why was snow depth never introduced? Could we get more weather effects (mist/fog, hail, icicles)? Will trees and bushes from newer packs get proper wind and seasonal effects?

**Morgan:** I was brand new when we made Seasons, but I recall we made this choice to support more of something else, maybe old worlds. When we made Snowy Escape, we built that world specifically so the snow could be deeper. Effects like mist/fog hurt performance, but we bake them into worlds on purpose (and test performance) when doing VFX for worlds, e.g. Moonwood Mill. We have some bugs tracked for items like this and will see if we can fix any for this update.

**Question:** Could you add more ways to manipulate weather besides the machine, e.g. for other occults or special traits?

**Morgan:** I'll add it to the feedback list.

**Question:** Could Sims' desire to make snowpals and play in leaves be decreased a little?

**Morgan:** We have done recent autonomy updates to this. Do you all still feel this is a problem?

**Question:** Is there any chance Sims being scared of thunderstorms could be reduced (maybe children more likely, adults rarely)?

**Morgan:** You're going to love some of our plans.

**Question:** Coldproof and Heatproof often bug out when a Sim has more than 12 traits.

**Carlos-Maxis:** Has this been reported in the forums? I think we may be hitting the trait cap, but I'll take a look.

**Question:** A Sim with Seasons hot/cold resistances who got the Danger Kid formative moment (Adventure Awaits) had hot and cold flashes their entire life. Can that be looked at?

**Morgan:** Adding it to the list to look at.

## Next QoL Patch: Events, Parties & Weddings

**Question:** Could NPC Sims throw weddings, or could we organize weddings for NPCs? More NPC-initiated events like balls?

**Morgan:** Don't know, but I'll add it as feedback to the list!

**Morgan (follow-up):** We don't have any planned, but we fixed a birthday party bug around one recently. Let me follow up as a potential thing for the update, but no promises.

**Question:** Any plans to let drink trays use "call to grab a drink"?

**Morgan:** I believe we looked into this one and it would have been very complex to do.

**Question:** Could we have more festive recipes, like a Yule log?

**Morgan:** We won't be able to add any recipes for this update, unfortunately.

## Other Gameplay & Bug Questions

**Question:** Will parent-child boundaries also be investigated? A father developed a romance bar with his sons in an unmodded save.

**Morgan:** While we normally go after the bugs with the most votes, a bug like this should get much higher priority regardless of votes. Following up on the link you shared.

**Question:** Could players get direct in-game access to tuning values (like NRaas Retuner for Sims 3)?

**Morgan:** Something like this would be very cool! I imagine it'd be pretty complex (a little too big for the QoL team to go after), but we often talk about ways to give players more choices.

**Question:** The navmesh under stairs needs looking at; I still find Sims stuck under stairs.

**Morgan:** Report that one with a save and information. If it's a problem with the stairs, QV should be able to repro with a save, but I've never heard of that one.

**Question:** Are there plans to fix bucket list goals for aroace and asexual Sims?

**Morgan:** Can I have more information on what you mean by this?

**Question:** Could we swim in natural water in older worlds?

**Morgan:** This one would be really hard to do, unfortunately.

## Tech Talk: Python, Navmesh & Routing

**Question:** Isn't the game coded in Python?

**Carlos-Maxis:** The gameplay is in Python, but other parts of the game are not.

**Question:** What do navmesh/routing mean?

**Carlos-Maxis:** Navmesh = the area Sims can walk around or "navigate" in. Routing = Sims using that area to move.

**Morgan:** Navmesh is an invisible net over the world that tells Sims where they can or cannot go. Most games have one. Routing means where Sims can move. It's like railroads, but in open space where you can go left, right or diagonal.

**Question:** Can we get an inside scoop on how routing works behind the scenes someday?

**Morgan:** Carlos says this is like a tech talk. XD

**Question:** Can we have a tech-talk hour with Carlos someday?

**Carlos-Maxis:** Maybe in the future, but the GDC Vault has talks by other coworkers. I don't have links off the top of my head, but there's some stuff out there already.

## Future of The Sims 4: Worlds, Roadmap & Releases

**Question:** New worlds?

**Morgan:** We aren't making new worlds as part of an update. Just to squash any rumors.

**Question:** Am I understanding correctly that new worlds will be coming to The Sims 4 in the future?

**Morgan:** No.

**Question:** Is Seasons getting a world?

**Morgan:** No.

**Morgan:** We're actually planning on doing a Dev Drop-In on World Building soon, which would be a great place to talk about worlds coming up!

**Question:** I'd love a Sims franchise roadmap like last January.

**Morgan:** I think we're planning to have an update at the start of the year, but it won't be about the full year.

**Question:** Are we getting anything ever? New drops, kits, packs, free drops, feature updates? (Later edited to add: not general game updates or QoL stuff.)

**Morgan:** Yes.

**Morgan (on the edit):** You edited your post. I am not commenting on edited posts.

**Question:** Please give us more details about the future. / The update?

**Carlos-Maxis:** The year is 2027.

**Morgan:** It will be after the month of January. / The future will be.

## Fun & Off-Topic

**Question:** What's the best world in the Sims expansion packs?

**Morgan:** Maybe Moonwood Mill, but I'm biased.

**Carlos-Maxis:** What is your favourite one? I like Tomarang.

**Comment:** Tartosa is GORGEOUS.

**Morgan:** I worked on this one too!

**Question:** Morgan, what's your favorite season?

**Morgan:** FALL! In real life, but in the Sims it would be winter, because I like playing inside and the snow makes it cozy. Though I'm also a big fan of thunderstorms.

**Comment:** Really cool stuff, thanks!

**Morgan:** When I was new in the game industry at a different studio, I was an art tester, and I got to run around checking things like navmesh to make sure it wasn't telling NPCs they could go places they shouldn't. It was like playing I Spy all day looking for art bugs.

**Carlos-Maxis:** We did not invent C+++++++++++, I wish we did. / It wasn't even fixed in C++!

**Comment:** Routing and animation are too scary...

**Carlos-Maxis:** They really are.

---

Reticulating splines as a webmaster for Sims Community over the last 12 years. You can find me here writing articles and doing reports on your favorite life sim games, among other things!
