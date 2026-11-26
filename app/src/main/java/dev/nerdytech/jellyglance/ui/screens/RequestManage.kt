package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.ManagedRequest
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.RequestEdit
import dev.nerdytech.jellyglance.data.RequestOptions
import dev.nerdytech.jellyglance.data.RequestQueue
import dev.nerdytech.jellyglance.data.RequestRules
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Avatar
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STATUSES = listOf("All", "Pending", "Approved", "Available", "Failed", "Declined")
private val Info = Color(0xFF5B9BD5)
private val SORTS = listOf("Newest", "Oldest", "Title", "Requester", "Status")

private fun Throwable.friendly() = (this as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: message ?: "Something went wrong"

private fun matches(r: ManagedRequest, status: String) = when (status) {
    "All" -> true
    else -> r.statusLabel.equals(status, true)
}

@Composable
private fun statusColor(r: ManagedRequest): Color = when {
    r.pending -> Color(Category.REQUESTS.color)
    r.available -> Good
    r.failed || r.declined -> Alert
    r.inProgress -> Warn
    else -> Info
}

/**
 * The full request queue for a signed-in user: stats, search, sort and filters, plus (for admins)
 * approve / decline / retry one or many, approve-all, editing where a request goes, and rules.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageRequestsPane(vm: AppViewModel, openItem: (String, String) -> Unit, openRules: () -> Unit, modifier: Modifier = Modifier, openIssues: () -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isAdmin = settings.sessionRole in setOf("Owner", "Admin")
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var queue by remember { mutableStateOf<RequestQueue?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("All") }
    var type by rememberSaveable { mutableStateOf("All") }
    var user by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<ManagedRequest?>(null) }
    var editing by remember { mutableStateOf<ManagedRequest?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, List<ManagedRequest>>?>(null) }

    suspend fun load() {
        runCatching { vm.requestAdmin.queue() }.onSuccess { queue = it; error = null }.onFailure { error = it.friendly() }
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(maxOf(settings.refreshSeconds, 60) * 1000L) } }

    fun reloadSoon() = scope.launch { delay(700); load(); vm.refresh(listOf(dev.nerdytech.jellyglance.data.Catalogue.FULL_REQUESTS)) }

    fun runOne(r: ManagedRequest, action: String) {
        open = null
        scope.launch {
            busy = true
            runCatching { vm.requestAdmin.act(r, action) }
                .onSuccess {
                    val verb = when (action) { "approve" -> "Approved"; "decline" -> "Declined"; else -> "Retrying" }
                    snackbar.showSnackbar("$verb ${r.title}")
                }
                .onFailure { snackbar.showSnackbar(it.friendly()) }
            busy = false
            reloadSoon()
        }
    }

    fun runBulk(action: String, items: List<ManagedRequest>) {
        scope.launch {
            busy = true
            runCatching { vm.requestAdmin.bulk(action, items) }
                .onSuccess { res ->
                    val past = when (action) { "approve" -> "approved"; "decline" -> "declined"; else -> "retried" }
                    snackbar.showSnackbar(
                        if (res.failed == 0) "${res.succeeded} $past"
                        else "${res.succeeded} $past, ${res.failed} failed" + (res.errors.firstOrNull()?.let { " ($it)" } ?: ""),
                    )
                    selecting = false; picked = emptySet()
                }
                .onFailure { snackbar.showSnackbar(it.friendly()) }
            busy = false
            reloadSoon()
        }
    }

    val all = queue?.requests.orEmpty()
    val users = remember(all) { all.map { it.requestedBy }.filter { it.isNotBlank() }.distinct().sorted() }
    val q = query.trim()
    val shown = remember(all, q, status, type, user, sort) {
        all.asSequence()
            .filter { matches(it, status) }
            .filter { type == "All" || (type == "Movies" && it.mediaType == "movie") || (type == "TV" && it.isTv) }
            .filter { user.isBlank() || it.requestedBy == user }
            .filter { q.isBlank() || it.title.contains(q, true) || it.requestedBy.contains(q, true) || it.year.contains(q) }
            .let { s ->
                when (sort) {
                    "Oldest" -> s.sortedBy { it.createdAt ?: "" }
                    "Title" -> s.sortedBy { it.title.lowercase() }
                    "Requester" -> s.sortedWith(compareBy<ManagedRequest> { it.requestedBy.lowercase() }.thenByDescending { it.createdAt ?: "" })
                    "Status" -> s.sortedWith(compareBy<ManagedRequest> { STATUSES.indexOf(it.statusLabel).let { i -> if (i < 0) 99 else i } }.thenByDescending { it.createdAt ?: "" })
                    else -> s.sortedByDescending { it.createdAt ?: "" }
                }
            }.toList()
    }
    val pendingAll = all.filter { it.pending }
    val pickedItems = all.filter { it.key in picked }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selecting) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${picked.size} selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { picked = shown.map { it.key }.toSet() }) { Text("All ${shown.size}") }
                        TextButton(onClick = { selecting = false; picked = emptySet() }) { Text("Done") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val canApprove = pickedItems.filter { it.pending }
                        val canRetry = pickedItems.filter { it.failed }
                        Button(onClick = { confirm = "approve" to canApprove }, enabled = !busy && canApprove.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text("Approve ${canApprove.size.takeIf { it > 0 } ?: ""}".trim())
                        }
                        OutlinedButton(onClick = { confirm = "decline" to canApprove }, enabled = !busy && canApprove.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text("Decline", color = if (canApprove.isNotEmpty()) Alert else Color.Unspecified)
                        }
                        OutlinedButton(onClick = { confirm = "retry" to canRetry }, enabled = !busy && canRetry.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text("Retry ${canRetry.size.takeIf { it > 0 } ?: ""}".trim())
                        }
                    }
                    if (pickedItems.isNotEmpty() && pickedItems.none { it.pending || it.failed }) {
                        Text(
                            "Only pending requests can be approved or declined, and only failed ones retried.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                error?.let { e ->
                    item {
                        Card(col, colors = CardDefaults.cardColors(containerColor = Alert.copy(alpha = 0.12f))) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = Alert)
                                Spacer(Modifier.width(10.dp))
                                Text(e, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { scope.launch { load() } }) { Text("Retry") }
                            }
                        }
                    }
                }
                if (queue == null && error == null) item { Box(col.padding(40.dp), contentAlignment = Alignment.Center) { dev.nerdytech.jellyglance.ui.components.JellyLoading() } }
                queue?.let { qd ->
                    item {
                        Column(col, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(Icons.Rounded.HourglassTop, all.count { it.pending }, "Pending approval", Color(Category.REQUESTS.color), status == "Pending", Modifier.weight(1f)) { status = if (status == "Pending") "All" else "Pending" }
                                StatTile(Icons.Rounded.Inventory2, all.count { it.inProgress }, "In progress", Warn, status == "Approved", Modifier.weight(1f)) { status = if (status == "Approved") "All" else "Approved" }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(Icons.Rounded.TaskAlt, all.count { it.available }, "Available", Good, status == "Available", Modifier.weight(1f)) { status = if (status == "Available") "All" else "Available" }
                                StatTile(Icons.Rounded.ErrorOutline, all.count { it.failed }, "Needs attention", Alert, status == "Failed", Modifier.weight(1f)) { status = if (status == "Failed") "All" else "Failed" }
                            }
                            val synced = qd.syncedAt?.let { "Synced ${Fields.formatString(it)}" }
                            Text(
                                listOfNotNull(qd.sources.joinToString(", ").ifBlank { null }, synced, if (qd.readOnly) "read-only connection" else null).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isAdmin) item {
                        Row(col.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pendingAll.isNotEmpty()) Button(onClick = { confirm = "approve" to pendingAll }, enabled = !busy && !qd.readOnly) {
                                Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Approve all ${pendingAll.size}")
                            }
                            OutlinedButton(onClick = { selecting = !selecting; picked = emptySet() }, enabled = !qd.readOnly) {
                                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (selecting) "Cancel select" else "Select")
                            }
                            OutlinedButton(onClick = openRules) {
                                Icon(Icons.Rounded.Rule, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Rules")
                            }
                            OutlinedButton(onClick = openIssues) {
                                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Issues")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            query, { query = it }, col, singleLine = true, shape = RoundedCornerShape(16.dp),
                            placeholder = { Text("Search titles or people") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
                        )
                    }
                    item {
                        Row(col.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MenuChip(Icons.AutoMirrored.Rounded.Sort, sort, SORTS) { sort = it }
                            MenuChip(Icons.Rounded.Person, user.ifBlank { "Everyone" }, listOf("Everyone") + users) { user = if (it == "Everyone") "" else it }
                            listOf("All", "Movies", "TV").forEach { t ->
                                val n = when (t) { "Movies" -> all.count { it.mediaType == "movie" }; "TV" -> all.count { it.isTv }; else -> all.size }
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text("$t $n") })
                            }
                        }
                    }
                    item {
                        Row(col.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            STATUSES.forEach { s ->
                                val n = all.count { matches(it, s) }
                                if (s == "All" || n > 0 || s == status) FilterChip(selected = status == s, onClick = { status = s }, label = { Text("$s $n") })
                            }
                        }
                    }
                    if (shown.isEmpty()) item {
                        Text(
                            if (all.isEmpty()) "No requests yet" else "Nothing matches these filters",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(top = 24.dp),
                        )
                    }
                    items(shown, key = { it.key }) { r ->
                        val on = r.key in picked
                        RequestCard(
                            vm, r, settings.baseUrl, selecting, on,
                            modifier = col.combinedClickable(
                                onClick = { if (selecting) picked = if (on) picked - r.key else picked + r.key else open = r },
                                onLongClick = { if (isAdmin && !qd.readOnly) { selecting = true; picked = picked + r.key } },
                            ),
                        )
                    }
                }
            }
        }
    }

    open?.let { r ->
        RequestSheet(
            vm, r, isAdmin && queue?.readOnly != true, busy,
            onDismiss = { open = null },
            onAction = { a -> if (a == "decline") { open = null; confirm = "decline" to listOf(r) } else runOne(r, a) },
            onEdit = { open = null; editing = r },
            openItem = { id -> open = null; openItem(id, r.title) },
        )
    }
    editing?.let { r ->
        EditRequestSheet(vm, r, onDismiss = { editing = null }) { msg ->
            editing = null
            scope.launch { snackbar.showSnackbar(msg) }
            reloadSoon()
        }
    }
    confirm?.let { (action, items) ->
        val verb = when (action) { "approve" -> "Approve"; "decline" -> "Decline"; else -> "Retry" }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("$verb ${if (items.size == 1) items[0].title else "${items.size} requests"}?") },
            text = {
                Column {
                    if (items.size > 1) Text(items.take(6).joinToString("\n") { "• " + it.title } + if (items.size > 6) "\n…and ${items.size - 6} more" else "")
                    if (action == "decline") Text("Declined requests are removed from the queue in Seerr.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = null
                        if (items.size == 1) runOne(items[0], action) else runBulk(action, items)
                    },
                    colors = if (action == "decline") ButtonDefaults.buttonColors(containerColor = Alert) else ButtonDefaults.buttonColors(),
                ) { Text(verb) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, n: Int, label: String, color: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (on) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(Fields.fmtNum(n.toDouble()), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MenuChip(icon: ImageVector, label: String, options: List<String>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { expanded = true }, label = { Text(label, maxLines = 1) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { expanded = false; onPick(o) }) }
        }
    }
}

private fun seasonSummary(r: ManagedRequest): String {
    val n = r.requestedSeasons.map { it.number }.sorted()
    if (n.isEmpty()) return ""
    val runs = mutableListOf<String>()
    var start = n[0]; var prev = n[0]
    for (x in n.drop(1) + Int.MIN_VALUE) {
        if (x == prev + 1) { prev = x; continue }
        runs += if (start == prev) "S$start" else "S$start–$prev"
        start = x; prev = x
    }
    return runs.joinToString(", ")
}

@Composable
private fun RequestCard(vm: AppViewModel, r: ManagedRequest, baseUrl: String, selecting: Boolean, picked: Boolean, modifier: Modifier) {
    val color = statusColor(r)
    Card(
        modifier = modifier.then(if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Icon(if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
            }
            Poster(r.posterUrl, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title + if (r.year.isNotBlank()) " (${r.year})" else "",
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (r.inProgress) "In progress" else r.statusLabel, color)
                    Text(
                        listOf(if (r.isTv) "Series" else "Movie", seasonSummary(r), if (r.is4k) "4K" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.requestedBy.ifBlank { "?" }, 18.dp, Models.userImageUrl(baseUrl, r.requesterJellyfinId))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        listOfNotNull(r.requestedBy.ifBlank { null }, r.createdAt?.let { Fields.formatString(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun seasonLabel(status: Int) = when (status) { 1 -> "Pending"; 2 -> "Approved"; 3 -> "Processing"; 4 -> "Partial"; 5 -> "Available"; else -> "Requested" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RequestSheet(
    vm: AppViewModel, initial: ManagedRequest, canManage: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onAction: (String) -> Unit, onEdit: () -> Unit, openItem: (String) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var r by remember(initial.key) { mutableStateOf(initial) }
    LaunchedEffect(initial.key) { runCatching { vm.requestAdmin.detail(initial) }.onSuccess { r = it } }
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            if (r.backdropUrl != null) {
                Box(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp))) {
                    AsyncImage(r.backdropUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(r.title + if (r.year.isNotBlank()) " (${r.year})" else "", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (r.inProgress) "In progress" else r.statusLabel, statusColor(r))
                    Text(if (r.isTv) "Series" else "Movie", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.rating?.let {
                        Icon(Icons.Rounded.Star, null, tint = Warn, modifier = Modifier.size(16.dp))
                        Text(String.format("%.1f", it), style = MaterialTheme.typography.labelLarge)
                    }
                    if (r.runtime > 0) Text("${r.runtime} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(r.requestedBy.ifBlank { "?" }, 28.dp, Models.userImageUrl(settings.baseUrl, r.requesterJellyfinId))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOfNotNull("Requested by ${r.requestedBy.ifBlank { "someone" }}", r.createdAt?.let { Fields.formatString(it) }, r.source.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (r.timeline.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Progress", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        r.timeline.forEachIndexed { i, s ->
                            val c = when (s.state) { "complete" -> Good; "current" -> MaterialTheme.colorScheme.primary; "failed", "error" -> Alert; else -> MaterialTheme.colorScheme.outlineVariant }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(c))
                                Spacer(Modifier.height(4.dp))
                                Text(s.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (s.state == "pending" || s.state.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                            }
                            if (i < r.timeline.lastIndex) Box(Modifier.weight(0.4f).height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }

                if (r.isTv && r.requestedSeasons.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Seasons", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.requestedSeasons.sortedBy { it.number }.forEach { s ->
                            val c = when (s.status) { 5 -> Good; 4, 3 -> Warn; 1 -> Color(Category.REQUESTS.color); else -> Info }
                            Pill("S${s.number} · ${seasonLabel(s.status)}", c)
                        }
                    }
                }

                val where = listOfNotNull(r.rootFolder.ifBlank { null }, if (r.is4k) "4K" else null).joinToString(" · ")
                val avail = listOfNotNull(r.availability.ifBlank { null }?.let { "In Jellyfin: $it" }, r.availabilityNote.ifBlank { null }).joinToString(" · ")
                if (where.isNotBlank() || avail.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    if (where.isNotBlank()) Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (avail.isNotBlank()) Text(avail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (r.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(r.overview, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(20.dp))
                if (canManage && (r.pending || r.failed)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (r.pending) {
                            Button(onClick = { onAction("approve") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(6.dp)); Text("Approve") }
                            OutlinedButton(onClick = { onAction("decline") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Close, null, tint = Alert); Spacer(Modifier.width(6.dp)); Text("Decline") }
                        }
                        if (r.failed) Button(onClick = { onAction("retry") }, enabled = !busy, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Retry") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canManage && !r.available && !r.declined) OutlinedButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
                    r.jellyfinItemId?.let { id -> OutlinedButton(onClick = { openItem(id) }) { Text("Open in library") } }
                    if (r.openUrl.isNotBlank()) TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.openUrl))) }
                    }) { Text("Open in ${r.source.ifBlank { "Seerr" }}") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "Choose…" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { expanded = false; onPick(k) }) }
            }
        }
    }
}

/** Change which *arr server, quality profile, folder, tags and seasons a request uses (the web's edit form). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditRequestSheet(vm: AppViewModel, r: ManagedRequest, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var opts by remember { mutableStateOf<RequestOptions?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var serverId by remember { mutableStateOf(r.serverId) }
    var profileId by remember { mutableStateOf(r.profileId) }
    var root by remember { mutableStateOf(r.rootFolder) }
    var lang by remember { mutableStateOf(r.languageProfileId) }
    var tags by remember { mutableStateOf(r.tags.toSet()) }
    var is4k by remember { mutableStateOf(r.is4k) }
    var seasons by remember { mutableStateOf(r.requestedSeasons.map { it.number }.toSet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(r.key) {
        runCatching { vm.requestAdmin.options(r) }.onSuccess { o ->
            opts = o
            if (o.error.isNotBlank()) error = o.error
            // Fill blanks from the server Seerr would use by default, like the web form.
            val sv = o.servers.firstOrNull { it.id == serverId } ?: o.servers.firstOrNull { it.isDefault && it.is4k == is4k } ?: o.servers.firstOrNull()
            if (sv != null) {
                if (serverId == null) serverId = sv.id
                if (profileId == null) profileId = sv.activeProfileId
                if (root.isBlank()) root = sv.activeDirectory
                if (lang == null) lang = sv.activeLanguageProfileId
            }
        }.onFailure { error = it.friendly() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Edit request", style = MaterialTheme.typography.headlineSmall)
            Text(r.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
            val o = opts
            if (o == null && error == null) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            if (o != null && o.servers.isNotEmpty()) {
                val has4k = o.servers.any { it.is4k }
                if (has4k) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("4K", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(is4k, { v ->
                        is4k = v
                        o.servers.firstOrNull { it.is4k == v && it.isDefault }?.let { s -> serverId = s.id; profileId = s.activeProfileId; root = s.activeDirectory }
                    })
                }
                val servers = o.servers.filter { !has4k || it.is4k == is4k }.ifEmpty { o.servers }
                Picker(if (o.serviceType.isNotBlank()) o.serviceType.replaceFirstChar { it.uppercase() } + " server" else "Server", serverId?.toString() ?: "", servers.map { it.id.toString() to it.name + if (it.isDefault) " (default)" else "" }) { k ->
                    val s = o.servers.first { it.id.toString() == k }
                    serverId = s.id; profileId = s.activeProfileId; root = s.activeDirectory; lang = s.activeLanguageProfileId; tags = s.activeTags.toSet()
                }
                val sv = o.servers.firstOrNull { it.id == serverId }
                if (sv != null) {
                    Picker("Quality profile", profileId?.toString() ?: "", sv.profiles.map { it.first.toString() to it.second }) { profileId = it.toIntOrNull() }
                    Picker("Root folder", root, sv.rootFolders.map { it.first to it.first + if (it.second.isNotBlank()) "  ·  ${it.second} free" else "" }) { root = it }
                    if (sv.languageProfiles.isNotEmpty()) Picker("Language profile", lang?.toString() ?: "", sv.languageProfiles.map { it.first.toString() to it.second }) { lang = it.toIntOrNull() }
                    if (sv.tags.isNotEmpty()) {
                        Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            sv.tags.forEach { (id, label) -> FilterChip(selected = id in tags, onClick = { tags = if (id in tags) tags - id else tags + id }, label = { Text(label) }) }
                        }
                    }
                }
            }
            if (r.isTv && r.seasonCount > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Seasons", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { seasons = (1..r.seasonCount).toSet() }) { Text("All") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..r.seasonCount).forEach { n -> FilterChip(selected = n in seasons, onClick = { seasons = if (n in seasons) seasons - n else seasons + n }, label = { Text("S$n") }) }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            runCatching { vm.requestAdmin.edit(r, RequestEdit(serverId, profileId, root, lang, tags.toList(), is4k, seasons.sorted())) }
                                .onSuccess { onSaved("Request updated in ${r.source.ifBlank { "Seerr" }}") }
                                .onFailure { error = it.friendly() }
                            saving = false
                        }
                    },
                    enabled = !saving && opts != null && !(r.isTv && r.seasonCount > 0 && seasons.isEmpty()),
                    modifier = Modifier.weight(1f),
                ) { Text(if (saving) "Saving…" else "Save") }
            }
        }
    }
}

/** Request limits and auto-approval (the web's "Rules" dialog). Per-user overrides are kept untouched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestRulesScreen(vm: AppViewModel, onBack: () -> Unit) {
    var rules by remember { mutableStateOf<RequestRules?>(null) }
    var usage by remember { mutableStateOf<List<RequestRules.Usage>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var people by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { vm.requestAdmin.rules() }.onSuccess { rules = it.first; usage = it.second }.onFailure { error = it.friendly() }
        runCatching { vm.parity.people() }.onSuccess { people = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = {
                        val r = rules ?: return@TextButton
                        scope.launch {
                            saving = true
                            runCatching { vm.requestAdmin.saveRules(r) }
                                .onSuccess { rules = it.first; usage = it.second; dirty = false; snackbar.showSnackbar("Saved") }
                                .onFailure { snackbar.showSnackbar(it.friendly()) }
                            saving = false
                        }
                    }, enabled = dirty && !saving) { Text(if (saving) "Saving…" else "Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val r = rules
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            error?.let { Text(it, color = Alert, modifier = col) }
            if (r == null && error == null) dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(40.dp))
            if (r != null) {
                fun set(n: RequestRules) { rules = n; dirty = true }
                Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        ToggleRow("Limit requests", "Cap how many movies and series each person can request.", r.enabled) { set(r.copy(enabled = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LimitRow("Movies", r.movieLimit, r.movieDays, r.enabled, { set(r.copy(movieLimit = it)) }, { set(r.copy(movieDays = it)) })
                        LimitRow("Series", r.tvLimit, r.tvDays, r.enabled, { set(r.copy(tvLimit = it)) }, { set(r.copy(tvDays = it)) })
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ToggleRow("Admins are exempt", "Owners and admins can always request.", r.exemptAdmins) { set(r.copy(exemptAdmins = it)) }
                        ToggleRow("Auto-approve everything", "New requests skip the approval queue.", r.autoApproveAll) { set(r.copy(autoApproveAll = it)) }
                        ToggleRow("Request as the Seerr user", "Send requests under each person's own Seerr account when it exists.", r.requestAsSeerrUser) { set(r.copy(requestAsSeerrUser = it)) }
                    }
                }
                if (usage.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Usage this period", style = MaterialTheme.typography.titleSmall, modifier = col)
                    Spacer(Modifier.height(8.dp))
                    Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            usage.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u.user, 26.dp); Spacer(Modifier.width(10.dp))
                                    Text(u.user, modifier = Modifier.weight(1f))
                                    Text("${u.movies}/${r.movieLimit} movies · ${u.tv}/${r.tvLimit} series", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                val everyone = (people + usage.filter { u -> u.key.isNotBlank() && people.none { it.first == u.key } }.map { it.key to it.user }).distinctBy { it.first }
                if (everyone.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Per person", style = MaterialTheme.typography.titleSmall, modifier = col)
                    Spacer(Modifier.height(8.dp))
                    Card(col, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            everyone.forEach { (id, name) ->
                                val u = r.users[id] ?: RequestRules.UserRule()
                                val used = usage.firstOrNull { it.key == id }
                                fun put(n: RequestRules.UserRule) = set(r.copy(users = r.users + (id to n)))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(name, 26.dp, Models.userImageUrl(vm.settings.value.baseUrl, id)); Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.bodyLarge)
                                            Text(used?.let { "${it.movies} movies, ${it.tv} series this period" } ?: "Nothing requested this period", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        MenuChip(Icons.Rounded.Rule, "Limit: " + when (u.mode) { "custom" -> "Custom"; "unlimited" -> "No limit"; else -> "Default" }, listOf("Default", "Custom", "No limit")) {
                                            put(u.copy(mode = when (it) { "Custom" -> "custom"; "No limit" -> "unlimited"; else -> "default" }))
                                        }
                                        MenuChip(Icons.Rounded.DoneAll, "Auto-approve: " + u.autoApprove.replaceFirstChar { it.uppercase() }, listOf("Default", "Always", "Never")) {
                                            put(u.copy(autoApprove = it.lowercase()))
                                        }
                                    }
                                    if (u.mode == "custom") Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        NumberField(u.movieLimit ?: r.movieLimit, "movies", true) { put(u.copy(movieLimit = it)) }
                                        NumberField(u.tvLimit ?: r.tvLimit, "series", true) { put(u.copy(tvLimit = it)) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Smart routing is kept as it is; change it in JellyGlance on the web.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, about: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(on, onChange)
    }
}

@Composable
private fun LimitRow(label: String, limit: Int, days: Int, enabled: Boolean, onLimit: (Int) -> Unit, onDays: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NumberField(limit, "per", enabled, onLimit)
        NumberField(days, "days", enabled, onDays)
    }
}

@Composable
private fun NumberField(value: Int, suffix: String, enabled: Boolean, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter(Char::isDigit).take(4); text.toIntOrNull()?.let(onChange) },
        modifier = Modifier.width(104.dp), enabled = enabled, singleLine = true,
        suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
