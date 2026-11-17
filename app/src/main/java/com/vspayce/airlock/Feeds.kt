package com.vspayce.airlock

import android.content.Context
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Feed(val id: String, val url: String, val title: String, val watchesPage: Boolean, val folder: String? = null, val error: String? = null)

data class FeedItem(
    val id: String,
    val feed: String,
    val title: String,
    val link: String,
    val date: Long,
    val summary: String,
    val minutes: Int?,
)

/** Fetches for Feeds and Site info: HTTPS only, public addresses only, no cookies, at most 5 MB. */
object SafeFetch {
    private fun isPublic(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost") || !h.contains('.')) return false
        val addrs = runCatching { InetAddress.getAllByName(h) }.getOrNull() ?: return false
        return addrs.isNotEmpty() && addrs.none { a ->
            val b = a.address
            a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isAnyLocalAddress || a.isMulticastAddress ||
                (b.size == 4 && (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64) ||   // 100.64.0.0/10
                (a is Inet6Address && (b[0].toInt() and 0xfe) == 0xfc)                                     // fc00::/7
        }
    }

    fun get(url: String, accept: String = "*/*", limit: Int = 5_000_000): Pair<ByteArray, String> {
        var current = url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        repeat(6) {
            val u = URL(current)
            if (u.protocol != "https" || !isPublic(u.host)) error("Airlock only fetches from public HTTPS sites")
            val conn = u.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:145.0) Gecko/145.0 Firefox/145.0")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    current = URL(u, conn.getHeaderField("Location") ?: error("Bad redirect")).toString()
                    return@repeat
                }
                if (code !in 200..299) error("HTTP $code")
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > limit) error("Too large")
                    }
                }
                return out.toByteArray() to current
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects")
    }
}

