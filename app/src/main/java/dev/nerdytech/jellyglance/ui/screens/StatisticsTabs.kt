package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.JellyLoading
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import dev.nerdytech.jellyglance.ui.theme.Warn

val STAT_TABS = listOf("Overview", "Recap", "Taste map", "Up next", "Library health", "Streams", "Devices")

// Small helpers for the loosely-typed insights JSON.
private fun Any?.m(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any, Any>()
private fun Map<*, *>.maps(k: String) = (this[k] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
private fun Map<*, *>.n(k: String) = (this[k] as? Double) ?: (this[k] as? String)?.toDoubleOrNull() ?: 0.0
private fun hours(seconds: Double) = Fields.fmtDuration(seconds)
private fun pct(r: Double) = "${(r * 100).toInt()}%"

/** One Statistics tab, loading its insights route and rendering it. */
@Composable
fun StatisticsTab(vm: AppViewModel, tab: String, openItem: (String, String) -> Unit) {
    var days by rememberSaveable(tab) { mutableStateOf(if (tab == "Devices" || tab == "Taste map") 90 else 30) }
    val path = when (tab) {
        "Recap" -> "wrapped?period=year&date=${java.time.LocalDate.now()}"
        "Taste map" -> "taste-map?days=$days"
        "Up next" -> "up-next"
        "Library health" -> "library-health?staleDays=180"
        "Streams" -> "streams?days=$days"
        else -> "devices?days=$days&sort=recent"
    }
    var data by remember(path) { mutableStateOf<Map<*, *>?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        runCatching { vm.parity.insights(path) }.onSuccess { data = it }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(),
    ) {
        val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        if (tab in setOf("Taste map", "Streams", "Devices")) item {
            Row(col, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("${d} days") }) }
            }
        }
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
        val d = data
        if (d == null) { if (error == null) item { JellyLoading(Modifier.padding(32.dp)) } } else when (tab) {
            "Recap" -> recap(d, col, openItem)
            "Taste map" -> taste(d, col)
            "Up next" -> upNext(d, col, openItem)
            "Library health" -> health(d, col, openItem)
            "Streams" -> streams(d, col)
            else -> devices(d, col)
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, trailing: String = "", content: @Composable () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BigStats(vararg items: Pair<String, String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (v, l) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
                Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: Double, max: Double, right: String, color: Color = LocalBrand.current.primary, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (onClick != null) it.then(Modifier.clip(RoundedCornerShape(6.dp))) else it }) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(right, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth((value / max.coerceAtLeast(1.0)).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun Columns(values: List<Pair<String, Double>>, color: Color = LocalBrand.current.primary) {
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { (l, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height((90 * (v / max)).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(color))
                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

private fun LazyListScope.recap(d: Map<*, *>, col: Modifier, openItem: (String, String) -> Unit) {
    val t = d["totals"].m()
    item {
        Panel("Your ${d.s("year")} on Jellyfin", col) {
            BigStats(Fields.fmtNum(t.n("plays")) to "plays", hours(t.n("seconds")) to "watched", Fields.fmtNum(t.n("active_days")) to "active days")
            Spacer(Modifier.height(8.dp))
            BigStats(Fields.fmtNum(t.n("titles")) to "titles", Fields.fmtNum(t.n("users")) to "people")
        }
    }
    d["longestBinge"].m().takeIf { it.isNotEmpty() }?.let { b ->
        item { Panel("Longest binge", col) { Text("${b.s("name")}: ${b.n("episodes").toInt()} episodes, ${hours(b.n("seconds"))} on ${Fields.formatString(b.s("day")).substringBefore(',')}", style = MaterialTheme.typography.bodyMedium) } }
    }
    listOf("topShows" to "Top shows", "topMovies" to "Top movies").forEach { (k, title) ->
        val list = d.maps(k)
        if (list.isNotEmpty()) item {
            Panel(title, col) {
                val max = list.maxOf { it.n("plays") }
                list.forEach { r -> BarRow(r.s("name"), r.n("plays"), max, "${r.n("plays").toInt()} plays") { openItem(r.s("item_id"), r.s("name")) } }
            }
        }
    }
    val months = d.maps("months")
    if (months.isNotEmpty()) item {
        Panel("By month", col) { Columns(months.map { java.time.Month.of(it.n("month").toInt().coerceIn(1, 12)).name.take(1) to it.n("seconds") }) }
    }
    val hoursOfDay = d.maps("hours")
    if (hoursOfDay.isNotEmpty()) item { Panel("Time of day", col) { Columns(hoursOfDay.map { (if (it.n("hour").toInt() % 6 == 0) it.n("hour").toInt().toString() else "") to it.n("seconds") }, LocalBrand.current.secondary) } }
    listOf("genres" to "Genres", "topUsers" to "Who watched most", "topClients" to "Apps").forEach { (k, title) ->
        val list = d.maps(k)
        if (list.isNotEmpty()) item {
            Panel(title, col) {
                val key = if (list.first().containsKey("seconds")) "seconds" else "plays"
                val max = list.maxOf { it.n(key) }
                list.forEach { r -> BarRow(r.s("name"), r.n(key), max, if (key == "seconds") hours(r.n(key)) else "${r.n(key).toInt()} plays") }
            }
        }
    }
}

private fun LazyListScope.taste(d: Map<*, *>, col: Modifier) {
    d.maps("users").forEach { u ->
        item(key = "u" + u.s("id")) {
            Panel(u.s("name"), col, "${u.n("plays").toInt()} plays · ${hours(u.n("seconds"))}") {
                val g = u.maps("genres").take(6)
                val max = g.maxOfOrNull { it.n("seconds") } ?: 1.0
                g.forEach { BarRow(it.s("name"), it.n("seconds"), max, hours(it.n("seconds"))) }
                val titles = u.maps("topTitles").take(4).joinToString(" · ") { it.s("name") }
                if (titles.isNotBlank()) Text("Most watched: $titles", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    val overlaps = d.maps("overlaps")
    if (overlaps.isNotEmpty()) item {
        Panel("Who watches alike", col) {
            overlaps.take(8).forEach { o ->
                val a = o["a"].m().s("name"); val b = o["b"].m().s("name")
                BarRow("$a & $b", o.n("similarity"), 1.0, pct(o.n("similarity")), LocalBrand.current.secondary)
                val shared = (o["sharedGenres"] as? List<*>)?.joinToString(", ").orEmpty()
                if (shared.isNotBlank()) Text("Both like $shared", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.upNext(d: Map<*, *>, col: Modifier, openItem: (String, String) -> Unit) {
    listOf("upNext" to "Up next", "abandoned" to "Stalled (no plays in ${d.n("abandonDays").toInt()} days)").forEach { (k, title) ->
        val list = d.maps(k)
        if (list.isNotEmpty()) item(key = k) {
            Panel(title, col, "${list.size}") {
                list.take(if (k == "upNext") 20 else 25).forEach { r ->
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.s("series_name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.s("user_name"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "Next: S${r.n("next_season").toInt()}E${r.n("next_episode").toInt()} ${r.s("next_name")} · ${r.n("watched").toInt()}/${r.n("total").toInt()} · ${r.s("series_status")}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                            Box(Modifier.fillMaxWidth(r.n("progress").toFloat().coerceIn(0.01f, 1f)).fillMaxHeight().background(if (k == "upNext") Good else Warn))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.health(d: Map<*, *>, col: Modifier, openItem: (String, String) -> Unit) {
    val s = d["summary"].m()
    item {
        Panel("Library health", col) {
            BigStats(Fields.fmtNum(s.n("files")) to "files", Fields.fmtBytes(s.n("total_size")) to "on disk", Fields.fmtBytes(s.n("staleSize")) to "never watched")
        }
    }
    val stale = d.maps("stale")
    if (stale.isNotEmpty()) item {
        Panel("Unwatched for ${d.n("staleDays").toInt()}+ days", col, "${stale.size}") {
            stale.take(15).forEach { r ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${r.s("name")} (${r.s("year")})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fields.fmtBytes(r.n("size")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val trans = d.maps("transcoded")
    if (trans.isNotEmpty()) item {
        Panel("Always transcoded", col) {
            val max = trans.maxOf { it.n("transcodes") }
            trans.take(10).forEach { r -> BarRow("${r.s("name")} → ${r.s("to_codec")}", r.n("transcodes"), max, "${r.n("transcodes").toInt()}×", Warn) }
        }
    }
    val dup = d.maps("duplicates")
    if (dup.isNotEmpty()) item {
        Panel("Duplicates", col, "${dup.size}") {
            dup.take(10).forEach { r -> Text("${r.s("name")} (${r.s("year")}) · ${r.n("copies").toInt()} copies", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
    val big = d.maps("largest")
    if (big.isNotEmpty()) item {
        Panel("Largest files", col) {
            val max = big.maxOf { it.n("size") }
            big.take(10).forEach { r -> BarRow(r.s("name"), r.n("size"), max, Fields.fmtBytes(r.n("size")), LocalBrand.current.secondary) }
        }
    }
}

private fun LazyListScope.streams(d: Map<*, *>, col: Modifier) {
    val s = d["summary"].m()
    item {
        Panel("Streams", col) {
            BigStats(Fields.fmtNum(s.n("plays")) to "plays", Fields.fmtNum(s.n("transcodes")) to "transcodes", pct(s.n("transcodeRate")) to "transcoded")
            s["peak"].m().takeIf { it.isNotEmpty() }?.let { p ->
                Text("Peak: ${p.n("peak").toInt()} at once, ${Fields.formatString(p.s("at"))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    val daily = d.maps("daily")
    if (daily.isNotEmpty()) item { Panel("Daily plays", col) { Columns(daily.map { "" to it.n("plays") }) } }
    listOf("methods" to "Playback method", "reasons" to "Why it transcoded", "codecs" to "Codecs", "clients" to "Apps").forEach { (k, title) ->
        val list = d.maps(k)
        if (list.isNotEmpty()) item(key = k) {
            Panel(title, col) {
                val max = list.maxOf { it.n("plays") }
                list.forEach { r ->
                    val extra = if (r.containsKey("transcodes")) " · ${r.n("transcodes").toInt()} transcoded" else ""
                    BarRow(Fields.humanize(r.s("name")), r.n("plays"), max, "${r.n("plays").toInt()}$extra", if (k == "reasons") Warn else LocalBrand.current.primary)
                }
            }
        }
    }
}

private fun LazyListScope.devices(d: Map<*, *>, col: Modifier) {
    val s = d["summary"].m()
    item {
        Panel("Devices", col) {
            BigStats(Fields.fmtNum(s.n("devices")) to "devices", Fields.fmtNum(s.n("plays")) to "plays", Fields.fmtNum(s.n("struggling")) to "struggling")
        }
    }
    d.maps("devices").forEach { dv ->
        item(key = "d" + dv.s("key")) {
            Card(col, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    dev.nerdytech.jellyglance.ui.components.DeviceIcon(dv.s("client"), dv.s("name"), 28.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(dev.nerdytech.jellyglance.ui.components.cleanDeviceName(dv.s("name")), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            listOf(dv.s("client"), "${dv.n("plays").toInt()} plays", hours(dv.n("seconds")), "seen " + Fields.formatString(dv.s("lastSeen"))).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        )
                    }
                    val tr = dv.n("transcodeRate")
                    Pill("${pct(1 - tr)} direct", if (tr > 0.5) Alert else if (tr > 0.2) Warn else Good)
                }
            }
        }
    }
}
