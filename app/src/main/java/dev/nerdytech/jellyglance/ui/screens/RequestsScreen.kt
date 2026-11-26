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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch

private val FILTERS = listOf("All", "Pending", "Approved", "Available", "Failed", "Declined")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    vm: AppViewModel, openItem: (String, String) -> Unit,
    openMedia: (dev.nerdytech.jellyglance.data.MediaItem) -> Unit = {}, openList: (String, String, String?, String) -> Unit = { _, _, _, _ -> },
    openRules: () -> Unit = {},
    openIssues: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val widgetRoute = Catalogue.get("requests")!!.route
    val summary = routes[widgetRoute]?.data?.let(Models::requests)
    val full = routes[Catalogue.FULL_REQUESTS]?.data?.let(Models::fullRequests)?.takeIf { it.isNotEmpty() || (summary?.total ?: 0) == 0 }
    val all = full ?: summary?.items ?: emptyList()
    var filter by rememberSaveable { mutableStateOf("All") }
    var selected by remember { mutableStateOf<Models.Request?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val watched = listOf(widgetRoute, Catalogue.FULL_REQUESTS)

    PollEffect(watched, maxOf(settings.refreshSeconds, 60)) { vm.refresh(it) }

    fun act(r: Models.Request, action: String) {
        selected = null
        scope.launch {
            vm.requestAction(r, action)
                .onSuccess {
                    val verb = when (action) { "approve" -> "Approved"; "decline" -> "Declined"; else -> "Retrying" }
                    snackbar.showSnackbar("$verb ${if (r.hasTitle) r.title else "request #${r.id}"}")
                }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Failed") }
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val titled = all.any { it.hasTitle }
    val shown = all.filter { !titled || filter == "All" || it.statusLabel == filter }

    var pane by rememberSaveable { mutableStateOf(if (settings.signedIn) 0 else 1) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(title = { BrandTitle("Requests") }, scrollBehavior = scroll)
                if (settings.signedIn) androidx.compose.material3.PrimaryTabRow(selectedTabIndex = pane) {
                    listOf("Discover", "Requests").forEachIndexed { i, t ->
                        androidx.compose.material3.Tab(selected = pane == i, onClick = { pane = i }, text = { Text(t) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        if (pane == 0 && settings.signedIn) {
            DiscoverPane(vm, openMedia, openList, Modifier.padding(top = pad.calculateTopPadding()))
            return@Scaffold
        }
        if (settings.signedIn) {
            ManageRequestsPane(vm, openItem, openRules, Modifier.padding(top = pad.calculateTopPadding()), openIssues)
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(watched); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                routes[widgetRoute]?.error?.let { e -> item { ErrorBanner(e, col) } }
                if (summary != null) {
                    item {
                        Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // The widget's own counts misfile Seerr statuses; count the full list when we have it.
                                    fun n(label: String, widget: Int) = if (full != null) all.count { it.statusLabel == label } else widget
                                    Count(if (full != null) all.size else summary.total, "Total", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                                    Count(n("Pending", summary.pending), "Pending", Color(Category.REQUESTS.color), Modifier.weight(1f))
                                    Count(n("Approved", summary.approved), "Approved", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                    Count(n("Available", summary.available), "Available", Good, Modifier.weight(1f))
                                }
                                if (full == null && all.none { it.hasTitle }) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "JellyGlance doesn't send request titles to API keys, so below are the latest ${all.size} by number. Open the web page for the full list.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = {
                                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(settings.baseUrl + "/requests"))) }
                                    }, contentPadding = PaddingValues(0.dp)) { Text("Open requests in JellyGlance") }
                                }
                            }
                        }
                    }
                }
                if (titled) item {
                    Row(col.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FILTERS.forEach { f ->
                            val n = if (f == "All") all.size else all.count { it.statusLabel == f }
                            if (f == "All" || n > 0 || f == filter) {
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(if (f == "All") "All $n" else "$f $n") })
                            }
                        }
                    }
                }
                if (!titled && shown.isNotEmpty()) item {
                    Text("LATEST REQUESTS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(top = 6.dp, start = 4.dp))
                }
                if (shown.isEmpty() && (full != null || summary != null)) {
                    item { Text("No ${if (filter == "All") "" else filter.lowercase() + " "}requests", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(top = 24.dp)) }
                }
                if (!titled) items(shown, key = { "c-" + it.sourceId + "-" + it.id }) { r ->
                    Row(
                        col.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#${r.id}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                        Text(r.requestedBy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Pill(r.statusLabel, requestColor(r.statusLabel))
                    }
                } else items(shown.take(200), key = { it.sourceId + "-" + it.id }) { r ->
                    Card(
                        onClick = { selected = r }, modifier = col, shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (r.posterUrl != null) {
                                Poster(r.posterUrl, 48.dp)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if (r.hasTitle) r.title else "Request #${r.id}", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Pill(r.statusLabel, requestColor(r.statusLabel))
                                    if (r.mediaType.isNotBlank()) Text(if (r.mediaType == "tv") "Series" else r.mediaType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    listOfNotNull(r.requestedBy.takeIf { it.isNotBlank() }, r.createdAt?.let { Fields.formatString(it) }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { r ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                if (r.backdropUrl != null) {
                    Box(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp))) {
                        AsyncImage(r.backdropUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(if (r.hasTitle) r.title else "Request #${r.id}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(r.statusLabel, requestColor(r.statusLabel))
                        r.rating?.let {
                            Icon(Icons.Rounded.Star, null, tint = Warn, modifier = Modifier.padding(start = 2.dp).width(16.dp))
                            Text(String.format("%.1f", it), style = MaterialTheme.typography.labelLarge)
                        }
                        if (r.runtime > 0) Text("${r.runtime} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(r.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        listOfNotNull(
                            r.requestedBy.takeIf { it.isNotBlank() }?.let { "Requested by $it" },
                            r.createdAt?.let { Fields.formatString(it) },
                            r.source.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (r.overview.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(r.overview, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.pending) {
                            Button(onClick = { act(r, "approve") }) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("Approve") }
                            OutlinedButton(onClick = { act(r, "decline") }) { Icon(Icons.Rounded.Close, null, tint = Alert); Spacer(Modifier.width(6.dp)); Text("Decline") }
                        }
                        if (r.failed) Button(onClick = { act(r, "retry") }) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry") }
                        r.jellyfinItemId?.let { id ->
                            TextButton(onClick = { selected = null; openItem(id, r.title) }) { Text("Open in library") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Count(n: Int, label: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(Fields.fmtNum(n.toDouble()), style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun requestColor(label: String): Color = when (label) {
    "Pending" -> Color(Category.REQUESTS.color)
    "Approved" -> MaterialTheme.colorScheme.primary
    "Available" -> Good
    "Declined", "Failed" -> Alert
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
