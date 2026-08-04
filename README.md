# unscroll

An Android app that closes short-video feeds for you. Open Reels, Shorts, TikTok or Spotlight
and it backs you out within a moment, with a short curtain over the screen so nothing you flick
lands on the feed underneath. It also enforces daily time budgets per app.

Kotlin, Jetpack Compose, no accounts, no network calls, no analytics. Nothing leaves the phone.

## What it actually does

**Feed blocking.** An accessibility service watches window changes. When the visible screen
matches one of the rules in [`BlockRules.kt`](app/src/main/java/me/blinkdev/unscroll/block/BlockRules.kt)
it presses Back. If you land straight back on the same feed (some apps make it the start
destination) it goes Home instead.

Rules ship for:

| Surface | Host app |
| --- | --- |
| YouTube Shorts | com.google.android.youtube |
| Instagram Reels, Instagram Explore | com.instagram.android |
| Facebook Reels | com.facebook.katana |
| Snapchat Spotlight | com.snapchat.android |
| Reddit video feed | com.reddit.frontpage |
| TikTok, whole app | com.zhiliaoapp.musically |

Each one is a toggle. Explore and the TikTok whole-app block are off by default.

**Daily limits.** Optional. Set a budget per app and the service sends you Home once the day's
foreground time crosses it. Time comes from `UsageStatsManager` events, computed from local
midnight rather than the system's daily buckets, which do not line up with midnight.

**Escape hatch.** A five minute pause, and nothing longer. If you want it off for real, you turn
the service off in Accessibility settings, which takes enough taps to count as a decision.

## Install

Grab the APK from the latest [build run](../../actions/workflows/build.yml) artifact, or build it:

```bash
./gradlew :app:assembleDebug
```

Then on the device:

1. Open unscroll, tap **Enable unscroll blocker**, find unscroll under Installed apps and turn it on.
2. Tap **Grant usage access** only if you want daily limits.

Android may warn about restricted settings when installing outside the Play Store. Long-press
the app in Settings > Apps and allow restricted settings if the accessibility toggle is greyed out.

## Keeping the rules working

The view ids in `BlockRules.kt` come from the host apps and change when they ship redesigns. If a
feed stops being blocked, dump the ids while the feed is open:

```bash
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
```

Look for the container that holds the video pager and add its `resource-id` to the surface.

Text matching is the fallback and it only fires when the matching node is *selected*, so a tab
labelled "Reels" sitting in the bottom bar of every screen does not trigger a block.

## Layout

```
app/src/main/java/me/blinkdev/unscroll/
  MainActivity.kt          Compose entry point
  block/BlockRules.kt      what counts as a short-video feed
  block/ScrollBlockerService.kt  the accessibility service
  block/BlockOverlay.kt    the curtain drawn while backing out
  data/Settings.kt         DataStore-backed preferences
  usage/UsageTracker.kt    per-app foreground time for today
  ui/                      screen, view model, permission helpers
```

minSdk 26, targetSdk 35.

## Caveats

- Accessibility services are the only way to do this without root, and Android will show a
  persistent reminder that unscroll can observe your screen. It reads view ids and selected tab
  labels, nothing else, and writes nothing off-device.
- Some launchers kill accessibility services aggressively. Exempt unscroll from battery
  optimisation if it stops firing.
- Detection is a heuristic. It errs towards blocking, so an occasional false positive on an
  adjacent screen is expected. Turn that surface off if it annoys you more than it helps.

## Licence

MIT.
