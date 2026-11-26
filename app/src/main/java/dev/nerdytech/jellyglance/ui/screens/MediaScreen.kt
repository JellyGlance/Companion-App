package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.MediaDetail
import dev.nerdytech.jellyglance.data.MediaItem
import dev.nerdytech.jellyglance.data.Quota
import dev.nerdytech.jellyglance.data.Season
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch

/** A Seerr title: details, ratings, cast, trailer, and the request button (with a season picker for TV). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(vm: AppViewModel, item: MediaItem, openItem: (String, String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var quota by remember { mutableStateOf<Quota?>(null) }
    var trailer by remember { mutableStateOf<String?>(null) }
    var seasons by remember { mutableStateOf<List<Season>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(item.requested || item.inLibrary) }
    var status by remember { mutableStateOf(item.badge) }

    LaunchedEffect(item.mediaId) {
        launch { runCatching { vm.discover.detail(item.sourceId, item.mediaType, item.mediaId) }.onSuccess { detail = it } }
        launch { runCatching { vm.discover.trailerKey(item.sourceId, item.mediaType, item.mediaId) }.onSuccess { trailer = it } }
        launch { runCatching { vm.discover.quota() }.onSuccess { quota = it } }
    }

    fun send(selected: List<Int>) {
        requesting = true
        picking = false
        scope.launch {
            runCatching { vm.discover.request(item, selected) }
                .onSuccess { r ->
                    requested = true
                    status = if (r == "approved") "Approved" else "Requested"
                    vm.requestsChanged()
                    snackbar.showSnackbar(if (r == "approved") "Requested and approved: ${item.title}" else "Requested ${item.title}. Waiting for approval.")
                }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: "Request failed") }
            requesting = false
        }
    }

    fun startRequest() {
        if (item.mediaType != "tv") { send(emptyList()); return }
        picking = true
        if (seasons == null) scope.launch { seasons = runCatching { vm.discover.seasons(item.title, item.mediaId) }.getOrDefault(emptyList()) }
    }

    val d = detail
    val libraryId = d?.jellyfinItemId ?: item.jellyfinItemId
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(250.dp)) {
                AsyncImage(item.backdropUrl ?: item.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, MaterialTheme.colorScheme.background))))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            }
            Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-70).dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Poster(item.posterUrl, 112.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d?.title?.ifBlank { null } ?: item.title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            listOfNotNull(
                                (d?.year ?: item.year).takeIf { it.isNotBlank() },
                                if (item.mediaType == "tv") "Series" else "Movie",
                                d?.runtime?.takeIf { it > 0 }?.let { "$it min" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        status?.let { Spacer(Modifier.height(6.dp)); Pill(it, if (it.startsWith("In library")) Good else MaterialTheme.colorScheme.primary) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        libraryId != null && (item.inLibrary || d?.availability == "Available") ->
                            Button(onClick = { openItem(libraryId, item.title) }) { Icon(Icons.Rounded.Movie, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("In your library") }
                        requested -> FilledTonalButton(onClick = {}, enabled = false) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(status ?: "Requested") }
                        else -> Button(onClick = ::startRequest, enabled = !requesting) {
                            if (requesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Request")
                        }
                    }
                    trailer?.let { key ->
                        OutlinedButton(onClick = { dev.nerdytech.jellyglance.ui.Links.youtube(ctx, key) }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Trailer")
                        }
                    }
                }
                quota?.takeIf { it.enabled && !it.unlimited }?.let { q ->
                    val use = if (item.mediaType == "tv") q.tv else q.movie
                    use?.limit?.let { limit ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You've used ${use.used} of $limit ${if (item.mediaType == "tv") "show" else "movie"} requests in the last ${use.days} days.",
                            style = MaterialTheme.typography.labelMedium, color = if (use.used >= limit) Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (d != null && d.ratings.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        d.ratings.forEach { (k, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Star, null, tint = Warn, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                val label = when (k) { "tmdb" -> "TMDB"; "imdb" -> "IMDb"; "rottenTomatoes" -> "RT"; else -> k }
                                val value = if (k == "rottenTomatoes" || v > 10) "${v.toInt()}%" else String.format("%.1f", v)
                                Text("$label $value", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (d != null && d.genres.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(d.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val overview = d?.overview?.ifBlank { null } ?: item.overview
                if (overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(overview, style = MaterialTheme.typography.bodyMedium)
                }
                if (d != null && d.cast.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Cast", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
                        items(d.cast) { c ->
                            Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    c.imageUrl, null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                                Text(c.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(c.character, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        dev.nerdytech.jellyglance.ui.components.StatusBarScrim(Modifier.align(Alignment.TopCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }) {
            val list = seasons
            val chosen = remember(list) { mutableStateOf(list?.map { it.number }?.toSet() ?: emptySet()) }
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Request ${item.title}", style = MaterialTheme.typography.titleLarge)
                Text("Choose the seasons to request.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when {
                    list == null -> dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(16.dp))
                    list.isEmpty() -> Text("Couldn't load the season list from Seerr.", color = MaterialTheme.colorScheme.error)
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                            chosen.value = if (chosen.value.size == list.size) emptySet() else list.map { it.number }.toSet()
                        }) {
                            Checkbox(checked = chosen.value.size == list.size, onCheckedChange = null)
                            Text("All seasons", style = MaterialTheme.typography.bodyLarge)
                        }
                        list.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                chosen.value = if (s.number in chosen.value) chosen.value - s.number else chosen.value + s.number
                            }) {
                                Checkbox(checked = s.number in chosen.value, onCheckedChange = null)
                                Text(s.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (s.episodes > 0) Text("${s.episodes} episodes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = { picking = false }) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { send(chosen.value.sorted()) }, enabled = chosen.value.isNotEmpty()) {
                        Text("Request ${chosen.value.size} season${if (chosen.value.size == 1) "" else "s"}")
                    }
                }
            }
        }
    }
}
