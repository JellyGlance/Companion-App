package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.Shelf
import dev.nerdytech.jellyglance.data.Tdarr
import dev.nerdytech.jellyglance.data.TdarrJob
import dev.nerdytech.jellyglance.data.TimelineRow
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import kotlinx.coroutines.launch

private fun errText(e: Throwable) = (e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: e.message.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onBack: () -> Unit, snackbar: SnackbarHostState? = null, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
    ) { pad -> content(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) }
}

/** Tdarr's queue, running jobs and history, like the web dashboard's Active Transcodes page. */
@Composable
fun TranscodesScreen(vm: AppViewModel, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<Tdarr?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var skip by remember { mutableStateOf<TdarrJob?>(null) }
    LaunchedEffect(reload) {
        while (true) {
            runCatching { vm.extras.tdarr() }.onSuccess { data = it; error = null }.onFailure { error = errText(it) }
            kotlinx.coroutines.delay(15_000)
        }
    }
    fun act(label: String, block: suspend () -> Unit) = scope.launch {
        runCatching { block() }.onSuccess { snackbar.showSnackbar(label); reload++ }.onFailure { snackbar.showSnackbar(errText(it)) }
    }
    Page("Active transcodes", onBack, snackbar) { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            val d = data
            if (d == null) { if (error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) } } else {
                item {
                    Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tdarr ${d.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Pill(if (d.connected) "Online" else "Offline", if (d.connected) Good else Alert)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                listOf(
                                    "Active" to d.active.size.toString(), "Queue" to Fields.fmtNum(d.queue.toDouble()),
                                    "Processed" to Fields.fmtNum(d.processed.toDouble()), "Saved" to Fields.fmtBytes(d.savedBytes.coerceAtLeast(0.0)),
                                ).forEach { (k, v) ->
                                    Column(Modifier.weight(1f)) {
                                        Text(v, style = MaterialTheme.typography.titleMedium)
                                        Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (d.errored > 0) Text("${d.errored} errored", color = Alert, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                            d.nodes.forEach { n ->
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Node ${n.name.ifBlank { n.id }}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { act(if (n.paused) "Node resumed" else "Node paused") { vm.extras.tdarrAction(if (n.paused) "resume" else "pause", nodeId = n.id) } }) {
                                        Icon(if (n.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, Modifier.size(18.dp))
                                        Text(if (n.paused) " Resume" else " Pause")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SingleChoiceSegmentedButtonRow(col) {
                        listOf("Active ${d.active.size}", "Queued ${d.queued.size}", "History").forEachIndexed { i, l ->
                            SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l, maxLines = 1) }
                        }
                    }
                }
                val jobs = when (tab) { 0 -> d.active; 1 -> d.queued; else -> d.history }
                if (jobs.isEmpty()) item { Text(if (tab == 0) "Nothing transcoding right now" else "Empty", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(16.dp)) }
                items(jobs) { j ->
                    Card(col, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(j.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(listOf(j.workKind, j.status, j.from).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (tab == 0) MiniProgress(j.progress / 100f, LocalBrand.current.primary, Modifier.padding(top = 6.dp))
                                if (tab == 2 && j.savedBytes > 0) Text("Saved ${Fields.fmtBytes(j.savedBytes)} (${j.savedPercent.toInt()}%)", style = MaterialTheme.typography.labelSmall, color = Good)
                                if (j.sizeBefore > 0 && tab != 2) Text(Fields.fmtBytes(j.sizeBefore), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (j.reason.isNotBlank()) Text(j.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (tab != 2) IconButton(onClick = { skip = j }) { Icon(Icons.Rounded.SkipNext, "Skip") }
                        }
                    }
                }
            }
        }
    }
    skip?.let { j ->
        AlertDialog(
            onDismissRequest = { skip = null }, title = { Text("Skip this job?") },
            text = { Text("Tdarr will ${if (j.nodeId.isNotBlank()) "cancel" else "skip"} \"${j.title}\".") },
            confirmButton = { TextButton(onClick = { skip = null; act("Skipped") { vm.extras.tdarrAction("skip", j.nodeId, j.id, j.workKind) } }) { Text("Skip", color = Alert) } },
            dismissButton = { TextButton(onClick = { skip = null }) { Text("Cancel") } },
        )
    }
}

/** One shelf per library of what was added most recently. */
@Composable
fun RecentlyAddedScreen(vm: AppViewModel, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var shelves by remember { mutableStateOf<List<Shelf>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { vm.extras.shelves() }.onSuccess { shelves = it }.onFailure { error = errText(it) } }
    Page("Recently added", onBack) { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (shelves == null && error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) }
            items(shelves ?: emptyList()) { s ->
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.items) { i ->
                            Column(Modifier.width(100.dp).clickable { openItem(i.imageId, i.series.ifBlank { i.name }) }) {
                                Poster(Models.posterUrl(settings.baseUrl, i.imageId, 240), 100.dp)
                                Text(i.series.ifBlank { i.name }, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text(
                                    listOfNotNull(if (i.series.isNotBlank()) i.name else null, i.added?.let { Fields.formatString(it) }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Per-user viewing timeline: what each person watched, grouped by title, newest first. */
@Composable
fun TimelineScreen(vm: AppViewModel, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var user by rememberSaveable { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<TimelineRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { vm.extras.timelineUsers() }.onSuccess { users = it; if (user.isBlank()) user = it.firstOrNull()?.first.orEmpty() }.onFailure { error = errText(it) }
    }
    LaunchedEffect(user) {
        if (user.isBlank()) return@LaunchedEffect
        rows = null
        runCatching { vm.extras.timeline(user) }.onSuccess { rows = it }.onFailure { error = errText(it) }
    }
    Page("Timeline", onBack) { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
            item {
                Row(col.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    users.forEach { (id, name) -> FilterChip(selected = user == id, onClick = { user = id }, label = { Text(name) }) }
                }
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            if (rows == null && error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) }
            if (rows?.isEmpty() == true) item { Text("Nothing watched yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col) }
            items(rows ?: emptyList()) { r ->
                Card(col.clickable { if (r.itemId.isNotBlank()) openItem(r.itemId, r.title) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    r.season.ifBlank { null }, r.episodes.takeIf { it > 0 }?.let { "$it episode${if (it == 1) "" else "s"}" },
                                    Fields.fmtDuration(r.seconds),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(r.last?.let { Fields.formatString(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
