# Airlock for Android

A starter Android version of Airlock, the privacy browser. It's written in Kotlin with Jetpack Compose and uses Mozilla's [GeckoView](https://mozilla.github.io/geckoview/) (the Firefox engine) to show web pages.

## What it does

- **Address bar** that takes a web address or a search. DuckDuckGo is the default. You can also pick Startpage, Brave, Kagi, Qwant, Ecosia or Mojeek from the menu (⋮). Google is there too, marked `Google *` because it isn't private.
- **Tabs**: tap the square with the tab count to see your tabs, switch between them, close them or open a new one.
- **Back, forward and reload**, plus a thin progress bar while a page loads.
- **Burn** closes every tab and erases all browsing data (cookies, cache, history, site data and permissions) with `StorageController.clearData(ALL)`.
- **Private by default** (see `app/src/main/java/com/vspayce/airlock/PrivacySettings.kt`):
  - Every tab is a private-mode session, so nothing is written to disk.
  - Enhanced Tracking Protection is set to strict, and the built-in content blocking uses the strict anti-tracking categories.
  - Cookie banners are rejected automatically.
  - Only HTTPS connections are allowed (HTTPS-Only mode).
  - Global Privacy Control is on.
  - Tracking parameters are removed from URLs (query stripping).
  - Third-party cookies are kept separate for each site, and tracker cookies are purged.

## Get the app onto your phone

**Download on your phone:** https://github.com/vspayce/airlock-android/releases/latest/download/airlock.apk

Open the downloaded file and allow your browser to install unknown apps when Android asks. Every build replaces this
download, so the same link always gets the newest one. Test builds are signed with a different key each time, so
uninstall the previous Airlock before installing a newer one.

## Building it yourself

You need JDK 17 and the Android SDK (Android Studio includes both).

```sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk   # optional, with a phone plugged in
```

## Project settings

| | |
|---|---|
| applicationId | `com.vspayce.airlock` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 36 |
| GeckoView | `org.mozilla.geckoview:geckoview:145.+` (stable channel, from `https://maven.mozilla.org/maven2/`) |
| Gradle / AGP / Kotlin | 8.14.3 / 8.10.1 / 2.1.21 |

All versions are in `gradle/libs.versions.toml`.

## License

GNU General Public License v3.0 or later; see `LICENSE`. GeckoView is Mozilla's, under the MPL-2.0; see
`THIRD-PARTY-NOTICES.md`. Security problems: see `SECURITY.md`.
