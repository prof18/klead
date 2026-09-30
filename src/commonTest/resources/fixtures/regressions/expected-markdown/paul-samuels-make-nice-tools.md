I spend a lot of time thinking about developer experience at all levels, from what makes a helpful error message deep in a framework to how we make people’s dev environments easy to spin up. In this post I’ll walk through how a local development tool I wrote evolved over 4.5 years and what that taught me about designing developer facing tools.

This isn’t a post about Docker, SwiftUI or Compose. It’s about what I learned building a developer tool that people actually used and what I got wrong along the way.

---

## The Problem

For my day job I work in the native apps team at Autotrader on the iOS/platform side of things. As a team we don’t only own the Android/iOS code bases, we also own a few backend services that power the apps, the number of backend services has grown over time. This means that if you want to make changes to a backend service and test it locally in a simulator you need to spin up all the related services locally.

As a simplified example imagine this setup

```plaintext
+-----+     +-----------+     +-----------+
| iOS |-----| Service A |-----| Service B |
+-----+     +-----------+     +-----------+
```

To run this locally there are many ways I can configure things. One scenario might be that I want to make changes to `Service B`, which means I still need to spin up `Service A` to allow the communication.

In the beginning this was all done manually - you’d load up the projects for `Service A` and `Service B` in IntelliJ and run them both locally. This worked but it pushed both cognitive and operational complexity onto individual developers, which is exactly where DX debt hurts most.

---

## First Solution

Some people will have been screaming “use docker compose” and you’d be right that’s what I did. All of our services were already containerised so there was nothing to change there. I just needed to write the `docker-compose.yml` to configure everything, which I did and it worked fine.

There were a few issues that made me feel uncomfortable stopping here

- Not everyone is comfortable on the command line so this can be intimidating
- Docker is possibly not a tech a lot of Android/iOS devs delve into
- The UI isn’t great

To elaborate on that last point the UI for interacting with this new setup would be one of the following:

```plaintext
docker compose up
docker compose up service-a
docker compose up service-b
```

Not everyone knows about reverse history search in bash (`ctrl + r`) so scraping around for these commands or pressing up a million times at the prompt isn’t the best experience. There’s also an issue with discoverability as you’d need to know the correct spelling of each service you want to run.

It seemed pretty clear that some kind of simple UI would really reduce the barrier to entry for using this tool.

---

## Put Some UI on it

I’d dabbled in macOS apps before and honestly not enjoyed the experience too much as all my previous experience was in UIKit. Luckily SwiftUI exists so I thought I’d use it as a learning experience to make a menu bar app that essentially wraps the docker compose.

This is what that first version looked like

