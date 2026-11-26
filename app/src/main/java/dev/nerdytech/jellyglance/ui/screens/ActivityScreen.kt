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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.Avatar
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch

/** Who watched what: recent plays, this week's top titles and viewers, and the user roster. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    vm: AppViewModel, onUsers: () -> Unit = {}, onLibraries: () -> Unit = {}, onStatistics: () -> Unit = {}, open: (String) -> Unit = {},
    openItem: (String, String) -> Unit = { _, _ -> },
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (settings.signedIn) ActivityFeed(vm, open, openItem)
    else LegacyActivity(vm, onUsers, onLibraries, onStatistics, open)
}

/** The API-key-only Activity page (widget data). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyActivity(vm: AppViewModel, onUsers: () -> Unit, onLibraries: () -> Unit, onStatistics: () -> Unit, open: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val ids = listOf("activity", "statistics", "users", "watch", "sessions")
    val watched = ids.map { Catalogue.get(it)!!.route }
    fun data(id: String) = routes[Catalogue.get(id)!!.route]?.data
    val plays = data("activity")?.let(Models::activity) ?: emptyList()
    val stats = data("statistics")?.let(Models::statistics)
    val users = data("users")?.let(Models::users)
    val watch = data("watch")?.let(Models::watch)
    val playback = data("sessions")?.let(Models::playback)
    val error = watched.firstNotNullOfOrNull { r -> routes[r]?.error?.takeIf { routes[r]?.flat == null } }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val accent = dev.nerdytech.jellyglance.ui.theme.LocalBrand.current.primary

    PollEffect(watched, maxOf(settings.refreshSeconds, 60)) { vm.refresh(it) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { BrandTitle("Activity") }, scrollBehavior = scroll) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(watched); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                error?.let { e -> item { ErrorBanner(e, col) } }

                item {
                    Row(col, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Big(watch?.let { Fields.fmtDuration(it.secondsWeek) }, "Watched this week", Modifier.weight(1f))
                        Big(watch?.let { Fields.fmtNum(Math.round(it.secondsAll / 3600.0).toDouble()) + " h" }, "Watched all time", Modifier.weight(1f))
                        Big(playback?.last24h?.let { Fields.fmtNum(it.toDouble()) }, "Plays in 24h", Modifier.weight(1f))
                    }
                }

                if (settings.signedIn) item { Column(col) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = onStatistics, modifier = Modifier.weight(1f)) { Text("Statistics", maxLines = 1) }
                        androidx.compose.material3.FilledTonalButton(onClick = onUsers, modifier = Modifier.weight(1f)) { Text("Users", maxLines = 1) }
                        androidx.compose.material3.FilledTonalButton(onClick = onLibraries, modifier = Modifier.weight(1f)) { Text("Libraries", maxLines = 1) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { open("timeline") }, modifier = Modifier.weight(1f)) { Text("Timeline", maxLines = 1) }
                        androidx.compose.material3.OutlinedButton(onClick = { open("transcodes") }, modifier = Modifier.weight(1f)) { Text("Transcodes", maxLines = 1) }
                        androidx.compose.material3.OutlinedButton(onClick = { open("recent") }, modifier = Modifier.weight(1f)) { Text("New", maxLines = 1) }
                    }
                } }
                item {
                    Block("Recent plays", col) {
                        if (plays.isEmpty()) Muted("No playback recorded yet")
                        plays.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.viewer, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(p.viewer, p.client).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(p.playedAt?.let { Fields.formatString(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (p.method.isNotBlank()) {
                                        val trans = p.method.contains("transcode", true)
                                        Pill(if (trans) "Transcode" else "Direct", if (trans) Warn else accent)
                                    }
                                }
                            }
                        }
                    }
                }

                if (stats != null) item {
                    Block("Top titles this week", col) {
                        if (stats.titles.isEmpty()) Muted("Not enough plays yet")
                        val max = stats.titles.maxOfOrNull { it.viewers }?.coerceAtLeast(1) ?: 1
                        stats.titles.forEachIndexed { i, t -> Bar(i + 1, t.title, t.viewers, max, "viewer", accent) }
                    }
                }

                if (stats != null && stats.people.isNotEmpty()) item {
                    Block("Top viewers this week", col) {
                        val max = stats.people.maxOf { it.plays }.coerceAtLeast(1)
                        stats.people.forEachIndexed { i, p -> Bar(i + 1, p.name, p.plays, max, "play", Color(Category.LIBRARY.color)) }
                    }
                }

                if (users != null) item {
                    Block("Users", col, trailing = "${users.activeToday} active today · ${users.total} total") {
                        users.items.forEach { u ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(u.name, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(u.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                if (u.admin) { Pill("Admin", MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                                Text(u.lastAt?.let { Fields.formatString(it) } ?: "Never", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Big(value: String?, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun Block(title: String, modifier: Modifier, trailing: String? = null, content: @Composable () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Bar(rank: Int, label: String, value: Int, max: Int, unit: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp))
        Box(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value.toFloat() / max).background(color.copy(alpha = 0.35f)))
            Text(
                label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 10.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$value $unit${if (value == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
