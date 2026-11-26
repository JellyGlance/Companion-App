package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.Ranked
import dev.nerdytech.jellyglance.data.Series
import dev.nerdytech.jellyglance.data.Stats
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import dev.nerdytech.jellyglance.ui.theme.Warn

/** Playback analytics for 7, 30 or 90 days, like the web dashboard's Statistics page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(vm: AppViewModel, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var days by rememberSaveable { mutableIntStateOf(30) }
    var byTime by rememberSaveable { mutableStateOf(false) }
    var stats by remember { mutableStateOf<Stats?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(days) {
        stats = null
        runCatching { vm.stats.load(days) }.onSuccess { stats = it; error = null }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("Statistics") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
            androidx.compose.material3.PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                STAT_TABS.forEachIndexed { i, t -> androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        if (tab != 0) {
            Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) { StatisticsTab(vm, STAT_TABS[tab], openItem) }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            item {
                Row(col, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        listOf(7, 30, 90).forEachIndexed { i, d ->
                            SegmentedButton(selected = days == d, onClick = { days = d }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text("${d}d") }
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        listOf("Plays", "Time").forEachIndexed { i, l ->
                            SegmentedButton(selected = byTime == (i == 1), onClick = { byTime = i == 1 }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                        }
                    }
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            val s = stats
            if (s == null) { if (error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) } } else {
                item {
                    val direct = (s.methods["DirectPlay"] ?: 0) + (s.methods["DirectStream"] ?: 0)
                    val trans = s.methods["Transcode"] ?: 0
                    val total = (direct + trans).coerceAtLeast(1)
                    Column(col, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Kpi("Total plays", Fields.fmtNum(s.totalPlays.toDouble()), Modifier.weight(1f))
                            Kpi("Direct play", "${direct * 100 / total}%", Modifier.weight(1f))
                            Kpi("Transcode", "${trans * 100 / total}%", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Kpi("Top library", s.libraries.firstOrNull()?.name ?: "–", Modifier.weight(1f), s.libraries.firstOrNull()?.let { "${it.value} plays" })
                            Kpi("Most active", s.users.firstOrNull()?.name ?: "–", Modifier.weight(1f), s.users.firstOrNull()?.let { "${it.value} plays" })
                            Kpi("Top client", s.clients.firstOrNull()?.name ?: "–", Modifier.weight(1f), s.clients.firstOrNull()?.let { "${it.value} plays" })
                        }
                    }
                }
                item { ChartCard("Daily ${if (byTime) "watch time" else "plays"}", s.daily, byTime, col) { "" } }
                item { ChartCard("By weekday", s.weekdays, byTime, col) { it.take(3) } }
                item { ChartCard("By hour", s.hours, byTime, col) { h -> h.toIntOrNull()?.let { if (it % 6 == 0) "${it}h" else "" } ?: h } }
                item { PosterRank("Most viewed movies", s.viewedMovies, "plays", settings.baseUrl, col, openItem) }
                item { PosterRank("Most viewed series", s.viewedSeries, "plays", settings.baseUrl, col, openItem) }
                item { PosterRank("Most popular movies", s.popularMovies, "viewers", settings.baseUrl, col, openItem) }
                item { PosterRank("Most popular series", s.popularSeries, "viewers", settings.baseUrl, col, openItem) }
                item { BarList("Libraries", s.libraries, col) }
                item { BarList("Users", s.users, col) }
                item { BarList("Clients", s.clients, col) }
                if (s.byType.isNotEmpty()) item { BarList("By media type", s.byType.entries.sortedByDescending { it.value }.map { Ranked(it.key, it.key, it.value) }, col) }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier, sub: String? = null) {
    Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Simple bar chart; [label] shortens the x labels (blank hides one). */
@Composable
private fun ChartCard(title: String, data: List<Series>, byTime: Boolean, modifier: Modifier, label: (String) -> String) {
    if (data.isEmpty()) return
    val brand = LocalBrand.current
    val values = data.map { if (byTime) it.minutes else it.count }
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val total = values.sum()
    StatCard(title, modifier) {
        Text(
            if (byTime) "${Fields.fmtDuration(total * 60.0)} in total" else "${Fields.fmtNum(total.toDouble())} plays in total",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = (v.toFloat() / max) * (size.height - 4f)
                drawRoundRect(
                    if (v == max) brand.secondary else brand.primary, Offset(i * w + w * 0.15f, size.height - h),
                    Size(w * 0.7f, h.coerceAtLeast(if (v > 0) 3f else 0f)), CornerRadius(4f, 4f),
                )
            }
        }
        if (data.size > 12) {
            // Too many bars to label each one: show where the range starts and ends.
            Row(Modifier.fillMaxWidth()) {
                Text(data.first().label.substringBefore(","), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(data.last().label.substringBefore(","), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Row(Modifier.fillMaxWidth()) {
            data.forEach { d -> Text(label(d.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f)) }
        }
        data.zip(values).maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (d, v) ->
            Text(
                "Peak: ${d.label} · ${if (byTime) Fields.fmtDuration(v * 60.0) else "$v plays"}",
                style = MaterialTheme.typography.labelMedium, color = Warn, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun PosterRank(title: String, list: List<Ranked>, unit: String, baseUrl: String, modifier: Modifier, openItem: (String, String) -> Unit) {
    if (list.isEmpty()) return
    StatCard(title, modifier) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { r ->
                Column(Modifier.width(92.dp).clickable { openItem(r.id, r.name) }) {
                    Box {
                        Poster(Models.posterUrl(baseUrl, r.id, 240), 92.dp)
                        Text(
                            "#${list.indexOf(r) + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White,
                            modifier = Modifier.padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(r.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text("${r.value} ${if (r.value == 1) unit.removeSuffix("s") else unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BarList(title: String, list: List<Ranked>, modifier: Modifier) {
    if (list.isEmpty()) return
    val max = list.maxOf { it.value }.coerceAtLeast(1)
    val brand = LocalBrand.current
    StatCard(title, modifier) {
        list.take(8).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(28.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(r.value.toFloat() / max).background(brand.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)))
                    Text(r.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp))
                }
                Text(Fields.fmtNum(r.value.toDouble()), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp).padding(start = 8.dp))
            }
        }
    }
}
