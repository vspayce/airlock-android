package com.vspayce.airlock

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime

/**
 * Site info lookups: domain registration (RDAP, the modern whois), DNS records (Cloudflare DNS over HTTPS)
 * and who hosts each address. HTTPS only, no cookies, nothing about you is sent. Call off the main thread.
 */
object SiteTools {
    data class Whois(
        val domain: String,
        val registrar: String?,
        val created: OffsetDateTime?,
        val expires: OffsetDateTime?,
        val nameservers: List<String>,
    )

    data class Owner(val ip: String, val name: String?, val network: String?, val country: String?)

    private fun get(url: String, accept: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.setRequestProperty("Accept", accept)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            // Redirects must stay on HTTPS.
            if (conn.url.protocol != "https") error("Refused a non-HTTPS redirect")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 1_000_000) error("Response too large")
                }
            }
            return JSONObject(out.toString("UTF-8"))
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun date(s: String?): OffsetDateTime? = runCatching { OffsetDateTime.parse(s) }.getOrNull()

    private fun vcardName(entity: JSONObject): String? {
        val card = entity.optJSONArray("vcardArray") ?: return null
        val props = card.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val p = props.optJSONArray(i) ?: continue
            if (p.optString(0) == "fn") return p.optString(p.length() - 1).takeIf { it.isNotBlank() }?.take(120)
        }
        return null
    }

    /** Tries news.bbc.co.uk, then bbc.co.uk..., since only registered domains have records. */
    fun whois(host: String): Whois {
        var labels = host.lowercase().trim('.').split('.')
        while (labels.size >= 2) {
            val name = labels.joinToString(".")
            val o = runCatching { get("https://rdap.org/domain/${enc(name)}", "application/rdap+json") }.getOrNull()
            if (o != null) {
                var created: OffsetDateTime? = null
                var expires: OffsetDateTime? = null
                val events = o.optJSONArray("events") ?: JSONArray()
                for (i in 0 until events.length()) {
                    val e = events.optJSONObject(i) ?: continue
                    when (e.optString("eventAction")) {
                        "registration" -> created = date(e.optString("eventDate"))
                        "expiration" -> expires = date(e.optString("eventDate"))
                    }
                }
                var registrar: String? = null
                val entities = o.optJSONArray("entities") ?: JSONArray()
                for (i in 0 until entities.length()) {
                    val ent = entities.optJSONObject(i) ?: continue
                    val roles = ent.optJSONArray("roles")?.toString() ?: ""
                    if ("registrar" in roles) registrar = vcardName(ent)
                }
                val ns = mutableListOf<String>()
                val nss = o.optJSONArray("nameservers") ?: JSONArray()
                for (i in 0 until nss.length()) nss.optJSONObject(i)?.optString("ldhName")?.lowercase()?.let { if (it.isNotBlank()) ns.add(it) }
                return Whois(o.optString("ldhName", name).lowercase(), registrar, created, expires, ns)
            }
            labels = labels.drop(1)
        }
        error("No registration record found")
    }

    private val types = listOf("A" to 1, "AAAA" to 28, "CNAME" to 5, "MX" to 15, "NS" to 2, "TXT" to 16, "CAA" to 257)

    fun dns(host: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var ok = false
        for ((name, code) in types) {
            val o = runCatching {
                get("https://cloudflare-dns.com/dns-query?name=${enc(host)}&type=$name", "application/dns-json")
            }.getOrNull() ?: continue
            ok = true
            val answers = o.optJSONArray("Answer") ?: continue
            for (i in 0 until answers.length()) {
                val a = answers.optJSONObject(i) ?: continue
                if (a.optInt("type") != code) continue
                val v = a.optString("data").trim('"').trimEnd('.').take(400)
                if ((name to v) !in out) out.add(name to v)
            }
        }
        if (!ok) error("DNS lookup failed")
        return out
    }

    fun owner(ip: String): Owner? {
        val o = runCatching { get("https://rdap.org/ip/${enc(ip)}", "application/rdap+json") }.getOrNull() ?: return null
        var name: String? = null
        val entities = o.optJSONArray("entities") ?: JSONArray()
        for (i in 0 until entities.length()) {
            val ent = entities.optJSONObject(i) ?: continue
            val roles = ent.optJSONArray("roles")?.toString() ?: ""
            if ("registrant" in roles || "administrative" in roles) {
                name = vcardName(ent)
                if (name != null) break
            }
        }
        return Owner(ip, name, o.optString("name").takeIf { it.isNotBlank() }, o.optString("country").takeIf { it.isNotBlank() })
    }
}
