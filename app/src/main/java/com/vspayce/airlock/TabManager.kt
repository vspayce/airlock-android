package com.vspayce.airlock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController

/** Process-wide holders. GeckoRuntime must be created exactly once, on the main thread. */
object Airlock {
    private var runtime: GeckoRuntime? = null
    private var tabManager: TabManager? = null

    fun runtime(context: Context): GeckoRuntime =
        runtime ?: GeckoRuntime.create(context.applicationContext, PrivacySettings.runtimeSettings())
            .also { runtime = it }

    fun tabs(context: Context): TabManager =
        tabManager ?: TabManager(runtime(context)).also { tabManager = it }
}

/** UI-observable state of one tab. Gecko delegate callbacks arrive on the main thread. */
class BrowserTab(val id: Long, val session: GeckoSession) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var secure by mutableStateOf(false)
    /** Hosts of trackers and ads Gecko blocked on the current page (shown on the shield). */
    val blocked = mutableStateListOf<String>()

    val isBlank: Boolean get() = url.isBlank() || url == "about:blank"
}

class TabManager(private val runtime: GeckoRuntime) {
    val tabs = mutableStateListOf<BrowserTab>()

    var selectedId by mutableLongStateOf(NO_TAB)
        private set

    val selected: BrowserTab? get() = tabs.firstOrNull { it.id == selectedId }

    /** The GeckoView currently on screen, so a session can be detached before it's closed. */
    var attachedView: GeckoView? = null

    private var nextId = 1L

    fun newTab(url: String? = null, select: Boolean = true): BrowserTab {
        val session = GeckoSession(PrivacySettings.sessionSettings())
        val tab = BrowserTab(nextId++, session)
        attachDelegates(tab)
        session.open(runtime)
        tabs.add(tab)
        if (select) select(tab) else session.setActive(false)
        if (!url.isNullOrBlank()) load(tab, url)
        return tab
    }

    fun select(tab: BrowserTab) {
        if (tab.id != selectedId) {
            selected?.session?.setActive(false)
            selectedId = tab.id
        }
        tab.session.setActive(true)
    }

    fun load(tab: BrowserTab, url: String) {
        tab.url = url
        tab.session.loadUri(url)
    }

    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        detachIfShowing(tab)
        tabs.removeAt(index)
        tab.session.close()
        when {
            tabs.isEmpty() -> newTab()
            tab.id == selectedId -> select(tabs[minOf(index, tabs.lastIndex)])
        }
    }

    /** Close every tab, wipe all Gecko storage, and start over with one fresh tab. */
    fun burn(onDone: (Boolean) -> Unit) {
        attachedView?.releaseSession()
        tabs.forEach { it.session.close() }
        tabs.clear()
        selectedId = NO_TAB
        runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { _ ->
                newTab()
                onDone(true)
            },
            { _ ->
                newTab()
                onDone(false)
            },
        )
    }

    private fun detachIfShowing(tab: BrowserTab) {
        val view = attachedView ?: return
        if (view.session === tab.session) view.releaseSession()
    }

    private fun attachDelegates(tab: BrowserTab) {
        val session = tab.session

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.blocked.clear()
                tab.url = url
                tab.loading = true
                tab.progress = 5
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                tab.progress = 100
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation,
            ) {
                tab.secure = securityInfo.isSecure
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.url = url.orEmpty()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            // target=_blank / window.open: open in a new Airlock tab instead of handing the
            // page an opener-linked window. Returning null tells Gecko not to create one itself.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                newTab(uri)
                return null
            }
        }

        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                val host = runCatching { Uri.parse(event.uri).host }.getOrNull()?.removePrefix("www.") ?: return
                if (host !in tab.blocked) tab.blocked.add(host)
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onCloseRequest(session: GeckoSession) {
                close(tab)
            }
        }
    }

    companion object {
        const val NO_TAB = -1L
    }
}
