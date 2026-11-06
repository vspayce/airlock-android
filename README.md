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

Each push to GitHub builds a debug APK automatically, using `.github/workflows/android.yml`.

1. **Download the build.** On GitHub, open the repo's **Actions** tab and click the latest successful **Android debug build** run. Scroll to **Artifacts** and download **airlock-debug-apk**. You need to be signed in to GitHub. You can also start a build yourself from the workflow page with **Run workflow**.
2. **Unzip it.** GitHub delivers the artifact as a `.zip`. Unzip it to get `app-debug.apk`. You can do this on a computer and copy the APK to the phone (USB, Google Drive, email to yourself and so on), or download and unzip it straight on the phone with the Files app.
3. **Allow "install unknown apps".** Android only installs apps from outside the Play Store after you give permission to the app that opens the APK (for example Files, Chrome or Drive). Go to **Settings → Apps → Special app access → Install unknown apps**, choose that app, and turn on **Allow from this source**. The exact menu names differ a little between phone makers. Many phones also just ask you the first time you open an APK.
4. **Open the APK.** Tap `app-debug.apk` and choose **Install**. Play Protect may warn you about an unrecognised app. Choose **Install anyway** (it may be under "More details"). Airlock then shows up in your app drawer.

**Installing a newer build:** CI signs every debug build with a new throwaway key. That means a new APK usually can't install over the old one ("App not installed" or "package conflicts"). Uninstall the old Airlock first. Doing so loses nothing, because Airlock doesn't keep browsing data anyway.

The APK contains `arm64-v8a` code (nearly every modern phone) and `x86_64` code (the emulator). It's large, around 100 MB, because the whole Gecko engine is inside it.

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
