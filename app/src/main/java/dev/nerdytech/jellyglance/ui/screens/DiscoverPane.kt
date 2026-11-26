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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.DiscoverRow
import dev.nerdytech.jellyglance.data.MediaItem
import dev.nerdytech.jellyglance.data.RowPage
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Good
import kotlinx.coroutines.delay

/** Search Seerr and browse its discover rows; tapping a title opens the media page to request it. */
@Composable
fun DiscoverPane(vm: AppViewModel, openMedia: (MediaItem) -> Unit, openList: (String, String, String?, String) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sourceId by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<DiscoverRow>>(emptyList()) }
    val pages = remember { mutableStateMapOf<String, RowPage>() }

    LaunchedEffect(Unit) {
        runCatching { vm.discover.rows() }
            .onSuccess { (src, r) -> sourceId = src; rows = r }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    // Debounced search.
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(450)
        searching = true
        results = runCatching { vm.discover.search(query.trim()) }.getOrElse { error = it.message; emptyList() }
        searching = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val col = Modifier.widthIn(max = 720.dp).fillMaxWidth()
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search movies and shows") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Clear, "Clear") } },
                shape = RoundedCornerShape(16.dp), modifier = col.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = col.padding(16.dp)) } }

        val found = results
        if (found != null || searching) {
            if (searching) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(24.dp)) }
            if (found != null && found.isEmpty() && !searching) item { Text("Nothing found for \"$query\"", modifier = col.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(found ?: emptyList()) { m -> ResultRow(m, col) { openMedia(m) } }
        } else {
            rows.forEach { row ->
                item(key = "row-${row.key}") {
                    LaunchedEffect(row.key, sourceId) {
                        if (sourceId.isNotBlank() && pages[row.key] == null) runCatching { vm.discover.row(sourceId, row.key) }.onSuccess { pages[row.key] = it }
                    }
                    val page = pages[row.key]
                    if (page == null || page.items.isNotEmpty() || page.chips.isNotEmpty()) Column(col.padding(top = 14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (row.kind == "items" && page?.hasMore == true) {
                                Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { openList(sourceId, row.key, null, row.title) }.padding(4.dp))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when {
                            page == null -> Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.width(24.dp)) }
                            row.kind == "chips" -> Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                page.chips.forEach { c -> AssistChip(onClick = { openList(sourceId, row.key, c.id, c.name) }, label = { Text(c.name) }) }
                            }
                            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(page.items) { m -> PosterCard(m) { openMedia(m) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(m: MediaItem, width: androidx.compose.ui.unit.Dp = 108.dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        Box {
            Poster(m.posterUrl, width)
            m.badge?.let { b ->
                // Solid so it stays readable on any poster.
                Text(
                    b, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(if (m.inLibrary) Good else MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(m.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(m.year, if (m.mediaType == "tv") "Series" else "Movie").filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultRow(m: MediaItem, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Poster(m.posterUrl, 54.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(m.year, if (m.mediaType == "tv") "Series" else "Movie").filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (m.overview.isNotBlank()) Text(m.overview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        m.badge?.let { Spacer(Modifier.width(8.dp)); Pill(it, if (m.inLibrary) Good else MaterialTheme.colorScheme.primary) }
    }
}
