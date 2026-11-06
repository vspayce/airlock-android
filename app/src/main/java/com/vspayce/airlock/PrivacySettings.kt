package com.vspayce.airlock

import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Airlock's private-by-default Gecko configuration.
 *
 * Everything here is applied once, when the single GeckoRuntime is created.
 */
object PrivacySettings {

    private fun contentBlocking(): ContentBlocking.Settings =
        ContentBlocking.Settings.Builder()
            // Built-in content blocking: ads, analytics, social, cryptominers,
            // fingerprinters and content trackers (the "strict" category set).
            .antiTracking(ContentBlocking.AntiTracking.STRICT)
            .strictSocialTrackingProtection(true)
            // Enhanced Tracking Protection: strict.
            .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            // Total Cookie Protection: third-party cookies are partitioned per site.
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            // Periodically purge cookies from known trackers.
            .cookiePurging(true)
            // Cookie banners: automatically click "reject" where Gecko has a rule.
            .cookieBannerHandlingMode(ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT)
            .cookieBannerHandlingModePrivateBrowsing(ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT)
            // Strip tracking query parameters (utm_*, fbclid, gclid, ...) from URLs.
            .queryParameterStrippingEnabled(true)
            .queryParameterStrippingPrivateBrowsingEnabled(true)
            .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
            .build()

    fun runtimeSettings(): GeckoRuntimeSettings =
        GeckoRuntimeSettings.Builder()
            .contentBlocking(contentBlocking())
            // HTTPS-Only mode for every session, not just private ones.
            .allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)
            // Send the Global Privacy Control (Sec-GPC: 1) signal.
            .globalPrivacyControlEnabled(true)
            .aboutConfigEnabled(false)
            .remoteDebuggingEnabled(false)
            .build()

    /** Every tab is a private-mode session: nothing is persisted to disk. */
    fun sessionSettings(): GeckoSessionSettings =
        GeckoSessionSettings.Builder()
            .usePrivateMode(true)
            .useTrackingProtection(true)
            .allowJavascript(true)
            .build()
}
