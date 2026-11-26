package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.HistoryRow
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.UserRow
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.components.StreamTile
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FILTERS = listOf("All", "Movies", "Episodes", "Transcodes")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** Signed-in Activity: live streams, shortcuts and the full play history like the web Activity page. */
@Composable
fun ActivityFeed(vm: AppViewModel, open: (String) -> Unit, openItem: (String, String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val brand = LocalBrand.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<HistoryRow>() }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var refreshing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    val watched = listOf("watch", "sessions").map { Catalogue.get(it)!!.route } + Catalogue.NOW_PLAYING + Catalogue.LIVE_SESSIONS
    PollEffect(watched, maxOf(settings.refreshSeconds, 30)) { vm.refresh(it) }
    val watch = routes[Catalogue.get("watch")!!.route]?.data?.let(Models::watch)
    val playback = routes[Catalogue.get("sessions")!!.route]?.data?.let(Models::playback)
    val live = vm.nowPlaying(routes)?.streams ?: emptyList()

    suspend fun more(reset: Boolean) {
        if (loading) return
        if (reset) { rows.clear(); page = 0; pages = 1 }
        if (page >= pages) return
        loading = true
        runCatching { vm.library.allHistory(page + 1, query.trim()) }
            .onSuccess { r -> page += 1; pages = r.pages; rows.addAll(r.items); error = null }
            .onFailure { error = it.message; pages = page }
        loading = false
    }
    LaunchedEffect(query, reload) { if (query.isNotEmpty()) delay(400); more(true) }

    val shown = rows.filter {
        when (filter) {
            "Movies" -> it.series.isBlank()
            "Episodes" -> it.series.isNotBlank()
            "Transcodes" -> it.method.contains("transcode", true)
            else -> true
        }
    }
    fun openUser(r: HistoryRow) = scope.launch {
        vm.selectedUser = runCatching { vm.library.users().firstOrNull { it.id == r.userId } }.getOrNull()
            ?: UserRow(r.userId, r.user, false, 0, 0.0, "", "", null)
        open("user")
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(brand.primary.copy(alpha = 0.2f), Color.Transparent))))
        PullToRefreshBox(refreshing, { scope.launch { refreshing = true; vm.refresh(watched); reload++; refreshing = false } }, Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                item { Box(col.padding(top = 16.dp)) { androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineMedium) { BrandTitle("Activity") } } }
                item {
                    Row(col, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(watch?.let { Fields.fmtDuration(it.secondsToday) } ?: "–", "Watched today", Modifier.weight(1f))
                        Tile(watch?.let { Fields.fmtDuration(it.secondsWeek) } ?: "–", "This week", Modifier.weight(1f))
                        Tile(playback?.last24h?.let { Fields.fmtNum(it.toDouble()) } ?: "–", "Plays in 24h", Modifier.weight(1f))
                    }
                }
                item {
                    Column(col, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Shortcut(Icons.Rounded.BarChart, "Statistics", Modifier.weight(1f)) { open("statistics") }
                            Shortcut(Icons.Rounded.Group, "Users", Modifier.weight(1f)) { open("users") }
                            Shortcut(Icons.Rounded.VideoLibrary, "Libraries", Modifier.weight(1f)) { open("libraries") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Shortcut(Icons.Rounded.Timeline, "Timeline", Modifier.weight(1f)) { open("timeline") }
                            Shortcut(Icons.Rounded.Memory, "Transcodes", Modifier.weight(1f)) { open("transcodes") }
                            Shortcut(Icons.Rounded.NewReleases, "New", Modifier.weight(1f)) { open("recent") }
                        }
                    }
                }
                if (live.isNotEmpty()) item {
                    Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = brand.primary.copy(alpha = 0.14f))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Live now · ${live.size}", style = MaterialTheme.typography.titleSmall)
                            live.forEach { StreamTile(it, settings.baseUrl, 40.dp) }
                        }
                    }
                }
                item {
                    Column(col.padding(top = 6.dp)) {
                        Text("History", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search titles or people") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear") } },
                            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FILTERS.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
                        }
                    }
                }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
                shown.groupBy { dayOf(it.at) }.forEach { (day, plays) ->
                    item { Text(day, style = MaterialTheme.typography.labelLarge, color = brand.primary, modifier = col.padding(top = 8.dp)) }
                    items(plays) { r -> PlayRow(r, settings.baseUrl, col, onOpen = { if (r.itemId.isNotBlank()) openItem(r.itemId, r.series.ifBlank { r.title }) }, onUser = { openUser(r) }) }
                }
                item {
                    Box(col.padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator()
                            page < pages -> LaunchedEffect(rows.size, filter) { more(false) }
                            rows.isNotEmpty() -> Text("That's everything", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Text(if (query.isBlank()) "No plays recorded yet" else "Nothing matches \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun dayOf(iso: String?): String {
    val date = iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull() } ?: return "Earlier"
    val today = LocalDate.now()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEEE"))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@Composable
private fun PlayRow(r: HistoryRow, baseUrl: String, modifier: Modifier, onOpen: () -> Unit, onUser: () -> Unit) {
    val time = r.at?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull() }
    Card(modifier.clickable(onClick = onOpen), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(Models.posterUrl(baseUrl, r.itemId.ifBlank { null }, 200), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.series.ifBlank { r.title }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.series.isNotBlank()) Text(r.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp).clickable(onClick = onUser), verticalAlignment = Alignment.CenterVertically) {
                    dev.nerdytech.jellyglance.ui.components.Avatar(r.user, 22.dp, Models.userImageUrl(baseUrl, r.userId))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        r.user + if (r.device.isNotBlank() || r.client.isNotBlank()) " · " else "",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                    dev.nerdytech.jellyglance.ui.components.DeviceIcon(r.client, r.device, 16.dp, Modifier.padding(end = 4.dp))
                    Text(
                        r.device.ifBlank { r.client },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(time ?: "", style = MaterialTheme.typography.labelMedium)
                Text(Fields.fmtDuration(r.seconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.method.isNotBlank()) {
                    val trans = r.method.contains("transcode", true)
                    Pill(if (trans) "Transcode" else "Direct", if (trans) Warn else MaterialTheme.colorScheme.primary, Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = LocalBrand.current.primary, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        }
    }
}
