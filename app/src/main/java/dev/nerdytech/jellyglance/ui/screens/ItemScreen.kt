package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch

/** A library title: backdrop, poster, and what JellyGlance knows about it across Seerr, the Arrs and download clients. */
@Composable
fun ItemScreen(vm: AppViewModel, itemId: String, fallbackTitle: String, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val route = "/api/item-glance/" + Uri.encode(itemId)
    val state = routes[route]
    val glance = state?.data?.let(Models::glance)
    val base = settings.baseUrl
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(route) { vm.refresh(listOf(route)) }
    // The synced Arr calendar carries plot overviews; borrow one when the title matches.
    val name = glance?.name ?: fallbackTitle
    val about = routes[Catalogue.FULL_CALENDAR]?.data?.let(Models::fullCalendar)
        ?.firstOrNull { it.title.equals(name, ignoreCase = true) && it.overview.isNotBlank() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                AsyncImage(
                    Models.backdropUrl(base, itemId), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, MaterialTheme.colorScheme.background)),
                    ),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            }
            Column(
                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp).offset(y = (-72).dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Poster(Models.posterUrl(base, itemId, 400), 112.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                        Text(glance?.name ?: fallbackTitle, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            listOfNotNull(glance?.year?.takeIf { it > 0 }?.toString(), glance?.type?.takeIf { it.isNotBlank() }, glance?.library?.takeIf { it.isNotBlank() })
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (!settings.signedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            refreshing = true
                            scope.launch {
                                vm.refreshItem(itemId)
                                    .onSuccess { snackbar.showSnackbar("Jellyfin is refreshing the metadata") }
                                    .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Refresh failed") }
                                refreshing = false
                            }
                        }, enabled = !refreshing) {
                            if (refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh metadata")
                        }
                        OutlinedButton(onClick = {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$base/libraries/item/${Uri.encode(itemId)}"))) }
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Web")
                        }
                    }
                }
                if (!settings.signedIn) about?.let { r ->
                    Spacer(Modifier.height(16.dp))
                    if (r.network.isNotBlank()) Text(r.network, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.overview, style = MaterialTheme.typography.bodyMedium)
                }
                if (settings.signedIn) ItemLibrarySections(
                    vm, itemId,
                    jellyGlanceUrl = "$base/libraries/item/${Uri.encode(itemId)}",
                    onRefresh = {
                        scope.launch {
                            vm.refreshItem(itemId)
                                .onSuccess { snackbar.showSnackbar("Jellyfin is refreshing the metadata") }
                                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Refresh failed") }
                        }
                    },
                )
                state?.error?.let { Spacer(Modifier.height(12.dp)); ErrorBanner(it) }

                glance?.let { g ->
                    Spacer(Modifier.height(16.dp))
                    InfoCard(Icons.Rounded.Inbox, "Request", Color(Category.REQUESTS.color)) {
                        if (g.request == null) Text("Not requested through Seerr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(g.request.statusLabel, if (g.request.statusLabel == "Available") Good else Color(Category.REQUESTS.color))
                            Text(g.request.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    InfoCard(Icons.Rounded.Download, "Download", Color(Category.DOWNLOADS.color)) {
                        if (g.download == null) Text("Not in the download queue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else {
                            Text(g.download.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Spacer(Modifier.height(6.dp))
                            MiniProgress(g.download.progress, Color(Category.DOWNLOADS.color))
                            Text("${(g.download.progress * 100).toInt()}% · ${g.download.state}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    InfoCard(Icons.Rounded.CalendarMonth, "Release", Color(Category.CALENDAR.color)) {
                        if (g.arr == null) Text("No upcoming release in the Arr calendar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(g.arr.date?.let { Fields.formatString(it) } ?: "", style = MaterialTheme.typography.bodySmall)
                            Text(g.arr.service, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Pill(if (g.arr.hasFile) "On disk" else "Missing", if (g.arr.hasFile) Good else Warn)
                        }
                    }
                    if (g.tools.isNotEmpty()) {
                        Text(
                            "Also connected: " + g.tools.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                }
            }
        }
        dev.nerdytech.jellyglance.ui.components.StatusBarScrim(Modifier.align(Alignment.TopCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, color: Color, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}
