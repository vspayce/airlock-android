# Third-party notices

Airlock for Android is licensed under the GNU General Public License, version 3 or later (see `LICENSE`).
It is built with the following work by others, downloaded at build time.

| Component | License | Source |
|---|---|---|
| GeckoView (Mozilla's Firefox engine) | MPL-2.0 | https://hg.mozilla.org/mozilla-central / maven.mozilla.org |
| AndroidX Core, Activity, Jetpack Compose (UI, Foundation, Material 3, icons) | Apache-2.0 | https://developer.android.com/jetpack |
| Kotlin standard library | Apache-2.0 | https://kotlinlang.org |

GeckoView's tracking protection, cookie-banner handling and query-parameter stripping lists are Mozilla's,
used under the MPL-2.0. MPL-2.0 files stay under the MPL; combining them with GPL-3.0 code is permitted
(MPL-2.0 section 3.3).

## Readability (bundled)

`app/src/main/assets/extension/Readability.js` is Mozilla's Readability library (https://github.com/mozilla/readability),
Copyright (c) 2010 Arc90 Inc and Mozilla, licensed under the Apache License 2.0. It finds the article on a page for
Reader view and clipping.
