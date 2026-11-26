package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.MediaItem
import dev.nerdytech.jellyglance.data.SharedTitle
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.JellyLoading
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Good
import kotlinx.coroutines.launch

/** "Share to JellyGlance": find what was shared and open it to request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(vm: AppViewModel, shared: String, openMedia: (MediaItem) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val parsed = remember(shared) { SharedTitle.parse(shared) }
    var query by remember(shared) { mutableStateOf(parsed.query) }
    var results by remember { mutableStateOf<List<MediaItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun search(q: String, autoOpen: Boolean) {
        if (q.isBlank()) { results = emptyList(); return }
        results = null; error = null
        runCatching { vm.discover.search(q) }.onSuccess { list ->
            // A TMDB link or a single title+year match is unambiguous: open it straight away.
            val exact = parsed.tmdbId?.let { id -> list.firstOrNull { it.mediaId == id && (parsed.mediaType == null || it.mediaType == parsed.mediaType) } }
                ?: parsed.year?.let { y -> list.filter { it.year == y && it.title.equals(q, true) }.singleOrNull() }
            if (autoOpen && exact != null) openMedia(exact)
            results = list
        }.onFailure { error = it.message ?: "Search failed"; results = emptyList() }
    }
    LaunchedEffect(shared, settings.signedIn) { if (settings.signedIn) search(parsed.query, autoOpen = true) }

    Scaffold(topBar = {
        TopAppBar(title = { BrandTitle("Request") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        if (!settings.signedIn) {
            Column(Modifier.padding(pad).padding(24.dp)) {
                Text("Sign in to JellyGlance to request titles.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            item {
                OutlinedTextField(
                    query, { query = it }, col, singleLine = true, shape = RoundedCornerShape(16.dp),
                    label = { Text("Shared title") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { scope.launch { search(query, autoOpen = false) } }),
                    supportingText = { Text(listOfNotNull(parsed.year?.let { "Year $it" }, parsed.imdbId, parsed.tmdbId?.let { "TMDB $it" }).joinToString(" · ").ifBlank { "Edit and search if this isn't right" }) },
                )
            }
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            when {
                results == null -> item { JellyLoading(Modifier.padding(32.dp), "Looking it up…") }
                results!!.isEmpty() -> item { Text("Nothing found. Try a shorter title.", modifier = col, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> items(results!!, key = { it.mediaType + it.mediaId }) { m ->
                    Card(onClick = { openMedia(m) }, modifier = col, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Poster(m.posterUrl, 46.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.title + if (m.year.isNotBlank()) " (${m.year})" else "", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (m.mediaType == "tv") "Series" else "Movie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            m.badge?.let { Pill(it, if (m.inLibrary) Good else MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}
