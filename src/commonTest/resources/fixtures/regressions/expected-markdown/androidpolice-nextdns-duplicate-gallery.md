![Wi-Fi router with a blocked Google logo above it.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/wi-fi-router-with-a-blocked-google-logo-above-it.jpg?&fit=crop&w=1600&h=900)

Google likes to track everything we do, and its [background app tracking](https://www.androidpolice.com/fixed-android-phones-overheating-issue-changing-background-app-setting/) practices can severely affect your phone's performance.

My phone was constantly draining its battery even when idle, and after I recently changed the battery, it got quite frustrating.

Whenever I checked the battery stats, there were always two culprits: Android System and Google Play Services.

I dug a little deeper and found a solution that everyone can use in a matter of minutes.

## Google's background tracking servers can kill your phone's battery faster

### Leading to more battery charge cycles

![Android phone showing a Private DNS setup with NextDNS.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/android-phone-showing-a-private-dns-setup-with-nextdns.png?q=49&fit=contain&w=705&h=397&dpr=2)

![A Google Pixel phone appears beside a low battery and an Android system error icon.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/07/a-google-pixel-phone-appears-beside-a-low-battery-and-an-android-system-error-icon.png?q=49&fit=contain&w=705&h=397&dpr=2)

![Android Private DNS settings with ad-blocking icons and a DNS shield.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/android-private-dns-settings-with-ad-blocking-icons-and-a-dns-shield.png?q=49&fit=contain&w=705&h=397&dpr=2)

![Hand holding a phone with a large battery saver icon.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/07/hand-holding-a-phone-with-a-large-battery-saver-icon.png?q=49&fit=contain&w=705&h=397&dpr=2)

![The Android mascot holding a DNS Provider sign with floating key icons in the background.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/03/the-android-mascot-holding-a-dns-provider-sign-with-floating-key-icons-in-the-background.png?q=49&fit=contain&w=705&h=397&dpr=2)

I've had my phone lose battery backup countless times, even though I haven't been using it that much.

My phone often ran hotter, and I had to charge the battery two or more times a day.

What was odd was that I replaced my OnePlus 9 Pro's battery two months ago, which made me suspicious.

After finding that third-party apps weren't responsible for the battery drain, I concluded it was Google's constant need to track me.

While conventional solutions, like restricting background data for apps I didn't use much, helped, the battery drain problem persisted.

The first solution that came to mind was to use Pi-Hole on a Raspberry Pi and add Google domains to the denylist.

But that required me first to purchase a Raspberry Pi and get it up and running.

After a little research, I was set on using a free Private DNS service to block these trackers, since it didn't require additional hardware.

It was simple to use, and I got it running in a few minutes. That's when I thought of using [NextDNS' private DNS service](https://www.androidpolice.com/adguard-nextdns-private-dns-battery-life/) on my router.

I was already using it to ditch AdGuard's subscription and had blocked over 9,000 queries to save battery life.

It was time for all my devices to get the same benefits.

After I used the service on my phone, I found some culprit domains on the analytics page in the NextDNS manager app that were constantly tracking me:

- **http://app-measurement.com**
- **http://firebase-settings.crashlytics.com**
- Many services with the **.googleapis.com** domain

I figured that, for Google to track me constantly, my phone needs to make frequent check-ins, which in turn would keep the SoC (System on Chip) and all radios active.

This would cause overheating, resulting in rapid battery drain.

After testing NextDNS on my phone, I installed it on my router so that other Android devices in my household didn't suffer the same fate.

## How to install NextDNS on your home Wi-Fi router

### Save battery across all your devices

![A Wi-Fi router next to the Wi-Fi icon](https://static0.anpoimages.com/wordpress/wp-content/uploads/2025/01/use-guest-wifi-network-feature.jpg?q=49&fit=contain&w=705&h=397&dpr=2)

![Wi-Fi router with a warning symbol indicating a network problem.](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/wi-fi-router-with-a-warning-symbol-indicating-a-network-problem.png?q=49&fit=contain&w=705&h=397&dpr=2)

![NextDNS showing domains that were blocked](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/nextdns-domains-blocked.jpg?q=49&fit=contain&w=705&h=397&dpr=2)

![NextDNS allowlist showing Google services](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/nextdns-allowlist-google-domains.jpg?q=49&fit=contain&w=705&h=397&dpr=2)

![NextDNS screenshot showing over 9000 queries blocked](https://static0.anpoimages.com/wordpress/wp-content/uploads/2026/08/nextdns-queries-blocked.jpg?q=49&fit=contain&w=705&h=397&dpr=2)

I use everything from an Android Tablet to an [Android DAP](https://www.androidpolice.com/android-dap-hiby-r4-audio-music/), so it was essential to save battery life on each.

If Google was draining my phone's battery, I'm sure it was affecting my tablet, other phones, and e-book readers.

The most logical solution was to use NextDNS on my router instead of changing private DNS settings on each device.

Setting up NextDNS is straightforward. You need to register an account on the website and copy the unique IPv6 address.

NextDNS has an easy [setup guide](https://my.nextdns.io/bbba55/setup) you can use. Alternatively, you can use the IPv4 address to keep it simple.

Here are the steps you need to install NextDNS on your router:

1. Log in to your router by visiting its gateway IP address in a browser. Most routers' IP gateway addresses are **192.168.1.1** or **192.168.0.1**.
2. Find sections named **Internet**, **WAN**, or **Network Setup**.
3. Turn off the **automatic DNS** being used by your ISP (internet service provider).
4. Paste the **IPv4 addresses** into the primary and secondary fields from your NextDNS dashboard. It will look something like **45.90.28.XX** and **45.90.30.XX**.

After I pasted NextDNS' IPv4 addresses, I was set. Every one of my devices now blocked Google's background tracking.

However, I needed to add some services to my allowlist. Certain apps, such as WhatsApp, use some of Google's services for push notifications.

Essential services via the Play Store, such as device registration, can also be affected. So it's advisable to add certain Google domains to the allowlist to prevent issues.

I added the following domains to my allowlist to keep essential services intact on every device:

- **mtalk.google.com**
- **android.clients.google.com**

After installing any private DNS service, you might encounter false positives and will need to add domains to the allowlist manually.

I recommend adding the domains of services you use to the allowlist to keep every device running normally. This would include services like iCloud and other Apple domains if you use any of their products.

I recommend trying this method, as NextDNS' service is free for up to 100,000 queries on unlimited devices.

You can upgrade to the Pro plan if the service works for you, as it unlocks unlimited queries across all devices in your home.

Alternatively, if you are subscribed to AdGuard, you can use the AdGuard Home service and follow the above steps.

## Blocking Google's tracking servers was a lifesaver

After using a [private DNS service](https://www.androidpolice.com/private-dns-on-android-is-easy-to-ignore-but-i-use-it/) on my phone, I've seen measurable results on the router.

My phone doesn't die as fast as it did before blocking Google's tracking servers, and Google is not tracking me at the same time.

Whether you use it to de-Google your life or to save battery life, using a private DNS service is not only recommended, but essential in 2026.
