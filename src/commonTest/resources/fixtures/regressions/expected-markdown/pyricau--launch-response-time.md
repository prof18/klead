![Launch Response Time](https://cdn.hashnode.com/res/hashnode/image/upload/v1632516499583/v_3HLtM0x.jpeg)

> Header image: *Caterpillar* [by Romain Guy](https://www.flickr.com/photos/romainguy/4874188049/).

In 2020 I started a series of Android performance deep dive blogs: [Android Vitals](https://dev.to/pyricau/series/7827). I'm currently working on releasing an Open Source library that helps with performance monitoring in production, and today I want to write a high level summary of what I've learnt and look at how we can properly measure **Launch Response Time** in production.

> Note: until now I've been writing at [dev.to/pyricau](https://dev.to/pyricau), this time I'm trying out Hashnode to see how it feels. Feedback welcome on whether I should continue here or back there.

## Terminology

- **Launch Response Time** is the time from when the system triggers *App Launch* to when the display has rendered the first frame of the window of the activity brought to the foreground.
- **App Launch** is what happens when an Android application that wasn't visible is brought to the foreground. This can happen when an intent is fired to launch one of your app activities (e.g. when a user taps on your app icon in the launcher) or when an activity task is brought back from Recents. You can have an *App Launch* without a corresponding *Process Start*.
- **Process Start** is what happens when the `system_server` process tells your app process to start loading your APK code and resources and calls `Application.onCreate()` ([learn more](https://dev.to/pyricau/android-vitals-diving-into-cold-start-waters-5hi6)). This happens only once per process, and usually starts with a fork of the Zygote process, [but not always](https://dev.to/pyricau/android-vitals-when-did-my-app-start-24p4#pre-forking). You can have a *Process Start* not triggered by an *App Launch*.

## App Launch or App Startup?

*App Launch* is often called *App Startup*, but I like to avoid the verb *Start*, as bringing back an app from recent isn't really starting anything from a user standpoint.

## Process Start

## Start time

In [When did my app start? ⏱](https://dev.to/pyricau/android-vitals-when-did-my-app-start-24p4), I concluded that process start time should ideally be measured when your app code and resources load. You need to use a different approach depending on the API level:

- Up to API 24: Use the class load time of a content provider (`Process.getStartUptimeMillis()` not available yet).
- API 24 - API 27: Use [Process.getStartUptimeMillis()](https://developer.android.com/reference/android/os/Process.html#getStartUptimeMillis\().
- API 28 and beyond: `Process.getStartUptimeMillis()` is sometimes way off. Use [Process.getStartUptimeMillis()](https://developer.android.com/reference/android/os/Process.html#getStartUptimeMillis\() but filter out weird values (e.g. more than 1 min to get to `Application.onCreate()`) and fallback to the time `ContentProvider.onCreate()` is called.

## Process start for app launch

In [Is this a cold start? 🦋](https://dev.to/pyricau/android-vitals-is-this-a-cold-start-3m44) and [Why did my process start? 🌄](https://dev.to/pyricau/android-vitals-why-did-my-process-start-4d0e), I established how to detect that a process start is for an app launch:

- Process importance on process start (measured as early as possible) is [IMPORTANCE\_FOREGROUND](https://developer.android.com/reference/android/app/ActivityManager.RunningAppProcessInfo#IMPORTANCE_FOREGROUND).
- The first activity is created before a Handler post sent from `Application.onCreate()` or earlier is dequeued ([learn more](https://dev.to/pyricau/android-vitals-is-this-a-cold-start-3m44#traditional-approach)).

In my experience it's also helpful to track different kinds of process starts / cold app launches to split the data:

- Detect whether this is the first process start after first install. This matters because first start might involve more work (e.g. copying APK assets to disk), and it's also the first time a user interacts with your app, so first impression matters!
- Similarly, detecting whether this is a first process start after the user clearing data. This is a strong signal that something went wrong.
- Detect whether this is the first process start after app version upgrade, which might trigger additional db migration work.
- Detect app specific states such as whether the app starts in a logged in or logged out state. Logged in often involves a lot more startup work.
- Detect whether the first launched activity has a bundle or not, i.e. whether the process was killed and the activity task is being restored.

## App Launch

At the beginning of this article, I defined *App Launch* as what happens when an Android application that wasn't visible is brought to the foreground. In practice, this means that:

1. There was 0 visible activity, i.e. either:
	- The process wasn't alive.
		- The process was alive but had never started any activity.
		- The process was alive and any started activity had been previously stopped or destroyed.
2. There is now at least one activity in foreground, i.e. resumed.

## App Launch start time

**Launch Response Time** starts the time when the system triggers *App Launch*. I'm intentionally ignoring what happens right after a user taps a launcher icon because this is beyond our control. There are different types of *App Launch*, so their start time must be measured differently.

### Cold Launch

A **Cold Launch** is what happens when the *App Launch* requires a *Process Start*. In the **Process Start** section above, I already outlined how to detect a *Cold Launch* and measure its start time.

### Hot Launch

A **Hot Launch** is what happens when the process was alive and the activity that is being resumed needs to first be started, i.e. was previously stopped but not destroyed. As far as I know, the best way we have to measure its start time is by recording the time of the call to [ActivityLifecycleCallbacks.onActivityPreStarted()](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks#onActivityPreStarted\(android.app.Activity)).

### Warm Launch

A **Warm Launch** is what happens when the process was alive and the activity that is being resumed needs to first be created, i.e. it was previously destroyed or never created. As far as I know, the best way we have to measure its start time is by recording the time of the call to [ActivityLifecycleCallbacks.onActivityPreCreated()](https://developer.android.com/reference/android/app/Application.ActivityLifecycleCallbacks#onActivityPreCreated\(android.app.Activity)).

## App Launch end time

**Launch Response Time** ends when the display has rendered the first frame of the window of the activity brought to the foreground. I'm ignoring the preview window rendering (can't use the app yet) and considering only the first drawn frame, but some apps might prefer waiting for extra async loading to finish.

We want to know when the display has rendered the first frame of the window of the resumed activity. Here's how we can do it:

- From `onResume()` we can register a OnPreDrawListener.
- Once we know the first draw for that window is happening, we have two options:
	- I explained in [Tap Response Time: Jetpack Navigation 🗺](https://dev.to/pyricau/tap-response-time-jetpack-navigation-4738) that starting with API 24 we can rely on [FrameMetrics.TOTAL\_DURATION](https://developer.android.com/reference/android/view/FrameMetrics#TOTAL_DURATION) to know how long the frame takes to render and be issued to the display subsystem (i.e. when the render thread is done swapping the frame buffer). In practice we need API 26 because that's when [FrameMetrics.INTENDED\_VSYNC\_TIMESTAMP](https://developer.android.com/reference/android/view/FrameMetrics#INTENDED_VSYNC_TIMESTAMP) was added. `TOTAL_DURATION` measures time from the intended vsync start, not the actual vsync start, so without this we'd end up measuring a longer launch time.
		- Before API 24 (or 26) we can leverage [Handler.sendMessageAtFrontOfQueue()](https://developer.android.com/reference/android/os/Handler#sendMessageAtFrontOfQueue\(android.os.Message) from within the `onPreDraw()` callback to measure the end time, with the assumption that the front of queue message will be processed right after the current frame is done rendering. Starting with API 22, we should also call [Message.setAsynchronous()](https://developer.android.com/reference/android/os/Message#setAsynchronous\(boolean) to avoid that message being delayed by a Looper synchronization barrier.

## Conclusion

I hope you enjoyed this summary of a year of deep dives! A huge thank you to **Romain Guy** for helping me make sense of all the display stuff, **Chet Haase** and **John Reck** for answering my questions about [JankStats](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/;drc=523d7a11e46390281ed3f77893671730cd6edb98) implementation details, and **Jun** for helping me realize my deep dives were in dire need of a high level summary and for his great feedback on this blog before I hit publish.
