package com.vspayce.airlock.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vspayce.airlock.BrowserTab
import com.vspayce.airlock.R
import com.vspayce.airlock.SearchEngine
import com.vspayce.airlock.TabManager
import com.vspayce.airlock.UrlInput
import com.vspayce.airlock.ui.theme.AirlockBackground
import com.vspayce.airlock.ui.theme.AirlockBurn
import com.vspayce.airlock.ui.theme.AirlockTeal
import org.mozilla.geckoview.GeckoView

/** The same layout as Airlock on iPhone: the page fills the screen and a floating dock sits at the bottom. */
@Composable
fun BrowserScreen(manager: TabManager) {
    val context = LocalContext.current
    val tab = manager.selected
    var engine by remember { mutableStateOf(SearchEngine.load(context)) }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var confirmBurn by remember { mutableStateOf(false) }
    var showShield by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showSiteInfo by remember { mutableStateOf(false) }
    var showFeeds by rememberSaveable { mutableStateOf(false) }
    val feedStore = remember { com.vspayce.airlock.Airlock.feeds(context) }
    val prefs = remember { com.vspayce.airlock.Airlock.prefs(context) }

    BackHandler(enabled = editing || showTabs || tab?.canGoBack == true) {
        when {
            editing -> editing = false
            showTabs -> showTabs = false
            else -> tab?.session?.goBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                GeckoContent(manager)
                if (tab == null || tab.isBlank) {
                    StartPage(
                        engine = engine,
                        prefs = prefs,
                        onSearch = { editing = true },
                        onOpen = { url -> if (tab != null) manager.load(tab, url) else manager.newTab(url) },
                        onMode = { prefs.setMode(it, com.vspayce.airlock.Airlock.runtime(context)) },
                    )
                }
            }
            Dock(
                tab = tab,
                tabCount = manager.tabs.size,
                engine = engine,
                editing = editing,
                onEditingChange = { editing = it },
                onEngineChange = {
                    engine = it
                    SearchEngine.save(context, it)
                },
                onSubmit = { input ->
                    val url = UrlInput.resolve(input, engine)
                    if (url.isNotEmpty()) {
                        if (tab != null) manager.load(tab, url) else manager.newTab(url)
                    }
                    editing = false
                },
                onHome = { tab?.let { manager.load(it, "about:blank") } },
                onNewTab = {
                    manager.newTab()
                    editing = true
                },
                onShield = { showShield = true },
                onFavorite = {
                    tab?.takeUnless { it.isBlank }?.let {
                        val added = prefs.addFavorite(it.title.ifBlank { shortName(it.url) }, it.url)
                        Toast.makeText(context, if (added) "Added to Favorites" else "Already in Favorites", Toast.LENGTH_SHORT).show()
                    }
                },
                onSiteInfo = { showSiteInfo = true },
                onReader = {
                    tab?.let { t ->
                        if (t.readerTitle != null) t.session.goBack()
                        else com.vspayce.airlock.Reader.extract(t) { a ->
                            if (a == null) Toast.makeText(context, "Airlock couldn't find an article on this page", Toast.LENGTH_SHORT).show()
                            else com.vspayce.airlock.Reader.show(t, a)
                        }
                    }
                },
                onClip = {
                    tab?.let { t ->
                        com.vspayce.airlock.Reader.extract(t) { a ->
                            if (a == null) Toast.makeText(context, "Open an article to clip it (from the page, not Reader view)", Toast.LENGTH_SHORT).show()
                            else com.vspayce.airlock.Reader.share(context, a)
                        }
                    }
                },
                onFeeds = {
                    showFeeds = true
                    if (feedStore.feeds.isNotEmpty()) feedStore.refresh { block -> android.os.Handler(android.os.Looper.getMainLooper()).post(block) }
                },
                onFollow = {
                    tab?.takeUnless { it.isBlank }?.let { t ->
                        Toast.makeText(context, "Looking for a feed…", Toast.LENGTH_SHORT).show()
                        Thread {
                            val result = runCatching { feedStore.followBlocking(t.url) }
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                result.onSuccess { (feed, items) ->
                                    feedStore.add(feed, items)
                                    val how = if (feed.watchesPage) "Watching ${feed.title} for new headlines" else "Added to Feeds: ${feed.title}"
                                    Toast.makeText(context, how, Toast.LENGTH_SHORT).show()
                                }.onFailure { Toast.makeText(context, "Couldn't find a feed or headlines on this page", Toast.LENGTH_LONG).show() }
                            }
                        }.start()
                    }
                },
                onBurn = { confirmBurn = true },
                onShowTabs = { showTabs = true },
            )
        }

        if (showTabs) {
            TabSwitcher(manager = manager, onDismiss = { showTabs = false })
        }
        if (showFeeds) {
            BackHandler { showFeeds = false }
            FeedsScreen(feedStore, onOpen = { url ->
                showFeeds = false
                if (tab != null) manager.load(tab, url) else manager.newTab(url)
            }) { showFeeds = false }
        }
        if (showSiteInfo && tab != null && !tab.isBlank) {
            BackHandler { showSiteInfo = false }
            SiteInfo(tab) { showSiteInfo = false }
        }
    }

    if (showShield && tab != null) {
        ShieldDialog(tab, onSiteInfo = { showShield = false; showSiteInfo = true }) { showShield = false }
    }

    if (confirmBurn) {
        AlertDialog(
            onDismissRequest = { confirmBurn = false },
            title = { Text("Burn everything?") },
            text = { Text("Closes all tabs and erases cookies, cache, history, site data and permissions.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBurn = false
                    showTabs = false
                    manager.burn { ok ->
                        val msg = if (ok) "Burned. All browsing data cleared." else "Burn failed to clear some data."
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Burn", color = AirlockBurn) }
            },
            dismissButton = { TextButton(onClick = { confirmBurn = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GeckoContent(manager: TabManager) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> GeckoView(ctx).also { manager.attachedView = it } },
        update = { view ->
            // Reading manager.selected here subscribes this block to tab switches.
            val session = manager.selected?.session
            if (session != null && view.session !== session) {
                view.releaseSession()
                view.setSession(session)
                view.coverUntilFirstPaint(AirlockBackground.toArgb())
            }
        },
        onRelease = { view ->
            view.releaseSession()
            if (manager.attachedView === view) manager.attachedView = null
        },
    )
}

/** "www.nytimes.com" -> "nytimes" (the iPhone dock shows the site's short name). */
private fun shortName(url: String): String {
    val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty().lowercase()
    var parts = host.split('.').filter { it.isNotEmpty() }
    if (parts.firstOrNull() == "www" || parts.firstOrNull() == "m") parts = parts.drop(1)
    if (parts.size < 2) return parts.firstOrNull() ?: host
    parts = parts.dropLast(1)
    if (parts.size >= 2 && parts.last() in setOf("co", "com", "org", "net", "gov", "ac", "edu")) parts = parts.dropLast(1)
    return parts.last()
}

@Composable
private fun Dock(
    tab: BrowserTab?,
    tabCount: Int,
    engine: SearchEngine,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onEngineChange: (SearchEngine) -> Unit,
    onSubmit: (String) -> Unit,
    onHome: () -> Unit,
    onNewTab: () -> Unit,
    onShield: () -> Unit,
    onFavorite: () -> Unit,
    onSiteInfo: () -> Unit,
    onFeeds: () -> Unit,
    onFollow: () -> Unit,
    onReader: () -> Unit,
    onClip: () -> Unit,
    onBurn: () -> Unit,
    onShowTabs: () -> Unit,
) {
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .shadow(16.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.75.dp, Color.White.copy(alpha = 0.22f), shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (editing) {
                SearchCapsule(tab, engine, onSubmit) { onEditingChange(false) }
            } else {
                SiteCapsule(tab, onShield = onShield, onBurn = onBurn, onEdit = { onEditingChange(true) })
                ButtonRow(tab, tabCount, engine, onEngineChange, onHome, onNewTab, onFavorite, onSiteInfo, onFeeds, onFollow, onReader, onClip, onShowTabs)
            }
        }
    }
}

@Composable
private fun capsuleFill() = MaterialTheme.colorScheme.surfaceContainerHighest

@Composable
private fun SiteCapsule(tab: BrowserTab?, onShield: () -> Unit, onBurn: () -> Unit, onEdit: () -> Unit) {
    val url = tab?.takeUnless { it.isBlank }?.url.orEmpty()
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(capsuleFill()),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shield with the number of trackers blocked on this page.
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = url.isNotEmpty(), onClick = onShield)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = "Site shield",
                    tint = AirlockTeal, modifier = Modifier.size(22.dp))
                val n = tab?.blocked?.size ?: 0
                if (url.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(if (n > 99) "99+" else "$n", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.dp).height(26.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = onEdit),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (url.isEmpty()) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                } else {
                    if (tab?.secure == false && !tab.loading) {
                        Icon(Icons.Filled.Warning, contentDescription = "Not secure", tint = Color(0xFFFFA94D),
                            modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(tab?.readerTitle?.let { "Reader" } ?: shortName(url), fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            // Burn key.
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(AirlockBurn.copy(alpha = 0.18f)).clickable(onClick = onBurn),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_burn), contentDescription = "Burn", tint = AirlockBurn,
                    modifier = Modifier.size(22.dp))
            }
        }
        if (tab != null && tab.loading) {
            val p = (tab.progress.coerceIn(5, 100)) / 100f
            Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 18.dp).fillMaxWidth(p).height(2.5.dp)
                .clip(CircleShape).background(AirlockTeal))
        }
    }
}

