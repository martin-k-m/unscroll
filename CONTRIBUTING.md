# Contributing to unscroll

Thanks for taking a look. unscroll has one hard promise — **nothing leaves the
phone**: no accounts, no network calls, no analytics. Every change is measured
against that first.

## Setup

You need a JDK (the CI uses Java 17) and the Android SDK. The Gradle wrapper
handles the rest.

```bash
git clone https://github.com/martin-k-m/unscroll
cd unscroll
./gradlew :app:assembleDebug --stacktrace
```

Install the resulting debug APK on a device, then grant the accessibility
service in Settings — the feed blocking depends on it.

## Ground rules

- **No network, ever.** The app declares no internet permission and must not
  gain one. A dependency that phones home, or any analytics or crash reporter,
  is out of scope by design. If a change appears to need the network, it is
  probably the wrong change.
- **Blocking rules live in one place.** New feeds to close belong in
  `BlockRules.kt`, matched on window changes by the accessibility service.
  Keep the matching there rather than scattering conditions through the UI.
- **The curtain is the point.** When a blocked feed is detected the screen is
  covered before anything underneath can register a flick. A change to that path
  should preserve how quickly the curtain lands.
- **State stays local.** Time budgets and settings are stored on-device. Nothing
  is synced.

## Before you open a pull request

```bash
./gradlew :app:assembleDebug --stacktrace
```

Describe what you tested on which Android version and which apps (Reels, Shorts,
TikTok, Spotlight), since the accessibility behaviour varies by device.

## Reporting bugs

Open an issue with your device model and Android version, the app whose feed did
not get blocked, and what happened. A short screen recording is worth a lot for
a timing-sensitive bug like this.
