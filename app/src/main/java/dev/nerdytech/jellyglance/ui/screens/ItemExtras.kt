package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.EpisodeRow
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.HistoryRow
import dev.nerdytech.jellyglance.data.ItemStats
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.SeasonRow
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Avatar
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch

/** Library stats, seasons/episodes and play history for an item; needs a signed-in session. */
@Composable
fun ItemLibrarySections(vm: AppViewModel, itemId: String, jellyGlanceUrl: String = "", onRefresh: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var stats by remember { mutableStateOf<ItemStats?>(null) }
    var seasons by remember { mutableStateOf<List<SeasonRow>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryRow>?>(null) }
    LaunchedEffect(itemId) {
        launch { runCatching { vm.library.item(itemId) }.onSuccess { stats = it } }
        launch { runCatching { vm.library.seasons(itemId) }.onSuccess { seasons = it } }
        launch { runCatching { vm.library.itemHistory(itemId).items }.onSuccess { history = it }.onFailure { history = emptyList() } }
    }
    val s = stats
    var seerr by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    LaunchedEffect(s?.tmdb) {
        val tmdb = s?.tmdb ?: return@LaunchedEffect
        seerr = vm.seerrRatings(if (s.type == "Movie") "movie" else "tv", tmdb)
    }
    if (s != null) {
        ActionBar(vm, itemId, s, jellyGlanceUrl, onRefresh)
        RatingsRow(s, seerr)
        if (s.overview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(s.overview, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Block("In your library") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(Fields.fmtNum(s.plays.toDouble()), "Plays", Modifier.weight(1f))
                Stat(if (s.playSeconds > 0) Fields.fmtDuration(s.playSeconds) else "0m", "Watched", Modifier.weight(1f))
                Stat(if (s.size > 0) Fields.fmtBytes(s.size) else "–", "On disk", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "Last played" to (s.lastPlayed?.let { Fields.formatString(it) } ?: "Never"),
                "Runtime" to (if (s.runtimeTicks > 0) Models.ticksToClock(s.runtimeTicks) else ""),
                "Rating" to listOfNotNull(s.rating?.let { String.format("%.1f", it) }, s.officialRating.takeIf { it.isNotBlank() }).joinToString(" · "),
                "Genres" to s.genres.joinToString(", "),
                "Video" to s.video, "Audio" to s.audio,
            ).filter { it.second.isNotBlank() }.forEach { (k, v) -> KV(k, v) }
            if (s.path.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.path, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (seasons.isNotEmpty()) {
        Block("Seasons") {
            val open = remember { mutableStateMapOf<String, List<EpisodeRow>?>() }
            // Episode lists, fetched up front so season counts are real (the server's counts include duplicates).
            val loaded = remember { mutableStateMapOf<String, List<EpisodeRow>>() }
            LaunchedEffect(seasons) {
                seasons.forEach { season -> launch { runCatching { vm.library.episodes(season) }.onSuccess { loaded[season.id] = it } } }
            }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            seasons.forEachIndexed { i, season ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val expanded = open.containsKey(season.id)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (expanded) open.remove(season.id) else {
                            open[season.id] = loaded[season.id]
                            if (loaded[season.id] == null) scope.launch { open[season.id] = runCatching { vm.library.episodes(season) }.getOrDefault(emptyList()) }
                        }
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(season.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOfNotNull((loaded[season.id] ?: open[season.id])?.size?.let { "$it episodes" } ?: "Counting episodes…", season.size.takeIf { it > 0 }?.let { Fields.fmtBytes(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                        val eps = open[season.id]
                        if (eps == null) CircularProgressIndicator(Modifier.size(20.dp).padding(4.dp), strokeWidth = 2.dp)
                        eps?.forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("E${e.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                                Text(e.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.aired?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    history?.let { h ->
        Block("Play history") {
            if (h.isEmpty()) Text("Nobody has played this yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            h.forEach { HistoryLine(it, showTitle = seasons.isNotEmpty(), baseUrl = settings.baseUrl) }
        }
    }
}

@Composable
fun HistoryLine(r: HistoryRow, showTitle: Boolean = true, showUser: Boolean = true, baseUrl: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showUser) { Avatar(r.user, 30.dp, dev.nerdytech.jellyglance.data.Models.userImageUrl(baseUrl, r.userId)); Spacer(Modifier.width(10.dp)) }
        else { dev.nerdytech.jellyglance.ui.components.DeviceIcon(r.client, r.device, 24.dp); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                if (showTitle) r.display else r.user, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(if (showTitle && showUser) r.user else "", r.client, r.device, if (r.seconds > 0) Fields.fmtDuration(r.seconds) else "")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.at?.let { Fields.formatString(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.method.isNotBlank()) {
                val trans = r.method.contains("transcode", true)
                Pill(if (trans) "Transcode" else "Direct", if (trans) Warn else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** Score pills like the web title page; each opens the matching site. Seerr's numbers win when present. */
@Composable
private fun RatingsRow(s: ItemStats, seerr: Map<String, Double>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isTv = s.type in setOf("Series", "Season", "Episode")
    data class Pill(val label: String, val value: String, val color: androidx.compose.ui.graphics.Color, val url: String?)
    val pills = buildList {
        // Seerr: community = TMDB vote average, rottenTomatoes/critic = RT critics; imdb only with an OMDb key.
        val tmdb = seerr["tmdb"] ?: seerr["community"]
        if (s.tmdb != null || tmdb != null) add(Pill("TMDB", tmdb?.let { "${(it * 10).toInt()}%" } ?: "Open", androidx.compose.ui.graphics.Color(0xFF01B4E4),
            s.tmdb?.let { "https://www.themoviedb.org/${if (isTv) "tv" else "movie"}/$it" }))
        val imdb = seerr["imdb"]
        if (s.imdb != null || imdb != null) add(Pill("IMDb", imdb?.let { String.format("%.1f", it) } ?: (s.rating?.let { String.format("%.1f", it) } ?: "Open"),
            androidx.compose.ui.graphics.Color(0xFFF5C518), s.imdb?.let { "https://www.imdb.com/title/$it/" }))
        val rt = seerr["rottenTomatoes"] ?: seerr["critic"] ?: s.criticRating
        add(Pill("RT", rt?.let { "${it.toInt()}%" } ?: "Search", androidx.compose.ui.graphics.Color(0xFFFA320A),
            "https://www.rottentomatoes.com/search?search=" + android.net.Uri.encode(s.name + if (s.year > 0) " ${s.year}" else "")))
        (seerr["rottenTomatoesAudience"] ?: seerr["audienceScore"])?.let { add(Pill("RT audience", "${it.toInt()}%", androidx.compose.ui.graphics.Color(0xFFFA6A0A), null)) }
        seerr["metacritic"]?.let { add(Pill("Metacritic", it.toInt().toString(), androidx.compose.ui.graphics.Color(0xFF66CC33), null)) }
        if (isTv && s.tvdb != null) add(Pill("TVDB", "Open", androidx.compose.ui.graphics.Color(0xFF6CD491), "https://thetvdb.com/dereferrer/series/${s.tvdb}"))
    }
    if (pills.isEmpty()) return
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pills.forEach { p ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(p.color.copy(alpha = 0.16f))
                    .clickable(enabled = p.url != null) { p.url?.let { dev.nerdytech.jellyglance.ui.Links.open(ctx, it) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(p.label, style = MaterialTheme.typography.labelMedium, color = p.color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(p.value, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * One clear primary action plus a "more" menu, instead of a wall of buttons.
 *
 * The Jellyfin Android apps can't be sent a link to a title, so when one is installed and Jellyfin is
 * linked, the app is launched and then told to show the title through Jellyfin's remote-control
 * "Viewing" command. Without a Jellyfin app the title opens on the Jellyfin web UI.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActionBar(vm: AppViewModel, itemId: String, s: ItemStats, jellyGlanceUrl: String, onRefresh: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val app = remember { dev.nerdytech.jellyglance.ui.Links.jellyfinApp(ctx) }
    var more by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(s.jellyfinUrl) { if (s.jellyfinUrl.isNotBlank()) vm.rememberJellyfinUrl(s.jellyfinUrl) }

    fun toast(msg: String) = android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()

    fun openInApp() {
        val pkg = app ?: return
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        opening = true
        scope.launch {
            runCatching { vm.jellyfin.showOnThisPhone(itemId, s.type.ifBlank { "Movie" }, s.name) }
                .onSuccess { toast("Opening ${s.name} in Jellyfin") }
                .onFailure { e -> toast((e as? dev.nerdytech.jellyglance.data.ApiException)?.detail?.ifBlank { null } ?: "Couldn't jump to the title: ${e.message}") }
            opening = false
        }
    }

    fun primary() = when {
        app != null && settings.jellyfinLinked -> openInApp()
        app != null -> linking = true
        else -> dev.nerdytech.jellyglance.ui.Links.browser(ctx, s.jellyfinUrl)
    }

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.jellyfinUrl.isNotBlank()) {
            androidx.compose.material3.Button(onClick = ::primary, enabled = !opening, modifier = Modifier.weight(1f).height(48.dp)) {
                if (opening) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (app != null) "Open in Jellyfin app" else "Open in Jellyfin")
            }
        } else Spacer(Modifier.weight(1f))
        androidx.compose.material3.FilledTonalIconButton(onClick = { more = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.MoreVert, "More actions")
        }
    }
    if (linking) JellyfinLinkSheet(vm, onDone = { ok -> linking = false; if (ok) openInApp() })
    if (more) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { more = false }) {
        @Composable
        fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String = "", run: () -> Unit) {
            androidx.compose.material3.ListItem(
                headlineContent = { Text(title) },
                supportingContent = sub.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                leadingContent = { Icon(icon, null) },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable { more = false; run() },
            )
        }
        Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (s.jellyfinUrl.isNotBlank() && app != null) {
            Item(Icons.Rounded.Language, "Jellyfin web", "Opens this exact title in the browser") { dev.nerdytech.jellyglance.ui.Links.browser(ctx, s.jellyfinUrl) }
        }
        s.arrLinks.forEach { (label, url) -> Item(Icons.Rounded.Dns, label) { dev.nerdytech.jellyglance.ui.Links.open(ctx, url) } }
        if (jellyGlanceUrl.isNotBlank()) Item(Icons.Rounded.Dashboard, "Open in JellyGlance", "This title on the web dashboard") {
            dev.nerdytech.jellyglance.ui.Links.browser(ctx, jellyGlanceUrl)
        }
        Item(Icons.Rounded.Refresh, "Refresh metadata", "Ask Jellyfin to rescan this title") { onRefresh() }
        if (app != null) {
            if (settings.jellyfinLinked) Item(Icons.Rounded.LinkOff, "Unlink Jellyfin", "Stop jumping straight to titles in the Jellyfin app") {
                vm.updateSettings { it.copy(jellyfinToken = "", jellyfinUserId = "") }
            } else Item(Icons.Rounded.Link, "Link Jellyfin", "Lets the button open titles directly in the Jellyfin app") { linking = true }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One-time Quick Connect against Jellyfin itself; only Jellyfin's access token is kept. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun JellyfinLinkSheet(vm: AppViewModel, onDone: (Boolean) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(settings.jellyfinUrl) }
    var qc by remember { mutableStateOf<dev.nerdytech.jellyglance.data.JellyfinRemote.QuickConnect?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(qc) {
        val code = qc ?: return@LaunchedEffect
        repeat(100) {
            kotlinx.coroutines.delay(3000)
            if (runCatching { vm.jellyfin.quickConnectApproved(url, code.secret) }.getOrDefault(false)) {
                runCatching { vm.jellyfin.quickConnectFinish(url, code.secret) }
                    .onSuccess { l -> vm.updateSettings { it.copy(jellyfinUrl = url.trimEnd('/'), jellyfinToken = l.token, jellyfinUserId = l.userId) }; onDone(true) }
                    .onFailure { error = it.message; qc = null }
                return@LaunchedEffect
            }
        }
        qc = null
        error = "The code expired. Get a new one."
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { onDone(false) }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Link Jellyfin", style = MaterialTheme.typography.titleLarge)
            Text(
                "The Jellyfin app can't be sent a link, but Jellyfin can tell it which title to show. Link once with Quick Connect.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp),
            )
            androidx.compose.material3.OutlinedTextField(
                url, { url = it.trim() }, label = { Text("Jellyfin address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            val code = qc
            if (code == null) {
                androidx.compose.material3.Button(onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { vm.jellyfin.quickConnectStart(url) }.onSuccess { qc = it }
                            .onFailure { error = (it as? dev.nerdytech.jellyglance.data.ApiException)?.detail?.ifBlank { null } ?: it.message }
                        busy = false
                    }
                }, enabled = !busy && url.startsWith("http"), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Get a Quick Connect code") }
            } else {
                Text(
                    code.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "In the Jellyfin app: menu → Settings → Quick Connect, enter this code and authorise.",
                    style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Waiting for approval…", style = MaterialTheme.typography.labelMedium)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}
