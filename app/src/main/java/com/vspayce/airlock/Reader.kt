package com.vspayce.airlock

import android.content.Context
import android.content.Intent
import android.text.Html
import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import java.time.OffsetDateTime

/**
 * Reader view and clipping. A small built-in extension (assets/extension) runs Mozilla's Readability in its own
 * content-script world, cleans the article down to a fixed set of tags, and sends it to the app over a native port.
 */
object Reader {
    private const val ID = "reader@airlock.vspayce.com"
    var extension: WebExtension? = null
        private set

    fun install(runtime: GeckoRuntime, onReady: () -> Unit) {
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn("resource://android/assets/extension/", ID).accept({ ext ->
            if (ext == null) return@accept
            // Every Airlock tab is a private session, so the extension has to be allowed there.
            controller.setAllowedInPrivateBrowsing(ext, true).accept({ allowed ->
                extension = allowed ?: ext
                onReady()
            }, { e ->
                Log.w("Airlock", "reader extension not allowed in private tabs", e)
                extension = ext
                onReady()
            })
        }, { e -> Log.w("Airlock", "reader extension failed to load", e) })
    }

    fun attach(tab: BrowserTab) {
        val ext = extension ?: return
        tab.session.webExtensionController.setMessageDelegate(ext, object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                tab.port = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val o = message as? JSONObject ?: return
                        if (o.optString("type") == "article") {
                            val cb = tab.onArticle
                            tab.onArticle = null
                            cb?.invoke(o)
                        }
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (tab.port === port) tab.port = null
                    }
                })
            }
        }, "airlock")
    }

    /** Asks the page for its article; [done] gets null if there's none (or the page isn't ready). */
    fun extract(tab: BrowserTab, done: (JSONObject?) -> Unit) {
        val port = tab.port ?: return done(null)
        tab.onArticle = { done(if (it.optBoolean("ok")) it else null) }
        port.postMessage(JSONObject().put("action", "extract"))
    }

    private fun esc(s: String) = Html.escapeHtml(s)

    fun show(tab: BrowserTab, a: JSONObject) {
        val meta = listOf(a.optString("site"), a.optString("byline")).filter { it.isNotBlank() }.joinToString(" · ")
        val page = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https:; style-src 'unsafe-inline'; form-action 'none'">
<title>${esc(a.optString("title"))}</title>
<style>:root{color-scheme:dark}body{background:#121722;color:#e2e7ee;font:19px/1.65 Georgia,serif;margin:0 auto;max-width:680px;padding:28px 20px 80px}
h1{font:700 28px/1.2 sans-serif;margin:0 0 8px}a{color:#62d6d6}.meta{font:14px sans-serif;opacity:.65;margin-bottom:24px}
img{max-width:100%;height:auto;border-radius:8px}pre{overflow:auto;font-size:15px}blockquote{border-left:3px solid #62d6d655;margin-left:0;padding-left:14px}</style>
</head><body><h1>${esc(a.optString("title"))}</h1><div class="meta">${esc(meta)} · <a href="${esc(a.optString("url"))}">Original page</a></div>
${a.optString("html")}</body></html>"""
        tab.readerTitle = a.optString("title").ifBlank { "Reader" }
        tab.session.load(GeckoSession.Loader().data(page, "text/html"))
    }

    /** Markdown note with front matter (titles are JSON-quoted, which is valid YAML, so nothing can break out). */
    fun note(a: JSONObject): String {
        val q = { s: String -> JSONObject.quote(s) }
        val lines = mutableListOf("---", "title: ${q(a.optString("title"))}", "source: ${q(a.optString("url"))}")
        a.optString("byline").takeIf { it.isNotBlank() }?.let { lines.add("author: ${q(it)}") }
        a.optString("published").takeIf { it.isNotBlank() }?.let { lines.add("published: ${q(it)}") }
        lines.add("clipped: ${q(OffsetDateTime.now().withNano(0).toString())}")
        lines.add("tags: [\"clippings\"]")
        lines.add("---")
        return lines.joinToString("\n") + "\n\n" + a.optString("markdown")
    }

    /** Shares the clip: pick Obsidian (or any notes app) in the share sheet. */
    fun share(context: Context, a: JSONObject) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, a.optString("title"))
            putExtra(Intent.EXTRA_TITLE, a.optString("title"))
            putExtra(Intent.EXTRA_TEXT, note(a))
        }
        context.startActivity(Intent.createChooser(send, "Clip to…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