object FeedParser {
    fun plain(html: String, limit: Int = 220): String {
        var t = html.replace(Regex("(?is)<(script|style)\\b.*?</\\1>"), " ")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = android.text.Html.fromHtml(t, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        t = t.replace(Regex("\\s+"), " ").trim()
        return if (t.length <= limit) t else t.take(limit - 1).trimEnd() + "…"
    }

    private fun date(s: String?): Long? {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return null
        return runCatching { ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() * 1000 }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(v).toEpochSecond() * 1000 }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(v.replace("GMT", "+0000").replace("UTC", "+0000"),
                DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.US)).toEpochSecond() * 1000 }.getOrNull()
    }

    private fun minutes(body: String): Int? {
        val words = plain(body, 1_000_000).split(' ').count { it.isNotBlank() }
        return if (words > 120) maxOf(1, words / 230) else null
    }

    private fun resolve(base: String, link: String): String = runCatching { URI(base).resolve(link.trim()).toString() }.getOrDefault(link)

    data class Item(val id: String, val title: String, val link: String, val date: Long?, val summary: String, val minutes: Int?)

    /** (title, items), or null if this isn't a feed. */
    fun parse(data: ByteArray, base: String): Pair<String, List<Item>>? {
        val head = String(data, 0, minOf(200, data.size), Charsets.UTF_8).trimStart()
        if (head.startsWith("{")) {
            val j = runCatching { JSONObject(String(data, Charsets.UTF_8)) }.getOrNull() ?: return null
            val arr = j.optJSONArray("items") ?: return null
            val items = (0 until minOf(arr.length(), 200)).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val body = o.optString("content_html").ifBlank { o.optString("content_text") }.ifBlank { o.optString("summary") }
                Item(o.optString("id").ifBlank { o.optString("url") }, plain(o.optString("title"), 300),
                    resolve(base, o.optString("url")), date(o.optString("date_published")), plain(body), minutes(body))
            }
            return j.optString("title") to items.filter { it.link.startsWith("http") }
        }
        if (head.lowercase().let { it.contains("<!doctype html") || it.contains("<html") }) return null
        return runCatching { parseXml(data, base) }.getOrNull()
    }

    private fun parseXml(data: ByteArray, base: String): Pair<String, List<Item>>? {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(data.inputStream(), null)
        var kind: String? = null
        var feedTitle = ""
        val items = mutableListOf<Item>()
        var fields: MutableMap<String, String>? = null
        var link = ""
        var depth = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) {
                if (p.eventType == XmlPullParser.END_TAG) {
                    depth--
                    val name = p.name.substringAfter(':').lowercase()
                    if ((name == "item" || name == "entry") && fields != null) {
                        val f = fields!!
                        val body = f["encoded"] ?: f["content"] ?: f["description"] ?: f["summary"] ?: ""
                        val l = link.ifBlank { f["link"].orEmpty() }
                        items.add(Item(f["guid"] ?: f["id"] ?: l, plain(f["title"].orEmpty(), 300), resolve(base, l),
                            date(f["pubdate"] ?: f["date"] ?: f["updated"] ?: f["published"]),
                            plain(f["description"] ?: f["summary"] ?: f["content"] ?: ""), minutes(body)))
                        fields = null
                        if (items.size >= 200) break
                    }
                }
                continue
            }
            depth++
            val name = p.name.substringAfter(':').lowercase()
            if (kind == null) {
                kind = name
                if (kind !in setOf("rss", "rdf", "feed")) return null
                continue
            }
            when {
                name == "item" || name == "entry" -> { fields = mutableMapOf(); link = "" }
                fields != null && name == "link" && kind == "feed" -> {
                    val rel = p.getAttributeValue(null, "rel") ?: "alternate"
                    if (rel == "alternate" && link.isBlank()) link = p.getAttributeValue(null, "href").orEmpty()
                }
                fields != null -> {
                    if (!fields!!.containsKey(name)) {
                        val text = runCatching { p.nextText() }.getOrDefault("")
                        depth--
                        fields!![name] = text
                    }
                }
                name == "title" && feedTitle.isBlank() -> { feedTitle = runCatching { p.nextText() }.getOrDefault(""); depth-- }
            }
        }
        if (kind == null) return null
        return plain(feedTitle, 80) to items.filter { it.link.startsWith("http") }
    }

    fun discover(data: ByteArray, base: String): String? {
        val text = String(data, 0, minOf(400_000, data.size), Charsets.UTF_8)
        for (m in Regex("(?is)<link\\b[^>]*>").findAll(text)) {
            val tag = m.value.lowercase()
            if ("alternate" in tag && listOf("application/rss+xml", "application/atom+xml", "application/feed+json").any { it in tag }) {
                val href = Regex("""(?i)href\s*=\s*["']([^"']+)["']""").find(m.value)?.groupValues?.get(1) ?: continue
                return resolve(base, android.text.Html.fromHtml(href, 0).toString())
            }
        }
        return null
    }

    private val skip = listOf("/tag/", "/tags/", "/category/", "/author/", "/login", "/signin", "/signup", "/subscribe",
        "/account", "/search", "/about", "/contact", "/privacy", "/terms", "/cookie", "/advertis", "/careers", "/page/", "/feed", "/rss")

    /** Headlines on a page that has no feed. */
    fun page(data: ByteArray, base: String): Pair<String, List<Item>> {
        var text = String(data, 0, minOf(700_000, data.size), Charsets.UTF_8)
        for (tag in listOf("script", "style", "noscript", "svg", "template", "nav", "footer", "header", "aside")) {
            text = text.replace(Regex("(?is)<$tag\\b.*?</$tag>"), " ")
        }
        val baseUri = URI(base)
        val site = (baseUri.host ?: "").lowercase().removePrefix("www.")
        val items = mutableListOf<Item>()
        val seen = HashSet<String>()
        fun add(href: String, raw: String, minLen: Int) {
            val url = runCatching { baseUri.resolve(android.text.Html.fromHtml(href, 0).toString().trim()) }.getOrNull() ?: return
            val host = (url.host ?: return).lowercase()
            if (url.scheme !in setOf("http", "https") || (host != site && !host.endsWith(".$site"))) return
            val path = (url.path ?: "").lowercase()
            if (path.length <= 1 || path == baseUri.path || skip.any { path.startsWith(it) }) return
            val title = plain(raw, 300)
            if (title.length < minLen || title.split(' ').size < 2 || !seen.add(path)) return
            items.add(Item("page:$path", title, URI("https", url.authority, url.path, url.query, null).toString(), null, "", null))
        }
        for (m in Regex("(?is)<h[1-4]\\b[^>]*>(.{0,1200}?)</h[1-4]>").findAll(text)) {
            val a = Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""").find(m.groupValues[1]) ?: continue
            add(a.groupValues[1], a.groupValues[2], 8)
        }
        if (items.size < 3) {
            for (m in Regex("""(?is)<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.{0,800}?)</a>""").findAll(text)) {
                if (m.groupValues[1].contains('-') || m.groupValues[1].any { it.isDigit() }) add(m.groupValues[1], m.groupValues[2], 24)
            }
        }
        val title = Regex("(?is)<title\\b[^>]*>(.{0,400}?)</title>").find(String(data, 0, minOf(200_000, data.size)))?.groupValues?.get(1)
        return (title?.let { plain(it, 80) }?.ifBlank { null } ?: site) to items.take(40)
    }
}

/** Followed feeds, their articles, read state and Read Later. Saved in the app's private storage. */
class FeedStore(private val context: Context) {
    val feeds = mutableStateListOf<Feed>()
    val items = mutableStateListOf<FeedItem>()
    val read = mutableStateListOf<String>()
    val later = mutableStateListOf<String>()
    var busy by mutableStateOf(false)
    private val file get() = File(context.filesDir, "feeds.json")

    init {
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONArray("feeds")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                    feeds.add(Feed(it.getString("id"), it.getString("url"), it.optString("title"), it.optBoolean("page"),
                        it.optString("folder").ifBlank { null }))
                }
            }
            o.optJSONArray("items")?.let { a ->
                for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                    items.add(FeedItem(it.getString("id"), it.getString("feed"), it.optString("title"), it.optString("link"),
                        it.optLong("date"), it.optString("summary"), it.optInt("minutes").takeIf { m -> m > 0 }))
                }
            }
            o.optJSONArray("read")?.let { a -> for (i in 0 until a.length()) read.add(a.optString(i)) }
            o.optJSONArray("later")?.let { a -> for (i in 0 until a.length()) later.add(a.optString(i)) }
        }
    }

    fun save() {
        val o = JSONObject()
        o.put("feeds", JSONArray().apply {
            feeds.forEach { put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title).put("page", it.watchesPage).put("folder", it.folder ?: "")) }
        })
        o.put("items", JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.id).put("feed", it.feed).put("title", it.title).put("link", it.link)
                .put("date", it.date).put("summary", it.summary).put("minutes", it.minutes ?: 0)) }
        })
        o.put("read", JSONArray(read.takeLast(5000)))
        o.put("later", JSONArray(later.toList()))
        file.writeText(o.toString())
    }

    fun isFollowing(url: String) = feeds.any { it.url.trimEnd('/').equals(url.trimEnd('/'), true) }

    /** Follows a feed, finds one on the page, or watches the page's headlines. Call off the main thread; returns the title. */
    fun followBlocking(input: String): Pair<Feed, List<FeedParser.Item>> {
        val url = if ("://" in input) input else "https://$input"
        val (data, final) = SafeFetch.get(url, "application/rss+xml, application/atom+xml, application/feed+json, text/html;q=0.8")
        FeedParser.parse(data, final)?.let { (t, items) ->
            return Feed(UUID.randomUUID().toString(), final, t.ifBlank { URI(final).host ?: final }, false) to items
        }
        FeedParser.discover(data, final)?.let { found ->
            val (fdata, ffinal) = SafeFetch.get(found, "application/rss+xml, application/atom+xml, application/feed+json")
            FeedParser.parse(fdata, ffinal)?.let { (t, items) ->
                return Feed(UUID.randomUUID().toString(), ffinal, t.ifBlank { URI(final).host ?: final }, false) to items
            }
        }
        val (t, items) = FeedParser.page(data, final)
        if (items.size < 2) error("No feed or headlines found there")
        return Feed(UUID.randomUUID().toString(), final, t, true) to items
    }

    /** Main thread: adds a followed feed. */
    fun add(feed: Feed, parsed: List<FeedParser.Item>) {
        if (isFollowing(feed.url)) return
        feeds.add(feed)
        merge(feed, parsed)
        save()
    }

    fun unfollow(feed: Feed) {
        feeds.remove(feed)
        items.removeAll { it.feed == feed.id }
        save()
    }

    fun setFolder(feed: Feed, folder: String?) {
        val i = feeds.indexOf(feed)
        if (i >= 0) feeds[i] = feed.copy(folder = folder?.trim()?.take(40)?.ifBlank { null })
        save()
    }

    fun merge(feed: Feed, parsed: List<FeedParser.Item>) {
        val existing = items.filter { it.feed == feed.id }.associateBy { it.id }.toMutableMap()
        val now = System.currentTimeMillis()
        parsed.forEachIndexed { n, it ->
            val id = feed.id + "|" + it.id.ifBlank { it.link }
            val old = existing[id]
            existing[id] = FeedItem(id, feed.id, it.title.ifBlank { it.link }, it.link, it.date ?: old?.date ?: (now - n * 1000L),
                it.summary, it.minutes)
        }
        val ranked = existing.values.sortedByDescending { it.date }
        val keep = ranked.take(60) + ranked.drop(60).filter { it.id in later }
        items.removeAll { it.feed == feed.id }
        items.addAll(keep)
    }

    /** Refreshes every feed off the main thread; [post] runs a block on the main thread. */
    fun refresh(post: (() -> Unit) -> Unit) {
        if (busy) return
        busy = true
        Thread {
            for (feed in feeds.toList()) {
                val result = runCatching {
                    val (data, final) = SafeFetch.get(feed.url, "application/rss+xml, application/atom+xml, application/feed+json, text/html;q=0.8")
                    if (feed.watchesPage) FeedParser.page(data, final).second else (FeedParser.parse(data, final) ?: error("Not a feed")).second
                }
                post {
                    val i = feeds.indexOfFirst { it.id == feed.id }
                    if (i >= 0) {
                        result.onSuccess { merge(feed, it); feeds[i] = feeds[i].copy(error = null) }
                            .onFailure { feeds[i] = feeds[i].copy(error = it.message) }
                    }
                }
            }
            post { busy = false; save() }
        }.start()
    }

    fun scoped(scope: String): List<FeedItem> {
        val byId = feeds.associateBy { it.id }
        val all = items.sortedByDescending { it.date }
        return when {
            scope == "today" -> all.filter { it.date >= System.currentTimeMillis() - 86_400_000 }
            scope == "later" -> all.filter { it.id in later }
            scope.startsWith("folder:") -> all.filter { byId[it.feed]?.folder == scope.removePrefix("folder:") }
            scope.startsWith("feed:") -> all.filter { it.feed == scope.removePrefix("feed:") }
            else -> all
        }
    }

    fun folders(): List<String> = feeds.mapNotNull { it.folder }.distinct().sorted()

    fun markRead(item: FeedItem) {
        if (item.id !in read) read.add(item.id)
        save()
    }

    fun toggleLater(item: FeedItem) {
        if (item.id in later) later.remove(item.id) else later.add(item.id)
        save()
    }

    fun markAllRead(scope: String) {
        scoped(scope).forEach { if (it.id !in read) read.add(it.id) }
        save()
    }
}
