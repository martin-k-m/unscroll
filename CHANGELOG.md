# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims
to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Nothing has been tagged or released yet. `0.1.0` is the `versionName` currently
in `app/build.gradle.kts`; the only builds that exist are the debug APKs
produced by CI.

## Unreleased

### Added
- `scripts/check-no-network.sh`, run by CI, which fails the build if the app
  gains `android.permission.INTERNET` or references a networking API. CI checks
  the merged manifest, so a permission contributed by a dependency is caught.
  The no-network claim previously rested on nothing but the file's contents.

### Fixed
- The README claimed Instagram Explore was off by default. It is on; only the
  TikTok whole-app block is off.
- The install section no longer implies the CI artifact is a release. It is a
  debug APK, it needs a GitHub login to download, and it expires after 90 days.

## 0.1.0

### Added
- Feed blocking through an accessibility service, with rules for YouTube
  Shorts, Instagram Reels and Explore, Facebook Reels, Snapchat Spotlight, the
  Reddit video feed, and TikTok as a whole app. Each surface is a toggle.
- An overlay drawn while backing out, so a flick landing during the transition
  does not reach the feed underneath.
- Optional daily time budgets per app, computed from `UsageStatsManager` events
  relative to local midnight rather than the system's daily buckets.
- A five minute pause, and nothing longer. Turning blocking off for real means
  disabling the service in Accessibility settings.
- Jetpack Compose UI, DataStore-backed settings, no accounts, no network
  permission, no analytics.
