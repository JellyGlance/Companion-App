package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.MediaItem
import dev.nerdytech.jellyglance.ui.AppViewModel

/** Every title in one discover row or genre/studio/network, loaded a page at a time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverListScreen(vm: AppViewModel, sourceId: String, key: String, chipId: String?, title: String, openMedia: (MediaItem) -> Unit, onBack: () -> Unit) {
    val items = remember { mutableStateListOf<MediaItem>() }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }

    suspend fun loadNext() {
        if (loading || !hasMore) return
        loading = true
        runCatching { vm.discover.row(sourceId, key, page + 1, chipId) }
            .onSuccess { r -> page += 1; hasMore = r.hasMore; items.addAll(r.items.filter { n -> items.none { it.mediaId == n.mediaId && it.mediaType == n.mediaType } }) }
            .onFailure { hasMore = false }
        loading = false
    }
    LaunchedEffect(Unit) { loadNext() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            items(items, key = { "${it.mediaType}-${it.mediaId}" }) { m -> PosterCard(m, 104.dp) { openMedia(m) } }
            if (hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(items.size) { loadNext() }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}