![simple first version](https://paul-samuels.com/images/dev-companion-01.png)

Yup it’s not going to win any design awards but the power of this abstraction can’t be overstated.

- We are completely abstracting away docker from the mobile app devs
- All available services are visible and just a button tap away from being started/stopped
- We show useful debug information like what ports things are running on
- We show the running status of each service and detect if running via docker or as .jar (read: most likely in IntelliJ)

This wasn’t a quick task and I ended up burning a lot of the midnight oil as it was an interesting task. There were lots of gotchas with handling subprocesses and their environments and coordinating lots of state events to the UI.

I was pretty happy with this step until I realised my first mistake…

---

## How do I install it?

The first lesson was that building a tool isn’t good enough; you need to make it accessible. I’m not sure if it’s common knowledge but developers tend to be very ~~lazy~~ energy efficient and having a multi step install is just a recipe for support pain.

It wasn’t long before I added an install script so people could simply run a `curl | bash` and enjoy a nicely installed app without manually

- Finding the repo
- Navigating to releases
- Downloading the binary
- Doing some gatekeeper to get it out of quarantine
- Finally be able to launch the app

Obviously whenever I shared installation instructions I did the good citizen thing and issued a disclaimer that people shouldn’t blindly trust me and pipe my remotely hosted bash script into an interpreter without reading it first.

---

## Success and Growing Pains

The tool in the form of a macOS menu bar app written in SwiftUI had a good 4 year run. New services were added and bugs were fixed and it ended up looking more like this

![second version](https://paul-samuels.com/images/dev-companion-02.png)

One fun lesson that you can see played out in the screen shot is that I added `{ {commit-sha} }`, which is populated on a release build with the git sha. This was because people have a tendency to not update things, especially when it involved manually running a `curl | bash`. Although adding some version identifier was helpful for support, it was just a plaster and the actual requirement I wish I noticed earlier is that this really needed to automatically update itself.

As you can see from the growth of services the tool is popular enough to have been updated multiple times **but** it wasn’t popular enough to help people overcome the thought of a steep learning curve required to contribute. The project was mostly maintained by myself and in all honesty I’d made some questionable architectural decisions early on and Combine heavy state management made the learning curve steeper than it needed to be.

Another detail that I’d started to notice with the way this had grown is that a long list of services is only good if the user knows how they relate. In an evolving estate where many teams contribute it may not be very obvious what services you need to run to enable you to work. You could spin up all the services but that’s sometimes overkill and hard on our poor little CPUs.

The project needed change but needed some inspiration…

---

## A Reimagining

So here I was thinking

- This codebase is a pain to maintain
- I want to better visualise how services hang together
- I want more people to be able to contribute
- I want the tool to autoupdate

My colleague (who doesn’t like being named) was working on a Compose Desktop app to help debug our apps. As they’d done all the hard work of getting a project scaffolded and off the ground I decided to see how hard it would be to port the SwiftUI tool to Compose, update the UI and incorporate it into this debug app.

This was actually a perfect opportunity to rethink architectural choices as it was an entirely different language and although I’d consider myself proficient in Kotlin I’d never done Compose so it would be a fun experience. Doing the work in this codebase also opened up the contributor pool considerably as now any Android dev could contribute and conveniently all of our iOS devs are solid Kotlin devs already. At this point, the limiting factor wasn’t UX polish - it was who felt capable of contributing.

After some fun learning and porting all the process management over I ended up with something like this

![current version](https://paul-samuels.com/images/dev-companion-03.png)

Personally I think I nailed the visualisation requirement as you can now at a glance see what services you’d need running to access different parts of the estate. The red connecting lines do actually go black to show that the apps can access services but I hastily made changes to get the anonymised screenshot and messed that detection up.

The other main thing with this tool was the autoupdate ability. I leant on having more contributors and got a colleague to write the autoupdate process logic and it works great. We even held back giving anyone access to the tool until the autoupdate was available as we just didn’t want to deal with the support.

I was feeling pretty good about this developer experience…

---

## Arrgghh SSO

Then a new requirement came in in the form of services needing to get tokens to communicate to our preprod environment. Actually this wasn’t a new thing as I’d just been avoiding doing anything about it for a few years but it was being more broadly rolled out so I couldn’t ignore it anymore.

As with most things I put off, the programming part didn’t actually end up being super complicated. Essentially when a service requires an auth token we need to invoke an external helper tool that does all the SSO magic and then inject the returned token into the relevant service when it is started. The more complicated part was figuring out how the UI would educate users that they might need to get permission to do this for each service and then signpost how/where they do that.

Once a user is setup they just press the play button like normal and everything is automatic. If the user isn’t configured then they get an ⚠️ on the service, which tells them how to resolve the misconfiguration.

---

## Make sure you are listening

Another lesson that I completely missed was tuning into the frequency of support requests. Behind the scenes to get all of the networking setup correctly to allow docker to communicate with localhost seamlessly people need to add an entry to `/etc/hosts`. This was constantly missed but quickly resolved with a trip to the docs. Unfortunately no one knew where the docs were so I’d just be dishing out the link constantly.

Instead of redirecting people to the docs and then helping them through the steps I decided to add a “Doctor” command that will diagnose common misconfigurations/issues and give remediation steps.

![doctor](https://paul-samuels.com/images/dev-companion-doctor.png)

---

## ~~Build it~~ Remove friction and they will come

I think there’s a few good victory stories on getting more contributors. Some that come to mind are:

A massive eye saver from one of the iOS devs was adding dark mode. A pretty impressive feat considering he’d never done Compose and actually didn’t just tweak colours he went full hog and made various components much more pleasing on the eyes.

![dark mode](https://paul-samuels.com/images/dev-companion-04.png)

Another capability added by a few iOS devs was the ability to install the latest development build into your simulator and launch it. This is a massive productivity boost especially for web developers who now don’t need to learn how to get the project running and interact with Xcode. There is still a requirement to have Xcode installed but after that it’s zero knowledge.

One of the QA engineers added hot reloading to make the development experience on the tool that improves developer experience better.

---

## Building a platform

Another observation that we made was that we now have a platform to build out tools that are automatically updating and installed by many people. This has lead to me sitting with another QA engineer to help them port their command line tool for finding all kinds of difficult to find adverts that people need for testing/development.

I think in total we spent about an hour getting something working together and then he’s been a one man feature factory improving things.

---

## Wrap up

I really think developer experience is important. Making and evolving tools is a nice way to show your colleagues that you value their time and hear their frustrations. It’s also been highly rewarding seeing other people get involved and seeing people interested in building a DX culture. I have no metrics on how much time this tool has saved others but I personally use it multiple times a day and the time I’ve saved alone has more than paid off for the personal investment I’ve put into this.
