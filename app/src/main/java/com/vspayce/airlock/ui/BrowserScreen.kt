package com.vspayce.airlock.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vspayce.airlock.BrowserTab
import com.vspayce.airlock.SearchEngine
import com.vspayce.airlock.TabManager
import com.vspayce.airlock.UrlInput
import com.vspayce.airlock.ui.theme.AirlockBackground
import com.vspayce.airlock.ui.theme.AirlockBurn
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(manager: TabManager) {
    val context = LocalContext.current
    val tab = manager.selected
    var engine by remember { mutableStateOf(SearchEngine.load(context)) }
    var showTabs by rememberSaveable { mutableStateOf(false) }
    var confirmBurn by remember { mutableStateOf(false) }

    BackHandler(enabled = showTabs || tab?.canGoBack == true) {
        if (showTabs) showTabs = false else tab?.session?.goBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(
                tab = tab,
                engine = engine,
                onEngineChange = {
                    engine = it
                    SearchEngine.save(context, it)
                },
                onSubmit = { input ->
                    val url = UrlInput.resolve(input, engine)
                    if (url.isNotEmpty()) {
                        if (tab != null) manager.load(tab, url) else manager.newTab(url)
                    }
                },
            )
            ProgressStrip(tab)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                GeckoContent(manager)
                if (tab == null || tab.isBlank) StartPage(engine)
            }
            BottomBar(
                tab = tab,
                tabCount = manager.tabs.size,
                onBurn = { confirmBurn = true },
                onShowTabs = { showTabs = true },
            )
        }

        if (showTabs) {
            TabSwitcher(manager = manager, onDismiss = { showTabs = false })
        }
    }

    if (confirmBurn) {
        AlertDialog(
            onDismissRequest = { confirmBurn = false },
            title = { Text("Burn everything?") },
            text = {
                Text("Closes all tabs and erases cookies, cache, history, site data and permissions.")
            },
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
            dismissButton = {
                TextButton(onClick = { confirmBurn = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GeckoContent(manager: TabManager) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            GeckoView(ctx).also { manager.attachedView = it }
        },
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

@Composable
private fun AddressBar(
    tab: BrowserTab?,
    engine: SearchEngine,
    onEngineChange: (SearchEngine) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var menuOpen by remember { mutableStateOf(false) }
    val currentUrl = tab?.takeUnless { it.isBlank }?.url.orEmpty()

    // Show the page URL whenever the user isn't editing.
    LaunchedEffect(currentUrl, focused, tab?.id) {
        if (!focused) field = TextFieldValue(currentUrl)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 0.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { state ->
                    if (state.isFocused && !focused) {
                        field = field.copy(selection = TextRange(0, field.text.length))
                    }
                    focused = state.isFocused
                },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("Search ${engine.shortName} or enter address", maxLines = 1) },
            leadingIcon = {
                val secure = tab?.secure == true && !focused && currentUrl.isNotEmpty()
                Icon(
                    imageVector = if (secure) Icons.Filled.Lock else Icons.Filled.Search,
                    contentDescription = if (secure) "Secure connection" else null,
                    tint = if (secure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = if (focused && field.text.isNotEmpty()) {
                {
                    IconButton(onClick = { field = TextFieldValue("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                onSubmit(field.text)
                focusManager.clearFocus()
            }),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Search engine")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Text(
                    "Search engine",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SearchEngine.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onEngineChange(option)
                            menuOpen = false
                        },
                        trailingIcon = if (option == engine) {
                            { Icon(Icons.Filled.Check, contentDescription = "Selected") }
                        } else {
                            null
                        },
                    )
                }
                HorizontalDivider()
                Text(
                    "* not private",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressStrip(tab: BrowserTab?) {
    if (tab != null && tab.loading) {
        val progress = tab.progress.coerceIn(0, 100) / 100f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    } else {
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun BottomBar(
    tab: BrowserTab?,
    tabCount: Int,
    onBurn: () -> Unit,
    onShowTabs: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { tab?.session?.goBack() }, enabled = tab?.canGoBack == true) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = { tab?.session?.goForward() }, enabled = tab?.canGoForward == true) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
        }
        val loading = tab?.loading == true
        IconButton(
            onClick = { if (loading) tab?.session?.stop() else tab?.session?.reload() },
            enabled = tab != null && !tab.isBlank,
        ) {
            Icon(
                imageVector = if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                contentDescription = if (loading) "Stop" else "Reload",
            )
        }
        TextButton(onClick = onBurn) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = AirlockBurn, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Burn", color = AirlockBurn)
        }
        IconButton(onClick = onShowTabs) {
            Box(
                Modifier
                    .size(24.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (tabCount > 99) "99+" else "$tabCount", fontSize = if (tabCount > 99) 8.sp else 12.sp)
            }
        }
    }
}

@Composable
private fun StartPage(engine: SearchEngine) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Airlock", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text("Private by default", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        listOf(
            "Strict tracking protection",
            "HTTPS-only connections",
            "Cookie banners rejected",
            "Global Privacy Control on",
            "Tracking parameters stripped",
            "Nothing saved after you leave",
            "Searching with ${engine.label}",
        ).forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TabSwitcher(manager: TabManager, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tabs (${manager.tabs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    manager.newTab()
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New tab")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close tab switcher")
                }
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
                            .clip(RoundedCornerShape(12.dp))
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
                            Text(
                                t.title.ifBlank { if (t.isBlank) "New tab" else t.url },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!t.isBlank) {
                                Text(
                                    t.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { manager.close(t) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab")
                        }
                    }
                }
            }
        }
    }
}
