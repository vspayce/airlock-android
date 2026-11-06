package com.vspayce.airlock

import android.content.Context
import java.net.URLEncoder

/** Search engines offered in the address bar. A trailing " *" marks an engine as not private. */
enum class SearchEngine(val label: String, private val template: String, val isPrivate: Boolean = true) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    KAGI("Kagi", "https://kagi.com/search?q=%s"),
    QWANT("Qwant", "https://www.qwant.com/?q=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    MOJEEK("Mojeek", "https://www.mojeek.com/search?q=%s"),
    GOOGLE("Google *", "https://www.google.com/search?q=%s", isPrivate = false);

    /** Name without the " *" marker, for placeholders. */
    val shortName: String get() = label.removeSuffix(" *")

    fun searchUrl(query: String): String =
        template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    companion object {
        val DEFAULT = DUCKDUCKGO
        private const val PREFS = "airlock_settings"
        private const val KEY = "search_engine"

        fun load(context: Context): SearchEngine {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return SearchEngine.entries.firstOrNull { it.name == name } ?: DEFAULT
        }

        fun save(context: Context, engine: SearchEngine) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, engine.name).apply()
        }
    }
}

/** Turns address-bar input into a URL: a web address, or a search with the chosen engine. */
object UrlInput {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val hostLabel = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$""")

    fun resolve(raw: String, engine: SearchEngine): String {
        val input = raw.trim()
        if (input.isEmpty()) return ""
        if (input.any { it.isWhitespace() }) return engine.searchUrl(input)
        val lower = input.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("about:")) {
            return input
        }
        if (looksLikeHost(input)) return "https://$input"
        return engine.searchUrl(input)
    }

    private fun looksLikeHost(input: String): Boolean {
        val hostAndPort = input.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = hostAndPort.substringBefore(':')
        val port = hostAndPort.substringAfter(':', "")
        if (port.isNotEmpty() && port.any { !it.isDigit() }) return false
        if (host.equals("localhost", ignoreCase = true) || ipv4.matches(host)) return true
        val labels = host.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        return labels.all { hostLabel.matches(it) } && tld.length >= 2 && tld.any { it.isLetter() }
    }
}
