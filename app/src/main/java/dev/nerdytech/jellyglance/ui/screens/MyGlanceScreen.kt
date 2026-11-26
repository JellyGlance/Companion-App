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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Achievements
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.ForYou
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.MyItem
import dev.nerdytech.jellyglance.data.MyLists
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.components.StreamTile
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class Act(val label: String, val action: String, val icon: ImageVector)

/** The signed-in user's own page, like the web dashboard's My Glance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGlanceScreen(vm: AppViewModel, openItem: (String, String) -> Unit, onSettings: () -> Unit, onRequests: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val brand = LocalBrand.current
    var userId by remember { mutableStateOf(settings.sessionUserId) }
    var lists by remember { mutableStateOf<MyLists?>(null) }
    var forYou by remember { mutableStateOf<ForYou?>(null) }
    var achievements by remember { mutableStateOf<Achievements?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Pair<MyItem, List<Act>>?>(null) }
    var showBadges by remember { mutableStateOf(false) }
    var editGoals by remember { mutableStateOf(false) }
    val year = LocalDate.now().year
    val name = settings.sessionUser

    // Older sessions have no Jellyfin id saved; find it by name in the user list.
    LaunchedEffect(name) {
        if (userId.isBlank() && name.isNotBlank()) {
            userId = runCatching { vm.library.users().firstOrNull { it.name.equals(name, true) }?.id }.getOrNull().orEmpty()
            if (userId.isBlank()) error = "Couldn't find your Jellyfin account in JellyGlance."
            else vm.updateSettings { it.copy(sessionUserId = userId) }
        }
    }
    LaunchedEffect(userId, reload) {
        if (userId.isBlank()) return@LaunchedEffect
        launch {
            runCatching { vm.me.lists(userId) }.onSuccess { lists = it; error = null }
                .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
        }
        launch { runCatching { vm.me.forYou(userId) }.onSuccess { forYou = it } }
        launch { runCatching { vm.me.achievements(userId, year) }.onSuccess { achievements = it } }
        vm.refreshAsync(listOf(Catalogue.FULL_REQUESTS, Catalogue.FULL_CALENDAR, Catalogue.NOW_PLAYING, Catalogue.LIVE_SESSIONS))
    }

    fun run(item: MyItem, action: String, label: String) {
        selected = null
        scope.launch {
            runCatching { vm.me.action(userId, item.id, action) }
                .onSuccess { snackbar.showSnackbar("$label: ${item.title}"); reload++ }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: "Couldn't update Jellyfin") }
        }
    }

    val myRequests = routes[Catalogue.FULL_REQUESTS]?.data?.let(Models::fullRequests)?.filter { it.requestedBy.equals(name, true) } ?: emptyList()
    val myStreams = vm.nowPlaying(routes)?.streams?.filter { it.viewer.equals(name, true) } ?: emptyList()
    val l = lists
    // Releases for titles this user follows (watchlist, in progress, favourites, requests), like the web's "Coming up".
    val followed = remember(l, myRequests) {
        ((l?.watchlist.orEmpty() + l?.continueWatching.orEmpty() + l?.favourites.orEmpty() + l?.nextUp.orEmpty()).map { it.title } + myRequests.map { it.title })
            .map { it.lowercase().replace(Regex("\\s*\\(\\d{4}\\)$"), "") }.toSet()
    }
    val comingUp = routes[Catalogue.FULL_CALENDAR]?.data?.let(Models::fullCalendar)?.let { Models.splitReleases(it).first }
        ?.filter { it.title.lowercase().replace(Regex("\\s*\\(\\d{4}\\)$"), "") in followed }?.take(18) ?: emptyList()

    val watched = Act("Mark watched", "markWatched", Icons.Rounded.Check)
    val unwatched = Act("Mark unwatched", "markUnwatched", Icons.Rounded.Undo)
    val fav = Act("Favourite", "favourite", Icons.Rounded.Favorite)
    val unfav = Act("Remove from favourites", "unfavourite", Icons.Rounded.FavoriteBorder)
    val addWl = Act("Add to watchlist", "addWatchlist", Icons.Rounded.PlaylistAdd)
    val rmWl = Act("Remove from watchlist", "removeWatchlist", Icons.Rounded.PlaylistRemove)

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(brand.primary.copy(alpha = 0.28f), Color.Transparent))))
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = { refreshing = true; reload++; refreshing = false },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                val col = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
                item {
                    Row(col.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (userId.isNotBlank()) UserAvatar(vm, userId, name, 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("MY GLANCE", style = MaterialTheme.typography.labelSmall, color = brand.primary)
                            Text(name.ifBlank { "You" }, style = MaterialTheme.typography.headlineMedium)
                            Text(settings.sessionRole.ifBlank { "Viewer" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                    }
                }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col.padding(top = 12.dp)) } }

                item {
                    Card(col.padding(top = 16.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Now playing", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            if (myStreams.isEmpty()) Text("Nothing playing on your account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            myStreams.forEach { StreamTile(it, settings.baseUrl, 44.dp) }
                        }
                    }
                }
                item {
                    Row(col.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Count(l?.continueWatching?.size, "Continue", Modifier.weight(1f))
                        Count(l?.watchlist?.size, "Watchlist", Modifier.weight(1f))
                        Count(l?.favourites?.size, "Favourites", Modifier.weight(1f))
                        Count(if (l == null) null else myRequests.size, "Requests", Modifier.weight(1f), onRequests)
                    }
                }
                achievements?.let { a ->
                    item {
                        Card(col.padding(top = 12.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.EmojiEvents, null, tint = Warn)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Achievements", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${a.earned} of ${a.total}", style = MaterialTheme.typography.labelLarge, color = brand.primary)
                                }
                                Text(
                                    "${Fields.fmtNum(a.movies.toDouble())} movies, ${Fields.fmtNum(a.episodes.toDouble())} episodes and ${Fields.fmtNum(a.hours.toDouble())} hours so far.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                val goalsSet = a.goals.filter { it.target != null }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${a.year} goals", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { editGoals = true }) { Text(if (goalsSet.isEmpty()) "Set goals" else "Edit") }
                                }
                                if (goalsSet.isEmpty()) Text("No goals yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                goalsSet.forEach { g ->
                                    Text("${g.key.replaceFirstChar { it.uppercase() }}: ${g.done} of ${g.target} · ${g.status.replace('-', ' ')}", style = MaterialTheme.typography.bodySmall)
                                    MiniProgress((g.percent ?: 0) / 100f, if (g.status == "behind") Warn else Good, Modifier.padding(vertical = 4.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                (if (showBadges) a.badges else a.badges.sortedByDescending { it.tierIndex }.take(4)).forEach { b ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(b.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                b.description + " · " + Fields.fmtNum(b.value.toDouble()) + " " + b.unit +
                                                    (b.next?.let { " · next tier at ${Fields.fmtNum(it.toDouble())}" } ?: ""),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Pill(b.tier?.replaceFirstChar { it.uppercase() } ?: "Locked", tierColor(b.tierIndex, b.maxTier))
                                    }
                                }
                                TextButton(onClick = { showBadges = !showBadges }) { Text(if (showBadges) "Show fewer" else "Show all ${a.total} badges") }
                            }
                        }
                    }
                }
                if (l?.tasteGenres?.isNotEmpty() == true) item {
                    Column(col.padding(top = 14.dp)) {
                        Text("Your taste", style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            l.tasteGenres.forEach { g -> AssistChip(onClick = {}, label = { Text(g) }) }
                        }
                    }
                }
                if (l == null && error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) }
                l?.let { lists ->
                    rail("Continue watching", "Pick up where you left off", lists.continueWatching, settings.baseUrl) { selected = it to listOf(watched, fav) }
                    rail("Up next", "Next unwatched episodes from your shows", lists.nextUp, settings.baseUrl) { selected = it to listOf(watched) }
                    forYou?.let { fy ->
                        rail("Picked for you", "Unwatched titles that match what you watch most", fy.picks, settings.baseUrl) { selected = it to listOf(addWl, fav, watched) }
                        fy.because.forEach { b -> rail("Because you watched ${b.seed}", "Similar genres you haven't played yet", b.items, settings.baseUrl) { selected = it to listOf(addWl, fav) } }
                    }
                    if (comingUp.isNotEmpty()) item {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 18.dp)) {
                            Text("Coming up", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                            Text("New releases for titles you follow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(comingUp) { r ->
                                    Column(Modifier.width(104.dp)) {
                                        Poster(Models.absolute(settings.baseUrl, r.posterUrl), 104.dp)
                                        Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(dayLabel(r.date), style = MaterialTheme.typography.labelSmall, color = brand.primary)
                                    }
                                }
                            }
                        }
                    }
                    rail("Watchlist", "Saved in Jellyfin", lists.watchlist, settings.baseUrl) { selected = it to listOf(rmWl, fav, watched) }
                    rail("Been on your watchlist a while", "Saved over 30 days ago and still unwatched", lists.staleWatchlist, settings.baseUrl) { selected = it to listOf(rmWl) }
                    rail("Favourites", "Your Jellyfin favourites", lists.favourites, settings.baseUrl) { selected = it to listOf(unfav) }
                    rail("Recently watched", "What you finished lately", lists.recentlyWatched, settings.baseUrl) { selected = it to listOf(unwatched, fav) }
                }
                if (myRequests.isNotEmpty()) item {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 18.dp)) {
                        Text("My requests", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(myRequests.take(30)) { r ->
                                Column(Modifier.width(104.dp).clickable { r.jellyfinItemId?.let { id -> openItem(id, r.title) } ?: onRequests() }) {
                                    Poster(r.posterUrl, 104.dp)
                                    Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(r.statusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item { CommunityFooter(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 20.dp, bottom = 12.dp)) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    selected?.let { (item, acts) ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(Models.posterUrl(settings.baseUrl, item.imageId), 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            acts.forEach { a ->
                ListItem(
                    headlineContent = { Text(a.label) }, leadingContent = { Icon(a.icon, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { run(item, a.action, a.label) },
                )
            }
            ListItem(
                headlineContent = { Text("Open title") }, leadingContent = { Icon(Icons.Rounded.Info, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { selected = null; openItem(if (item.type == "Episode") item.imageId else item.id, item.title) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editGoals) achievements?.let { a ->
        var movies by remember { mutableStateOf(a.goals.firstOrNull { it.key == "movies" }?.target?.toString().orEmpty()) }
        var episodes by remember { mutableStateOf(a.goals.firstOrNull { it.key == "episodes" }?.target?.toString().orEmpty()) }
        var hours by remember { mutableStateOf(a.goals.firstOrNull { it.key == "hours" }?.target?.toString().orEmpty()) }
        AlertDialog(
            onDismissRequest = { editGoals = false },
            title = { Text("${a.year} goals") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Movies" to movies, "Episodes" to episodes, "Hours" to hours).forEachIndexed { i, (label, v) ->
                        OutlinedTextField(
                            v, { n -> val clean = n.filter(Char::isDigit); when (i) { 0 -> movies = clean; 1 -> episodes = clean; else -> hours = clean } },
                            label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editGoals = false
                    scope.launch {
                        runCatching { vm.me.saveGoals(a.year, movies.toIntOrNull(), episodes.toIntOrNull(), hours.toIntOrNull()) }
                            .onSuccess { snackbar.showSnackbar("Goals saved"); reload++ }
                            .onFailure { snackbar.showSnackbar("Couldn't save goals") }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editGoals = false }) { Text("Cancel") } },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rail(
    title: String, subtitle: String, items: List<MyItem>, baseUrl: String, onTap: (MyItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "rail-$title") {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(top = 18.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items) { m ->
                    Column(Modifier.width(104.dp).clickable { onTap(m) }) {
                        Poster(Models.posterUrl(baseUrl, m.imageId, 240), 104.dp)
                        if (m.progress in 1..99) MiniProgress(m.progress / 100f, MaterialTheme.colorScheme.primary, Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(m.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.note.ifBlank { m.subtitle }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Count(n: Int?, label: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(n?.let { Fields.fmtNum(it.toDouble()) } ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun tierColor(index: Int, max: Int): Color = when {
    index < 0 -> MaterialTheme.colorScheme.onSurfaceVariant
    index >= max -> Color(0xFF9FD8FF)
    index == max - 1 -> Warn
    else -> MaterialTheme.colorScheme.primary
}

