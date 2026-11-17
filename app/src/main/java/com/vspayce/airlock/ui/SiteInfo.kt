package com.vspayce.airlock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vspayce.airlock.BrowserTab
import com.vspayce.airlock.SiteTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** Certificate, whois, DNS and hosting for the current site (same as Site Tools on iPhone). */
@Composable
fun SiteInfo(tab: BrowserTab, onDismiss: () -> Unit) {
    val host = runCatching { android.net.Uri.parse(tab.url).host }.getOrNull().orEmpty()
    var whois by remember { mutableStateOf<Result<SiteTools.Whois>?>(null) }
    var dns by remember { mutableStateOf<Result<List<Pair<String, String>>>?>(null) }
    var owners by remember { mutableStateOf<List<SiteTools.Owner>>(emptyList()) }

    LaunchedEffect(host) {
        if (host.isEmpty()) return@LaunchedEffect
        whois = withContext(Dispatchers.IO) { runCatching { SiteTools.whois(host) } }
        val records = withContext(Dispatchers.IO) { runCatching { SiteTools.dns(host) } }
        dns = records
        owners = withContext(Dispatchers.IO) {
            records.getOrNull().orEmpty().filter { it.first == "A" || it.first == "AAAA" }.take(4)
                .mapNotNull { SiteTools.owner(it.second) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Site info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(host, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Section("Connection") {
                            val cert = tab.certificate
                            if (!tab.url.startsWith("https://")) {
                                Line("Encryption", "None: plain HTTP. Anyone on the network can read and change this page.")
                            } else if (cert == null) {
                                Line("Encryption", if (tab.secure) "HTTPS" else "HTTPS, not verified")
                            } else {
                                Line("Encryption", if (tab.secure) "HTTPS, certificate verified" else "HTTPS, certificate problems")
                                Line("Issued to", cert.subjectX500Principal.name.cn())
                                Line("Issued by", cert.issuerX500Principal.name.cn())
                                val left = Duration.between(java.time.Instant.now(), cert.notAfter.toInstant()).toDays()
                                Line("Valid", "${cert.notBefore.toInstant().atOffset(java.time.ZoneOffset.UTC).format(dateFormat)} to " +
                                    "${cert.notAfter.toInstant().atOffset(java.time.ZoneOffset.UTC).format(dateFormat)} ($left days left)")
                                val fp = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                                    .joinToString(":") { "%02X".format(it) }
                                Line("SHA-256 fingerprint", fp)
                            }
                        }
                    }
                    item {
                        Section("Domain (whois)") {
                            val w = whois
                            when {
                                w == null -> Line("Looking up…", "")
                                w.isFailure -> Line("No registration record", w.exceptionOrNull()?.message ?: "")
                                else -> {
                                    val d = w.getOrThrow()
                                    Line("Domain", d.domain)
                                    d.created?.let {
                                        if (Duration.between(it, OffsetDateTime.now()).toDays() < 60) {
                                            Text("This domain is less than two months old. New domains are common for scams and phishing.",
                                                color = Color(0xFFFFA94D), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        }
                                        Line("Registered", it.format(dateFormat) + " (" + age(it) + ")")
                                    }
                                    d.expires?.let { Line("Expires", it.format(dateFormat)) }
                                    d.registrar?.let { Line("Registrar", it) }
                                    if (d.nameservers.isNotEmpty()) Line("Name servers", d.nameservers.take(6).joinToString(", "))
                                }
                            }
                        }
                    }
                    item {
                        Section("DNS records") {
                            val r = dns
                            when {
                                r == null -> Line("Looking up…", "")
                                r.isFailure -> Line("DNS lookup failed", r.exceptionOrNull()?.message ?: "")
                                else -> r.getOrThrow().take(40).forEach { (t, v) -> Line(t, v) }
                            }
                        }
                    }
                    item {
                        Section("Hosted by") {
                            if (owners.isEmpty()) Line(if (dns == null) "Looking up…" else "Unknown", "")
                            owners.forEach { o -> Line(o.ip, listOfNotNull(o.name ?: o.network, o.country).joinToString(" · ")) }
                        }
                    }
                    item {
                        Text("Looked up only when you open this. Domain details come from the registry's RDAP service (the modern whois), " +
                            "DNS records from Cloudflare's encrypted DNS. No cookies, nothing about you is sent.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
                    }
                }
            }
        }
    }
}

private fun String.cn(): String =
    split(',').map { it.trim() }.firstOrNull { it.startsWith("CN=") }?.removePrefix("CN=")
        ?: split(',').map { it.trim() }.firstOrNull { it.startsWith("O=") }?.removePrefix("O=") ?: this

private fun age(d: OffsetDateTime): String {
    val days = Duration.between(d, OffsetDateTime.now()).toDays()
    return when {
        days < 60 -> "$days days old"
        days < 730 -> "${days / 30} months old"
        else -> "${days / 365} years old"
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.isNotEmpty()) Text(value, fontSize = 15.sp)
    }
}