@Composable
private fun SearchCapsule(tab: BrowserTab?, engine: SearchEngine, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val start = tab?.takeUnless { it.isBlank }?.url.orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(capsuleFill()).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (field.text.isEmpty()) {
                Text("Search ${engine.shortName} or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(AirlockTeal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    onSubmit(field.text)
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        IconButton(onClick = {
            focusManager.clearFocus()
            onCancel()
        }) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ButtonRow(
    tab: BrowserTab?,
    tabCount: Int,
    engine: SearchEngine,
    onEngineChange: (SearchEngine) -> Unit,
    onHome: () -> Unit,
    onNewTab: () -> Unit,
    onFavorite: () -> Unit,
    onSiteInfo: () -> Unit,
    onFeeds: () -> Unit,
    onFollow: () -> Unit,
    onReader: () -> Unit,
    onClip: () -> Unit,
    onShowTabs: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { tab?.session?.goBack() }, enabled = tab?.canGoBack == true) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = onHome, enabled = tab != null && !tab.isBlank) {
            Icon(Icons.Filled.Home, contentDescription = "Home")
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("New tab") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { menuOpen = false; onNewTab() })
                DropdownMenuItem(text = { Text(if (tab?.loading == true) "Stop" else "Reload") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, null) }, enabled = tab != null && !tab.isBlank,
                    onClick = {
                        menuOpen = false
                        if (tab?.loading == true) tab.session.stop() else tab?.session?.reload()
                    })
                DropdownMenuItem(text = { Text("Add to Favorites") }, enabled = tab != null && !tab.isBlank,
                    onClick = { menuOpen = false; onFavorite() })
                DropdownMenuItem(text = { Text("Site info") }, enabled = tab != null && !tab.isBlank,
                    onClick = { menuOpen = false; onSiteInfo() })
                DropdownMenuItem(text = { Text(if (tab?.readerTitle != null) "Leave Reader view" else "Reader view") },
                    enabled = tab != null && !tab.isBlank, onClick = { menuOpen = false; onReader() })
                DropdownMenuItem(text = { Text("Clip to Obsidian / notes…") }, enabled = tab != null && !tab.isBlank && tab.readerTitle == null,
                    onClick = { menuOpen = false; onClip() })
                DropdownMenuItem(text = { Text("Add to Feeds") }, enabled = tab != null && !tab.isBlank,
                    onClick = { menuOpen = false; onFollow() })
                DropdownMenuItem(text = { Text("Forward") }, enabled = tab?.canGoForward == true,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    onClick = { menuOpen = false; tab?.session?.goForward() })
                HorizontalDivider()
                Text("Search engine", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                SearchEngine.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onEngineChange(option); menuOpen = false },
                        trailingIcon = if (option == engine) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                    )
                }
                Text("* not private", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        IconButton(onClick = onFeeds) {
            Icon(painterResource(R.drawable.ic_feeds), contentDescription = "Feeds")
        }
        IconButton(onClick = onShowTabs) {
            Box(
                Modifier.size(24.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (tabCount > 99) "∞" else "$tabCount", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ShieldDialog(tab: BrowserTab, onSiteInfo: () -> Unit, onDismiss: () -> Unit) {
    val host = runCatching { android.net.Uri.parse(tab.url).host }.getOrNull().orEmpty().removePrefix("www.")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = AirlockTeal) },
        title = { Text(host.ifEmpty { "This page" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (tab.secure) AirlockTeal else Color(0xFFFFA94D))
                    Spacer(Modifier.width(6.dp))
                    Text(if (tab.secure) "Secure connection" else "Not secure")
                }
                val n = tab.blocked.size
                Text("$n tracker${if (n == 1) "" else "s"} and ads blocked on this page", fontWeight = FontWeight.SemiBold)
                if (n > 0) {
                    Text(tab.blocked.take(25).joinToString("\n") + if (n > 25) "\n…" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Text("Strict tracking protection, cookie banners rejected, HTTPS only.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onSiteInfo) { Text("Site info") } },
    )
}

private val tileColors = listOf(0xFF2F7F86, 0xFF5B6FD6, 0xFFC2683A, 0xFF3F8F55, 0xFF9B59B6, 0xFFB94A6A, 0xFF8A7A2F, 0xFF3A76B8)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StartPage(
    engine: SearchEngine,
    prefs: com.vspayce.airlock.AirlockPrefs,
    onSearch: () -> Unit,
    onOpen: (String) -> Unit,
    onMode: (com.vspayce.airlock.Mode) -> Unit,
) {
    var modeMenu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<com.vspayce.airlock.Favorite?>(null) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.airlock_logo), contentDescription = "Airlock",
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(14.dp))
        Text("Airlock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Mode button: "Sealed · Strict", tap to change (like the iPhone home screen).
        Box {
            val label = if (prefs.mode == com.vspayce.airlock.Mode.HARDENED) "Fully Sealed" else "Sealed · ${prefs.mode.label}"
            Row(
                Modifier.clip(CircleShape).background(AirlockTeal.copy(alpha = 0.14f)).clickable { modeMenu = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = AirlockTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("$label ▾", color = AirlockTeal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                com.vspayce.airlock.Mode.entries.forEach { m ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(m.label, fontWeight = FontWeight.SemiBold)
                                Text(m.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingIcon = if (m == prefs.mode) ({ Icon(Icons.Filled.Check, contentDescription = "Selected") }) else null,
                        onClick = { modeMenu = false; onMode(m) },
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().height(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onSearch).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("Search ${engine.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        if (prefs.favorites.isEmpty()) {
            Text("Add sites here with Add to Favorites in the menu.", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(76.dp),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(prefs.favorites.size) { index ->
                    val f = prefs.favorites[index]
                    val name = shortName(f.url)
                    Column(
                        Modifier.combinedClickable(onClick = { onOpen(f.url) }, onLongClick = { removing = f }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(tileColors[name.sumOf { it.code } % tileColors.size])),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(f.title.ifBlank { name }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from Favorites?") },
            text = { Text(f.title.ifBlank { f.url }) },
            confirmButton = { TextButton(onClick = { prefs.removeFavorite(f); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TabSwitcher(manager: TabManager, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tabs (${manager.tabs.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    manager.newTab()
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New tab")
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close tab switcher") }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(manager.tabs, key = { it.id }) { t ->
                    val isSelected = t.id == manager.selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .clickable {
                                manager.select(t)
                                onDismiss()
                            }
                            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title.ifBlank { if (t.isBlank) "New tab" else t.url }, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!t.isBlank) {
                                Text(t.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { manager.close(t) }) { Icon(Icons.Filled.Close, contentDescription = "Close tab") }
                    }
                }
            }
        }
    }
}
