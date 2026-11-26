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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.LibraryCard
import dev.nerdytech.jellyglance.data.LibraryEntry
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** All Jellyfin libraries with their size and how much they are watched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(vm: AppViewModel, openLibrary: (String, String, Int) -> Unit, onBack: () -> Unit) {
    var libs by remember { mutableStateOf<List<LibraryCard>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { vm.library.libraries() }.onSuccess { libs = it }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Libraries") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            if (libs == null && error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) }
            val all = libs ?: emptyList()
            val live = all.filter { it.synced > 0 }
            val empty = all.filter { it.synced == 0 }
            items(live, key = { it.id }) { l ->
                Card(
                    onClick = { openLibrary(l.id, l.name, l.items) }, modifier = col, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (l.type) { "movies" -> Icons.Rounded.Movie; "tvshows" -> Icons.Rounded.Tv; "music" -> Icons.Rounded.LibraryMusic; else -> Icons.Rounded.VideoLibrary }
                        Box(
                            Modifier.size(48.dp).background(LocalBrand.current.primary.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = LocalBrand.current.primary) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    when (l.type) {
                                        "tvshows" -> "${Fields.fmtNum(l.synced.toDouble())} shows · ${Fields.fmtNum(l.episodes.toDouble())} episodes"
                                        "music" -> "${Fields.fmtNum(l.synced.toDouble())} items"
                                        else -> "${Fields.fmtNum(l.synced.toDouble())} titles"
                                    },
                                    l.sizeLabel.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOfNotNull(
                                    l.playSeconds.takeIf { it > 0 }?.let { "${Fields.fmtDuration(it)} watched" },
                                    l.lastPlayed.takeIf { it.isNotBlank() }?.let { "last: $it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (empty.isNotEmpty()) item {
                var open by remember { mutableStateOf(false) }
                Column(col) {
                    androidx.compose.material3.TextButton(onClick = { open = !open }) {
                        Text(if (open) "Hide ${empty.size} empty libraries" else "Show ${empty.size} libraries with nothing synced")
                    }
                    if (open) {
                        Text(
                            "JellyGlance has no titles stored for these. They may have been removed from Jellyfin, or never synced.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        empty.forEach { l ->
                            Row(
                                Modifier.fillMaxWidth().clickable { openLibrary(l.id, l.name, l.items) }.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(l.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text("Jellyfin last reported ${Fields.fmtNum(l.items.toDouble())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SORTS = listOf("Date" to "Recently added", "Title" to "Title", "Views" to "Most played", "WatchTime" to "Watch time", "Size" to "Size")

/** One library as a searchable, sortable poster grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: AppViewModel, libraryId: String, name: String, expected: Int, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf("Date") }
    var query by rememberSaveable { mutableStateOf("") }
    val items = remember { mutableStateListOf<LibraryEntry>() }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(reset: Boolean) {
        if (loading) return
        if (reset) { items.clear(); page = 0; pages = 1 }
        if (page >= pages) return
        loading = true
        runCatching { vm.library.items(libraryId, page + 1, sort, sort != "Title", query.trim()) }
            .onSuccess { r -> page += 1; pages = r.pages; items.addAll(r.items.filter { n -> items.none { it.id == n.id } }) }
            .onFailure { error = it.message; pages = 0 }
        loading = false
    }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(sort, query, reload) { if (query.isNotEmpty()) delay(400); load(true) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Search $name") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear") } },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SORTS.forEach { (k, label) -> FilterChip(selected = sort == k, onClick = { sort = k }, label = { Text(label) }) }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(items, key = { it.id }) { e ->
                Column(Modifier.clickable { openItem(e.id, e.name) }) {
                    Poster(Models.posterUrl(settings.baseUrl, e.id, 240), 104.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(e.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (sort) {
                            "Views" -> "${e.plays} play${if (e.plays == 1) "" else "s"}"
                            "WatchTime" -> Fields.fmtDuration(e.playSeconds)
                            "Size" -> if (e.size > 0) Fields.fmtBytes(e.size) else "–"
                            "Date" -> e.added?.let { Fields.formatString(it) } ?: ""
                            else -> e.year.takeIf { it > 0 }?.toString() ?: ""
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
            if (page < pages) item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(items.size) { load(false) }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (items.isEmpty() && !loading && query.isBlank() && expected > 0) item(span = { GridItemSpan(maxLineSpan) }) {
                NotSynced(vm, name, expected) { vm.library.forget(libraryId); reload++ }
            } else if (items.isEmpty() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (query.isBlank()) "This library is empty" else "Nothing matches \"$query\"", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/** Jellyfin reports items but JellyGlance's database has none under this library: offer a full sync. */
@Composable
private fun NotSynced(vm: AppViewModel, name: String, expected: Int, onReload: () -> Unit) {
    var state by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Card(
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Nothing stored for this library", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Jellyfin last reported ${Fields.fmtNum(expected.toDouble())} items in $name, but JellyGlance has none stored under it. " +
                    "If the library still exists in Jellyfin, a full sync brings it back; if it was removed, there's nothing to show.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = {
                    state = "Starting…"
                    scope.launch {
                        state = runCatching { vm.library.startTask("JellyfinSync") }.fold(
                            { "Full sync started. It can take several minutes; reload when it finishes." },
                            { e -> (e as? ApiException)?.detail?.ifBlank { null } ?: "Couldn't start the sync: ${e.message}" },
                        )
                    }
                }) { Text("Run full sync") }
                androidx.compose.material3.OutlinedButton(onClick = onReload) { Text("Reload") }
            }
            state?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
