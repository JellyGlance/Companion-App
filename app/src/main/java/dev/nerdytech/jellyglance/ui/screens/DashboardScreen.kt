package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.RouteState
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.components.StatusDot
import dev.nerdytech.jellyglance.ui.components.StreamTile
import dev.nerdytech.jellyglance.ui.components.WidgetCard
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel, openWidget: (String) -> Unit, onStreams: () -> Unit,
    openItem: (String, String) -> Unit, onRequests: () -> Unit, onBrowse: () -> Unit, onUpcoming: () -> Unit,
    onIssues: () -> Unit, onSignIn: () -> Unit, onLibraries: () -> Unit = {}, onRecent: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    val watched = remember(settings.dashboard) { vm.dashboardRoutes() }
    PollEffect(watched, settings.refreshSeconds) { vm.refresh(it) }

    fun data(id: String): Any? = routes[Catalogue.get(id)!!.route]?.data
    fun state(id: String): RouteState? = routes[Catalogue.get(id)!!.route]
    fun loaded(id: String) = state(id)?.flat != null

    val base = settings.baseUrl
    val health = data("health")?.let(Models::health)
    val healthState = state("health")
    val now = vm.nowPlaying(routes)
    val playback = data("sessions")?.let(Models::playback)
    val watch = data("watch")?.let(Models::watch)
    val catalog = data("catalog")?.let(Models::catalog)
    val storage = data("storage")?.let(Models::storage)
    val downloads = data("downloads")?.let(Models::downloads)
    val requests = data("requests")?.let(Models::requests)
    val issues = data("issues")?.let(Models::issues)
    val calendar = data("calendar")?.let(Models::calendar)
    val recent = data("recent")?.let(Models::recent) ?: emptyList()
    val extras = settings.dashboard.mapNotNull { Catalogue.get(it) }
    val firstError = healthState?.error?.takeIf { healthState.flat == null }
    val attention = dev.nerdytech.jellyglance.data.Issues.visible(vm.issues(routes), settings.dismissed)

    // Web-dashboard extras (signed-in only), refreshed every few minutes while Home is open.
    var dash by remember { mutableStateOf<dev.nerdytech.jellyglance.data.Dashboard?>(null) }
    var peak by remember { mutableStateOf<dev.nerdytech.jellyglance.data.PeakHours?>(null) }
    var runway by remember { mutableStateOf<dev.nerdytech.jellyglance.data.Runway?>(null) }
    var lastPlay by remember { mutableStateOf<dev.nerdytech.jellyglance.data.LastPlay?>(null) }
    var apps by remember { mutableStateOf<List<dev.nerdytech.jellyglance.data.AppHealth>>(emptyList()) }
    val signedIn = settings.signedIn
    val isAdmin = signedIn && settings.sessionRole in setOf("Owner", "Admin")
    androidx.compose.runtime.LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        while (true) {
            launch { runCatching { vm.insights.dashboard() }.onSuccess { dash = it } }
            launch { runCatching { vm.insights.peakHours() }.onSuccess { peak = it } }
            launch { runCatching { vm.insights.runway() }.onSuccess { runway = it } }
            launch { runCatching { vm.insights.lastPlayback() }.onSuccess { lastPlay = it } }
            if (isAdmin) launch { runCatching { vm.insights.appHealth() }.onSuccess { apps = it } }
            kotlinx.coroutines.delay(5 * 60 * 1000L)
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun openWeb(path: String) {
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(base + path))) }
    }

    fun run(label: String, block: suspend () -> Result<Unit>) {
        scope.launch {
            block().onSuccess { snackbar.showSnackbar(label) }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: e.message ?: "Failed") }
        }
    }

    val glow = LocalBrand.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(340.dp).background(
                Brush.verticalGradient(listOf(glow.primary.copy(alpha = 0.22f), glow.secondary.copy(alpha = 0.06f), Color.Transparent)),
            ),
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(watched); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                item { Header(health, attention.size, firstError != null, android.net.Uri.parse(base).host ?: base, col, onIssues) }
                item { UpdateBanner(vm, col) }
                firstError?.let { e -> item { ErrorBanner(e, col) } }
                // Can't reach the server but have the last good copy: say how old it is.
                if (firstError == null && healthState?.error != null && healthState.flat != null) item {
                    Row(
                        col.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CloudOff, null, tint = Warn, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Offline · showing what JellyGlance said " + Fields.relative(java.time.Instant.ofEpochMilli(healthState.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (settings.sessionExpired) item {
                    Card(onClick = onSignIn, modifier = col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Your JellyGlance session expired. Using the API key for now.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("Sign in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (attention.isNotEmpty()) {
                    item { Attention(attention, col, onIssues) }
                }

                item { NowPlayingCard(now, base, col, onStreams, lastPlay) }

                item {
                    Section("Today", col) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat(playback?.today, "Plays", Modifier.weight(1f)) { openWidget("sessions") }
                            Stat(playback?.viewersToday, "Viewers", Modifier.weight(1f)) { openWidget("viewers") }
                            StatText(watch?.let { Fields.fmtDuration(it.secondsToday) }, "Watched", Modifier.weight(1f)) { openWidget("watch") }
                        }
                        if (playback != null || watch != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                listOfNotNull(
                                    playback?.let { "${Fields.fmtNum(it.last24h.toDouble())} plays in 24h" },
                                    watch?.let { "${Fields.fmtDuration(it.secondsWeek)} this week" },
                                    playback?.let { "${it.users} users" },
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (signedIn) {
                    if (isAdmin) item {
                        QuickActionsCard(
                            col,
                            onSync = { run("Jellyfin sync started") { runCatching { vm.insights.syncJellyfin() } } },
                            onTest = { run("Testing integrations") { runCatching { vm.admin.testAllIntegrations() } } },
                            onBackup = { run("Backup started") { runCatching { vm.admin.backupNow() } } },
                            onRequests = onRequests,
                        )
                    }
                    peak?.let { p -> item { PeakHoursCard(p, col) } }
                    runway?.let { r -> item { RunwayCard(r, col) } }
                    dash?.let { d ->
                        item { WatchTonightRail(d, base, openItem, col) }
                        item { WeekCard(d, col) }
                        item { HallOfFameCard(vm, d, col) }
                        item {
                            val wins = routes[Catalogue.FULL_REQUESTS]?.data?.let(Models::fullRequests)?.count { it.statusLabel == "Approved" || it.statusLabel == "Available" }
                                ?: (requests?.let { it.approved + it.available } ?: 0)
                            MilestonesCard(dev.nerdytech.jellyglance.data.HomeInsights.milestones(d, wins, health?.jellyfinOk != false), col)
                        }
                    }
                    if (apps.isNotEmpty()) item { AppHealthCard(apps, col) }
                }

                item {
                    Section("Library", col, onMore = if (settings.signedIn) onLibraries else ({ openWidget("catalog") })) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat(catalog?.movies, "Movies", Modifier.weight(1f)) { openWidget("catalog") }
                            Stat(catalog?.shows, "Shows", Modifier.weight(1f)) { openWidget("catalog") }
                            Stat(catalog?.episodes, "Episodes", Modifier.weight(1f)) { openWidget("catalog") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull(
                                catalog?.let { "+${it.addedWeek} added this week" },
                                storage?.label?.takeIf { it.isNotBlank() }?.let { "$it on disk" },
                                storage?.upcomingEstimate?.takeIf { it.isNotBlank() }?.let { "~$it incoming" },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (recent.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (signedIn) "Recently added ›" else "Recently added", style = MaterialTheme.typography.labelLarge,
                                modifier = if (signedIn) Modifier.clickable(onClick = onRecent) else Modifier,
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recent) { r ->
                                    Column(Modifier.width(92.dp).clickable(enabled = r.id.isNotBlank()) { openItem(r.id, r.title) }) {
                                        Poster(Models.posterUrl(base, r.id.ifBlank { null }, 240), 92.dp)
                                        Spacer(Modifier.height(4.dp))
                                        Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(r.year.takeIf { it > 0 }?.toString(), r.added?.let { Fields.formatString(it) }).joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (downloads != null) item {
                    Section(
                        "Downloads", col,
                        badge = if (downloads.stalled > 0) "${downloads.stalled} stalled" to Warn else null,
                        onMore = { openWidget("downloads") },
                    ) {
                        if (downloads.items.isEmpty()) {
                            Empty("Nothing downloading")
                        } else downloads.items.forEachIndexed { i, d ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                    MiniProgress(d.progress, if (d.stalled) Warn else Color(Category.DOWNLOADS.color))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${(d.progress * 100).toInt()}%" + if (d.state.isNotBlank()) " · ${d.state}" else "",
                                        style = MaterialTheme.typography.labelSmall, color = if (d.stalled) Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (d.id.isNotBlank()) {
                                    IconButton(onClick = { run(if (d.paused) "Resumed" else "Paused") { vm.pauseDownload(d.id, !d.paused) } }) {
                                        Icon(if (d.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (d.paused) "Resume" else "Pause")
                                    }
                                }
                            }
                        }
                        if (downloads.total > downloads.items.size) {
                            Spacer(Modifier.height(8.dp))
                            Text("${downloads.total} in queue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (requests != null) item {
                    val full = routes[Catalogue.FULL_REQUESTS]?.data?.let(Models::fullRequests)?.takeIf { it.isNotEmpty() }
                    val rows = (full ?: requests.items.filter { it.hasTitle || it.pending || it.failed }).take(5)
                    Section("Requests", col, onMore = onRequests) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // The widget misfiles Seerr statuses; count the full list when there is one.
                            fun n(label: String, widget: Int) = full?.count { it.statusLabel == label } ?: widget
                            CountChip(n("Pending", requests.pending), "Pending", Color(Category.REQUESTS.color), Modifier.weight(1f))
                            CountChip(n("Approved", requests.approved), "Approved", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            CountChip(n("Available", requests.available), "Available", Good, Modifier.weight(1f))
                        }
                        if (issues != null && issues.open > 0) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${issues.open} open issue${if (issues.open == 1) "" else "s"}", color = Warn,
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { openWidget("issues") },
                            )
                        }
                        if (rows.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(if (full != null) "Latest" else "Needs action", style = MaterialTheme.typography.labelLarge)
                        }
                        rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (full != null) {
                                    Poster(r.posterUrl, 36.dp)
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (r.hasTitle) r.title else "Request #${r.id}",
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(r.statusLabel, statusColor(r.statusLabel))
                                        val meta = listOfNotNull(
                                            r.requestedBy.takeIf { it.isNotBlank() },
                                            r.createdAt?.let { Fields.formatString(it) },
                                        ).joinToString(" · ")
                                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                if (r.pending) {
                                    IconButton(onClick = { run("Approved") { vm.requestAction(r, "approve") } }) { Icon(Icons.Rounded.Check, "Approve", tint = Good) }
                                    IconButton(onClick = { run("Declined") { vm.requestAction(r, "decline") } }) { Icon(Icons.Rounded.Close, "Decline", tint = Alert) }
                                } else if (r.failed) {
                                    IconButton(onClick = { run("Retrying") { vm.requestAction(r, "retry") } }) { Icon(Icons.Rounded.Refresh, "Retry") }
                                }
                            }
                        }
                        if (full == null && requests.items.none { it.hasTitle } && requests.total > 0) {
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { openWeb("/requests") }, contentPadding = PaddingValues(0.dp)) { Text("Open requests in JellyGlance") }
                        }
                    }
                }

                if (calendar != null) item {
                    val full = routes[Catalogue.FULL_CALENDAR]?.data?.let(Models::fullCalendar)
                    val (upcoming, missed) = Models.splitReleases(full ?: calendar.items)
                    val today = upcoming.count { dayLabel(it.date) == "Today" }
                    Section(
                        "Coming up", col,
                        badge = if (today > 0) "$today today" to Color(Category.CALENDAR.color) else null,
                        onMore = onUpcoming,
                    ) {
                        if (upcoming.isEmpty()) {
                            Empty(if (full == null && missed.isNotEmpty()) "The calendar widget only lists older releases. Use a full-access key to see what's next." else "Nothing scheduled")
                        }
                        upcoming.take(8).groupBy { dayLabel(it.date) }.forEach { (day, rows) ->
                            Text(
                                day, style = MaterialTheme.typography.labelLarge, color = Color(Category.CALENDAR.color),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                            rows.forEach { r ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (full != null) {
                                        Poster(Models.absolute(base, r.posterUrl), 36.dp)
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOf(r.subtitle, r.service).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        if (upcoming.size > 8) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = onUpcoming, contentPadding = PaddingValues(0.dp)) { Text("See all ${upcoming.size} upcoming") }
                        }
                        if (missed.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "${missed.size}${if (full == null) "+" else ""} released in the last 5 weeks without a file yet",
                                style = MaterialTheme.typography.labelMedium, color = Warn, modifier = Modifier.clickable(onClick = onUpcoming),
                            )
                        }
                    }
                }

                if (extras.isNotEmpty()) {
                    item {
                        Text(
                            "PINNED WIDGETS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = col.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    items(extras.chunked(2), key = { row -> row.joinToString { it.id } }) { row ->
                        Row(col, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { def ->
                                WidgetCard(def, routes[def.route], settings.pinned[def.id], onClick = { openWidget(def.id) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                item {
                    TextButton(onClick = onBrowse, modifier = col) {
                        Icon(Icons.Rounded.Widgets, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pin more widgets")
                    }
                }
                if (signedIn) item { BrandStrip(vm, col) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(health: Models.Health?, issueCount: Int, offline: Boolean, host: String, modifier: Modifier, onHealth: () -> Unit) {
    val brand = LocalBrand.current
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Column(modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.jelly_logo), contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { dev.nerdytech.jellyglance.ui.components.Wordmark(26.dp) }
            val (color, label) = when {
                offline -> MaterialTheme.colorScheme.outline to "Offline"
                health == null -> MaterialTheme.colorScheme.outline to "Checking"
                !health.jellyfinOk -> Alert to "Jellyfin down"
                issueCount > 0 -> Warn to "Attention"
                else -> Good to "Healthy"
            }
            Surface(onClick = onHealth, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        val server = health?.jellyfinName?.takeIf { it.isNotBlank() }?.let { n -> n + (health.jellyfinVersion.takeIf { it.isNotBlank() }?.let { " · Jellyfin $it" } ?: "") } ?: host
        Text(server, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Attention(issues: List<dev.nerdytech.jellyglance.data.Issue>, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Warn.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Warn)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (issues.size == 1) "1 issue needs attention" else "${issues.size} issues need attention",
                    style = MaterialTheme.typography.titleSmall, color = Warn, modifier = Modifier.weight(1f),
                )
                Text("Review", style = MaterialTheme.typography.labelLarge, color = Warn)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Warn)
            }
            Spacer(Modifier.height(6.dp))
            issues.take(3).forEach {
                Text(
                    "• ${it.title}" + (if (it.count > 1) "  ×${it.count}" else "") + (if (it.summary.isNotBlank() && it.webhookId != null) " — ${it.summary}" else ""),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingCard(now: dev.nerdytech.jellyglance.data.NowPlaying?, base: String, modifier: Modifier, onStreams: () -> Unit, last: dev.nerdytech.jellyglance.data.LastPlay? = null) {
    val accent = dev.nerdytech.jellyglance.ui.theme.LocalBrand.current.primary
    Card(
        onClick = onStreams, modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when { now == null -> "Now playing"; now.playing == 0 -> "Nothing playing"; else -> "${now.playing} playing now" },
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                if (now != null && now.transcoding > 0) Pill("${now.transcoding} transcoding", Warn)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (now != null && now.streams.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    now.streams.take(3).forEach { StreamTile(it, base, 44.dp) }
                }
                if (now.streams.size > 3) {
                    Spacer(Modifier.height(8.dp))
                    Text("+${now.streams.size - 3} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (now != null) {
                if (last != null) LastPlaybackLine(last)
                else Text("Streams show up here as soon as someone presses play.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(
    title: String, modifier: Modifier, badge: Pair<String, Color>? = null, onMore: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().let { if (onMore != null) it.clickable(onClick = onMore) else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                badge?.let { (t, c) -> Pill(t, c) }
                if (onMore != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Stat(value: Int?, label: String, modifier: Modifier, onClick: () -> Unit) =
    StatText(value?.let { Fields.fmtNum(it.toDouble()) }, label, modifier, onClick)

@Composable
private fun StatText(value: String?, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun CountChip(count: Int, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(Fields.fmtNum(count.toDouble()), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun statusColor(label: String): Color = when (label) {
    "Pending" -> Color(Category.REQUESTS.color)
    "Approved" -> MaterialTheme.colorScheme.primary
    "Available" -> Good
    "Declined", "Failed" -> Alert
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun dayLabel(iso: String?): String {
    if (iso == null) return ""
    val t = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }
        .recoverCatching { LocalDate.parse(iso.take(10)).atStartOfDay(ZoneId.systemDefault()) }
        .getOrNull() ?: return iso.take(10)
    val days = ChronoUnit.DAYS.between(LocalDate.now(), t.toLocalDate())
    return when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        in 2L..6L -> t.format(DateTimeFormatter.ofPattern("EEE"))
        else -> t.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}
