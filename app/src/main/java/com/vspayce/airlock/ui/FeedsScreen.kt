package com.vspayce.airlock.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vspayce.airlock.Feed
import com.vspayce.airlock.FeedItem
import com.vspayce.airlock.FeedStore
import com.vspayce.airlock.ui.theme.AirlockTeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val main = Handler(Looper.getMainLooper())

private fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.isAfter(today.minusDays(7)) -> d.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
        else -> d.format(DateTimeFormatter.ofPattern("MMMM d"))
    }
}

private fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 3600 -> "${maxOf(1, s / 60)}m"
        s < 86_400 -> "${s / 3600}h"
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

/** Feedly-style reader: Today / All / Read Later / folders, day sections, headlines first. */
@Composable
fun FeedsScreen(store: FeedStore, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var scope by remember { mutableStateOf("today") }
    var adding by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    val byId = store.feeds.associateBy { it.id }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Feeds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { store.markAllRead(scope) }) { Icon(Icons.Filled.Check, contentDescription = "Mark all read") }
                IconButton(onClick = { store.refresh { block -> main.post(block) } }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = "Follow a site") }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close Feeds") }
            }
            if (store.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = AirlockTeal)
            // Category chips, like the iPhone version (Google News style).
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf("today" to "Today", "all" to "All", "later" to "Read Later") +
                    store.folders().map { "folder:$it" to it }
                chips.forEach { (key, name) ->
                    val unread = if (key == "later") 0 else store.scoped(key).count { it.id !in store.read }
                    val on = key == scope
                    Text(
                        if (unread > 0) "$name  $unread" else name,
                        modifier = Modifier.clip(CircleShape)
                            .background(if (on) AirlockTeal else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { scope = key }.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
                Text("Sources", modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { managing = true }.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 14.sp)
            }
            val list = store.scoped(scope).take(400)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (store.feeds.isEmpty()) {
                    item { Text("Follow a site with + to see its latest articles here. Sites without a feed work too: Airlock reads their headlines.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
                } else if (list.isEmpty()) {
                    item { Text(if (scope == "later") "Nothing saved for later yet. Tap ☆ on an article." else "No new articles here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp)) }
                }
                var lastDay: String? = null
                list.forEach { it ->
                    val day = dayLabel(it.date)
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "day-$day") {
                            Text(day.uppercase(), fontSize = 12.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
                        }
                    }
                    item(key = it.id) { Row_(it, byId[it.feed], store, onOpen) }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (adding) {
        var text by remember { mutableStateOf("") }
        var working by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Follow a site") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Site or feed address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done))
                    Text("Sites without a feed work too: Airlock reads their headlines.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !working && text.isNotBlank(), onClick = {
                    working = true
                    val input = text.trim()
                    Thread {
                        val result = runCatching { store.followBlocking(input) }
                        main.post {
                            result.onSuccess { (feed, items) ->
                                store.add(feed, items)
                                Toast.makeText(context, "Added to Feeds: ${feed.title}", Toast.LENGTH_SHORT).show()
                                adding = false
                            }.onFailure {
                                Toast.makeText(context, "Couldn't follow that: ${it.message}", Toast.LENGTH_LONG).show()
                                working = false
                            }
                        }
                    }.start()
                }) { Text(if (working) "Following…" else "Follow") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }

    if (managing) {
        Sources(store) { managing = false }
    }
}

@Composable
private fun Row_(it: FeedItem, feed: Feed?, store: FeedStore, onOpen: (String) -> Unit) {
    val read = it.id in store.read
    val saved = it.id in store.later
    Column {
        Row(Modifier.fillMaxWidth().clickable { store.markRead(it); onOpen(it.link) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(it.title, fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold, fontSize = 16.sp,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(feed?.title, ago(it.date), it.minutes?.let { m -> "${m}min" }).joinToString(" / "),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (it.summary.isNotBlank()) {
                    Text(it.summary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { store.toggleLater(it) }) {
                if (saved) Icon(Icons.Filled.Star, contentDescription = "Saved for later", tint = AirlockTeal)
                else Text("☆", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
}

@Composable
private fun Sources(store: FeedStore, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<Feed?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources (${store.feeds.size})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(store.feeds.sortedWith(compareBy({ it.folder ?: "~" }, { it.title.lowercase() })), key = { it.id }) { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { editing = f }) {
                            Text(f.title + if (f.watchesPage) "  (page)" else "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(f.folder ?: "No folder", f.error).joinToString(" · "), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { store.unfollow(f) }) { Icon(Icons.Filled.Delete, contentDescription = "Unfollow") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
    editing?.let { f ->
        var name by remember(f.id) { mutableStateOf(f.folder ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Folder for ${f.title}") },
            text = {
                OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("e.g. News, Tech, Food") },
                    keyboardActions = KeyboardActions(onDone = { store.setFolder(f, name); editing = null }))
            },
            confirmButton = { TextButton(onClick = { store.setFolder(f, name); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
