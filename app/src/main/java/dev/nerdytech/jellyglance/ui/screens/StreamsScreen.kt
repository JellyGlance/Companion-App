package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.rounded.Message
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Stream
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.StreamTile
import dev.nerdytech.jellyglance.ui.theme.Alert
import kotlinx.coroutines.launch

private val STREAM_ROUTES = listOf(Catalogue.NOW_PLAYING, Catalogue.LIVE_SESSIONS)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamsScreen(vm: AppViewModel, openItem: (String, String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val now = vm.nowPlaying(routes)
    val streams = now?.streams ?: emptyList()
    val error = routes[Catalogue.NOW_PLAYING]?.error
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Stream?>(null) }
    var messaging by remember { mutableStateOf<Stream?>(null) }
    val canManage = settings.signedIn
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    PollEffect(STREAM_ROUTES, minOf(settings.refreshSeconds, 10)) { vm.refresh(it) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { BrandTitle(if (streams.isEmpty()) "Streams" else "${streams.size} streaming") },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(STREAM_ROUTES); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                error?.let { e -> item { ErrorBanner(e, col) } }
                if (now != null && streams.isEmpty()) item { EmptyStreams() }
                items(streams, key = { it.sessionId.ifBlank { it.title + it.viewer } }) { s ->
                    StreamCard(
                        s, settings.baseUrl, col, onStop = { confirm = s }, onOpen = s.itemId?.let { id -> { openItem(id, s.title) } },
                        onMessage = if (canManage && s.sessionId.isNotBlank()) ({ messaging = s }) else null,
                    )
                }
                if (now != null && !now.live && streams.isNotEmpty()) {
                    item {
                        Row(col.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Posters, progress and pause state need a full-access API key. Widget keys only get the title, viewer and device.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    messaging?.let { s ->
        MessageSheet(vm, s, onDismiss = { messaging = null }) { msg -> messaging = null; scope.launch { snackbar.showSnackbar(msg) } }
    }
    if (canManage) confirm?.let { s ->
        StopSheet(vm, s, onDismiss = { confirm = null }) { msg -> confirm = null; scope.launch { snackbar.showSnackbar(msg) } }
    } else confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.Stop, null, tint = Alert) },
            title = { Text("Stop stream?") },
            text = { Text("This ends playback of ${s.title} for ${s.viewer} on ${s.device.ifBlank { s.client }}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        if (s.sessionId.isBlank()) {
                            snackbar.showSnackbar("This stream has no session id, so it can't be stopped from here.")
                            return@launch
                        }
                        vm.stopStream(s.sessionId)
                            .onSuccess { snackbar.showSnackbar("Stopped ${s.title}") }
                            .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Couldn't stop the stream") }
                    }
                }) { Text("Stop", color = Alert) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyStreams() {
    dev.nerdytech.jellyglance.ui.components.BrandEmpty(
        "Nothing is playing",
        "JellyGlance is keeping an eye out for new streams. Pull down to check again.",
        Modifier.padding(top = 24.dp),
    )
}

@Composable
private fun StreamCard(s: Stream, baseUrl: String, modifier: Modifier, onStop: () -> Unit, onOpen: (() -> Unit)?, onMessage: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val details = listOf(
        "Client" to s.client, "Device" to s.device, "Playback" to s.methodLabel,
        "Quality" to s.resolution, "Transcode reason" to s.transcodeReason,
    ).filter { it.second.isNotBlank() }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            StreamTile(s, baseUrl, 64.dp)
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    details.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "Tap to collapse" else "Tap for details",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (onOpen != null) {
                    TextButton(onClick = onOpen) { Text("Info") }
                }
                if (onMessage != null) {
                    FilledTonalButton(onClick = onMessage, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Message, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Message")
                    }
                    Spacer(Modifier.width(6.dp))
                }
                FilledTonalButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
    }
}
