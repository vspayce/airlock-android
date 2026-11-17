package com.vspayce.airlock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime

data class Favorite(val title: String, val url: String)

/** Protection modes, as on iPhone: Standard, Strict, and Hardened (Strict + Burn whenever you leave Airlock). */
enum class Mode(val label: String, val detail: String) {
    STANDARD("Standard", "Trackers can't follow you; most sites just work"),
    STRICT("Strict", "Trackers blocked outright; a few sites may break"),
    HARDENED("Hardened", "Strict, and everything is burned whenever you leave Airlock"),
}

/** Settings kept on the phone (favorites, mode). Observable from Compose. */
class AirlockPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("airlock_settings", Context.MODE_PRIVATE)

    val favorites = mutableStateListOf<Favorite>()
    var mode by mutableStateOf(Mode.STRICT)
        private set

    init {
        mode = Mode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: Mode.STRICT
        runCatching {
            val arr = JSONArray(prefs.getString("favorites", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.startsWith("https://") || url.startsWith("http://")) favorites.add(Favorite(o.optString("title"), url))
            }
        }
    }

    fun addFavorite(title: String, url: String): Boolean {
        if (favorites.any { it.url == url }) return false
        favorites.add(Favorite(title.take(60), url))
        saveFavorites()
        return true
    }

    fun removeFavorite(f: Favorite) {
        favorites.remove(f)
        saveFavorites()
    }

    private fun saveFavorites() {
        val arr = JSONArray()
        favorites.forEach { arr.put(JSONObject().put("title", it.title).put("url", it.url)) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    fun setMode(newMode: Mode, runtime: GeckoRuntime) {
        mode = newMode
        prefs.edit().putString("mode", newMode.name).apply()
        apply(runtime)
    }

    /** Pushes the mode into Gecko's tracking protection. */
    fun apply(runtime: GeckoRuntime) {
        val cb = runtime.settings.contentBlocking
        if (mode == Mode.STANDARD) {
            cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT)
            cb.setAntiTracking(ContentBlocking.AntiTracking.DEFAULT)
            cb.setStrictSocialTrackingProtection(false)
        } else {
            cb.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            cb.setAntiTracking(ContentBlocking.AntiTracking.STRICT)
            cb.setStrictSocialTrackingProtection(true)
        }
    }
}
