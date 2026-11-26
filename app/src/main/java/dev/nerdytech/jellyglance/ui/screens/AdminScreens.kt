package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Language
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class Section(val key: String, val title: String, val about: String, val icon: ImageVector)

private val SECTIONS = listOf(
    Section("tasks", "Server tasks", "Run syncs, stats refreshes and backups", Icons.Rounded.Schedule),
    Section("downloads", "Downloads", "Pause, resume, remove and add downloads", Icons.Rounded.Download),
    Section("webhooks", "Webhooks", "Enable, test and remove webhooks", Icons.Rounded.Webhook),
    Section("integrations", "Integrations", "Health of every connected service", Icons.Rounded.Hub),
    Section("backups", "Backups", "Back up now and manage backup files", Icons.Rounded.Backup),
    Section("invites", "Wizarr invites", "Create and share invite links", Icons.Rounded.PersonAdd),
    Section("devices", "Jellyfin devices", "Clients that have signed in", Icons.Rounded.Devices),
    Section("plugins", "Jellyfin plugins", "Installed plugins and versions", Icons.Rounded.Extension),
    Section("repair", "Repair hub", "Missing artwork, empty series, failed tasks and transcoding clients", Icons.Rounded.Build),
    Section("issues", "Reported issues", "Problems people reported in Seerr: resolve, reopen or reply", Icons.Rounded.ReportProblem),
    Section("jobs", "Jellyfin scheduled tasks", "Run Jellyfin's own tasks (library scan, metadata, cleanup)", Icons.Rounded.Alarm),
    Section("logs", "Task logs", "What JellyGlance's syncs and tasks did", Icons.AutoMirrored.Rounded.Article),
    Section("keys", "API keys", "Create and revoke JellyGlance API keys", Icons.Rounded.Key),
    Section("libraries", "Library settings", "Which Jellyfin libraries JellyGlance tracks", Icons.Rounded.VideoLibrary),
    Section("monitor", "Activity monitor", "How often JellyGlance polls Jellyfin for sessions", Icons.Rounded.Speed),
    Section("web", "More on the web", "Security, kiosk, status page, newsletter, notifications and imports", Icons.Rounded.Language),
)

/** Entry point for server administration. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: AppViewModel, open: (String) -> Unit, onBack: (() -> Unit)?, openRoute: (String) -> Unit = {}) {
    Scaffold(topBar = {
        TopAppBar(
            title = { if (onBack == null) BrandTitle("Admin") else Text("Server admin") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            item { ProjectCard(vm, Modifier.widthIn(max = 640.dp).fillMaxWidth(), compact = true) }
            item {
                // Shortcuts to the other admin areas that live outside this hub.
                Row(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        Triple("Requests", "request-rules", Icons.Rounded.Rule),
                        Triple("Users", "users", Icons.Rounded.People),
                        Triple("Libraries", "libraries", Icons.Rounded.VideoLibrary),
                        Triple("Statistics", "statistics", Icons.Rounded.Insights),
                        Triple("Transcodes", "transcodes", Icons.Rounded.Memory),
                    ).forEach { (label, route, icon) ->
                        androidx.compose.material3.AssistChip(
                            onClick = { openRoute(route) }, label = { Text(if (route == "request-rules") "Request rules" else label) },
                            leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            items(SECTIONS, key = { it.key }) { s ->
                Card(
                    onClick = { open(s.key) }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall)
                            Text(s.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { CommunityFooter(Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
        }
    }
}

/** Confirmation for anything that deletes or removes. */
private data class Confirm(val title: String, val text: String, val action: String, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSectionScreen(vm: AppViewModel, key: String, onBack: () -> Unit) {
    val section = SECTIONS.firstOrNull { it.key == key } ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    /** Runs a server action, reports it, then reloads the section. */
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { snackbar.showSnackbar(done); reload++ }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: e.message ?: "Failed") }
        }
    }
    val ctx = Ctx(vm, scope, ::act) { confirm = it }

    Scaffold(
        topBar = { TopAppBar(title = { Text(section.title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = { refreshing = true; reload++; refreshing = false },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            when (key) {
                "tasks" -> Tasks(ctx, reload)
                "downloads" -> Downloads(ctx, reload)
                "webhooks" -> Webhooks(ctx, reload)
                "integrations" -> Integrations(ctx, reload)
                "backups" -> Backups(ctx, reload)
                "invites" -> Invites(ctx, reload)
                "devices" -> Devices(ctx, reload)
                "plugins" -> Plugins(ctx, reload)
                "repair" -> Repair(ctx, reload)
                "issues" -> Issues(ctx, reload)
                "jobs" -> Jobs(ctx, reload)
                "logs" -> Logs(ctx, reload)
                "keys" -> Keys(ctx, reload)
                "libraries" -> Libraries(ctx, reload)
                "monitor" -> Monitor(ctx, reload)
                "web" -> WebOnly(ctx)
            }
        }
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) }, text = { Text(c.text) },
            confirmButton = { TextButton(onClick = { confirm = null; c.run() }) { Text(c.action, color = Alert) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private class Ctx(
    val vm: AppViewModel, val scope: CoroutineScope,
    val act: (String, suspend () -> Unit) -> Unit, val confirm: (Confirm) -> Unit,
)

/** Loads [load] whenever [reload] changes and lays the result out in a centred list. */
@Composable
private fun <T> Loaded(reload: Int, load: suspend () -> T, content: LazyListScope.(T) -> Unit) {
    var data by remember { mutableStateOf<T?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) {
        runCatching { load() }.onSuccess { data = it; error = null }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize(),
    ) {
        error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) } }
        val d = data
        if (d == null) { if (error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) } } else content(d)
    }
}

@Composable
private fun Row2(title: String, subtitle: String = "", trailing: @Composable () -> Unit = {}, leading: (@Composable () -> Unit)? = null, body: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                trailing()
            }
            body()
        }
    }
}

private fun LazyListScope.empty(text: String) = item {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
}

// ------------------------------------------------------------------ sections

@Composable
private fun Tasks(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.taskRuns() }) { runs ->
        items(c.vm.admin.tasks, key = { it.key }) { t ->
            val last = runs.firstOrNull { it.name.equals(t.name, true) || it.name.contains(t.name.substringBefore(' '), true) }
            Row2(
                t.name,
                listOfNotNull(t.about, last?.let { "Last: ${it.result.ifBlank { "ran" }} ${it.ranAt?.let { a -> Fields.formatString(a) } ?: ""}".trim() }).joinToString("\n"),
                trailing = {
                    FilledTonalButton(onClick = { c.act("${t.name} started") { c.vm.admin.startTask(t.key) } }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Run")
                    }
                },
            )
        }
    }
}

@Composable
private fun Downloads(c: Ctx, reload: Int) {
    var adding by remember { mutableStateOf(false) }
    Loaded(reload, { c.vm.admin.downloads() }) { (clients, items) ->
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clients.joinToString(" · ") { "${it.name}: ${if (it.connected) "online" else it.message}" }.ifBlank { "No download clients connected" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                if (clients.any { it.connected }) FilledTonalButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Add") }
            }
            if (adding) AddDownload(c, clients.filter { it.connected }) { adding = false }
        }
        if (items.isEmpty()) empty("The queue is empty")
        items(items, key = { it.id }) { d ->
            Row2(
                d.name,
                listOf(d.client, d.state, d.size).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = {
                    if (!d.done) IconButton(onClick = { c.act(if (d.paused) "Resumed" else "Paused") { c.vm.admin.pause(d.id, !d.paused) } }) {
                        Icon(if (d.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (d.paused) "Resume" else "Pause")
                    }
                    IconButton(onClick = {
                        c.confirm(Confirm("Remove download?", "Removes \"${d.name}\" from ${d.client}. Downloaded files are kept.", "Remove") {
                            c.act("Removed") { c.vm.admin.remove(d.id, false) }
                        })
                    }) { Icon(Icons.Rounded.Delete, "Remove") }
                },
            ) {
                Spacer(Modifier.height(8.dp))
                MiniProgress(d.progress / 100f, if (d.error.isNotBlank()) Alert else MaterialTheme.colorScheme.primary)
                Text(
                    listOfNotNull(
                        "${d.progress}%",
                        d.down.takeIf { it.isNotBlank() && !it.startsWith("0") }?.let { "↓ $it" },
                        d.eta?.let { "${Fields.fmtDuration(it.toDouble())} left" },
                        if (d.seeds + d.peers > 0) "${d.seeds} seeds · ${d.peers} peers" else null,
                        d.error.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = if (d.error.isNotBlank()) Alert else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AddDownload(c: Ctx, clients: List<dev.nerdytech.jellyglance.data.DownloadClient>, onDone: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var client by remember { mutableStateOf(clients.firstOrNull()) }
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(value, { value = it }, label = { Text("Magnet link or .torrent URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (clients.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            clients.forEach { cl -> FilterChip(selected = client == cl, onClick = { client = cl }, label = { Text(cl.name) }) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cl = client ?: return@Button
                c.act("Sent to ${cl.name}") { c.vm.admin.add(cl, value) }
                onDone()
            }, enabled = value.startsWith("magnet:") || value.startsWith("http")) { Text("Add download") }
            TextButton(onClick = onDone) { Text("Cancel") }
        }
    }
}

@Composable
private fun Webhooks(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.webhooks() }) { hooks ->
        if (hooks.isEmpty()) empty("No webhooks set up")
        items(hooks, key = { it.id }) { w ->
            Row2(
                w.name.ifBlank { "Webhook ${w.id}" },
                listOf(w.type.replaceFirstChar { it.uppercase() }, w.trigger.replace('_', ' '), w.event.replace('_', ' ')).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = { Switch(checked = w.enabled, onCheckedChange = { on -> c.act(if (on) "Enabled" else "Disabled") { c.vm.admin.setWebhookEnabled(w, on) } }) },
            ) {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { c.act("Test sent to ${w.name}") { c.vm.admin.testWebhook(w.id) } }) { Text("Send test") }
                    TextButton(onClick = {
                        c.confirm(Confirm("Delete webhook?", "\"${w.name}\" will stop sending notifications. This can't be undone.", "Delete") {
                            c.act("Deleted") { c.vm.admin.deleteWebhook(w.id) }
                        })
                    }) { Text("Delete", color = Alert) }
                }
            }
        }
    }
}

@Composable
private fun Integrations(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.health() }) { services ->
        item {
            FilledTonalButton(onClick = { c.act("Testing every integration; results update shortly") { c.vm.admin.testAllIntegrations() } }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Test all integrations")
            }
        }
        if (services.isEmpty()) empty("No integrations connected")
        items(services) { s ->
            Row2(
                s.name, if (s.issues.isEmpty()) "No issues" else s.issues.take(4).joinToString("\n"),
                trailing = { Icon(if (s.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (s.ok) Good else Alert) },
            )
        }
    }
}

@Composable
private fun Backups(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.backups() }) { files ->
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                Button(onClick = { c.act("Backup started") { c.vm.admin.backupNow() } }, modifier = Modifier.fillMaxWidth()) { Text("Back up now") }
                Text(
                    "Restoring replaces the whole JellyGlance database, so do that from the web dashboard.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (files.isEmpty()) empty("No backup files yet")
        items(files, key = { it.name }) { f ->
            Row2(
                f.name, listOfNotNull(f.created?.let { Fields.formatString(it) }, Fields.fmtBytes(f.size)).joinToString(" · "),
                trailing = {
                    IconButton(onClick = {
                        c.confirm(Confirm("Delete backup?", "${f.name} will be deleted from the server. This can't be undone.", "Delete") {
                            c.act("Deleted") { c.vm.admin.deleteBackup(f.name) }
                        })
                    }) { Icon(Icons.Rounded.Delete, "Delete") }
                },
            )
        }
    }
}

@Composable
private fun Invites(c: Ctx, reload: Int) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    Loaded(reload, { c.vm.admin.wizarr() }) { (invites, servers) ->
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                if (!creating) Button(onClick = { creating = true }, enabled = servers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New invite")
                } else NewInvite(c, servers) { creating = false }
            }
        }
        if (invites.isEmpty()) empty("No invites")
        items(invites, key = { it.id.ifBlank { it.code } }) { i ->
            val used = i.status.equals("used", true)
            Row2(
                i.code.ifBlank { "Invite" },
                listOfNotNull(
                    i.servers.joinToString(", ").takeIf { it.isNotBlank() },
                    if (used) "Used by ${i.usedBy.ifBlank { "someone" }}" else i.expires?.let { "Expires " + Fields.formatString(it) } ?: "Never expires",
                ).joinToString(" · "),
                trailing = {
                    Pill(i.status.replaceFirstChar { it.uppercase() }, if (used) MaterialTheme.colorScheme.onSurfaceVariant else Good)
                    if (!used && i.url.isNotBlank()) IconButton(onClick = {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, i.url), "Share invite"))
                    }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton(onClick = {
                        c.confirm(Confirm("Delete invite?", "The link for ${i.code} will stop working.", "Delete") { c.act("Deleted") { c.vm.admin.deleteInvite(i.id) } })
                    }) { Icon(Icons.Rounded.Delete, "Delete") }
                },
            )
        }
    }
}

@Composable
private fun NewInvite(c: Ctx, servers: List<dev.nerdytech.jellyglance.data.WizarrServer>, onDone: () -> Unit) {
    var chosen by remember { mutableStateOf(servers.map { it.id }.toSet()) }
    var days by remember { mutableStateOf<Int?>(7) }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text("New Wizarr invite", style = MaterialTheme.typography.titleSmall)
            servers.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { chosen = if (s.id in chosen) chosen - s.id else chosen + s.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = s.id in chosen, onCheckedChange = null); Text(s.name)
                }
            }
            Text("Link expires", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 7, 30, null).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text(d?.let { "$it day${if (it == 1) "" else "s"}" } ?: "Never") }) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { c.act("Invite created") { c.vm.admin.createInvite(chosen.toList(), days) }; onDone() }, enabled = chosen.isNotEmpty()) { Text("Create") }
                TextButton(onClick = onDone) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Devices(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.devices() }) { list ->
        if (list.isEmpty()) empty("No devices")
        items(list.size) { i ->
            val d = list[i]
            Row2(
                d.name, listOf(d.app, d.user, d.lastActive?.let { Fields.formatString(it) } ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                leading = {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        dev.nerdytech.jellyglance.ui.components.DeviceIcon(d.app, d.name, 26.dp)
                    }
                },
            )
        }
    }
}

@Composable
private fun Plugins(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.admin.plugins() }) { list ->
        if (list.isEmpty()) empty("No plugins")
        items(list.size) { i ->
            val p = list[i]
            Row2(p.name, listOf(p.version, p.status).filter { it.isNotBlank() }.joinToString(" · "),
                trailing = { if (p.status.isNotBlank()) Pill(p.status, if (p.status.equals("Active", true)) Good else Warn) })
        }
    }
}


private val REPAIR_LABELS = listOf(
    "missingPosters" to "Missing posters", "missingLogos" to "Missing logos", "missingRuntime" to "Missing runtime",
    "emptySeries" to "Empty series", "taskFailures" to "Task failures", "transcodeClients" to "Clients that transcode",
    "failedStarts" to "Failed starts", "unmatchedImports" to "Unmatched imported history", "orphanedActivity" to "Orphaned activity",
)

@Composable
private fun Repair(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.parity.repair() }) { r ->
        item {
            Row2(
                "${Fields.fmtNum(r.counts.values.sum().toDouble())} items need attention",
                "Checked ${r.checkedAt?.let { Fields.formatString(it) } ?: "just now"}",
                trailing = {
                    FilledTonalButton(onClick = { c.act("Library scan started in Jellyfin") { c.vm.parity.scanLibraries() } }) { Text("Scan libraries") }
                },
            )
        }
        REPAIR_LABELS.forEach { (k, label) ->
            val n = r.counts[k] ?: return@forEach
            val samples = r.samples[k].orEmpty()
            item(key = k) {
                var open by remember { mutableStateOf(false) }
                Row2(
                    label, if (samples.isEmpty()) "" else if (open) "Tap to hide examples" else "Tap for examples",
                    trailing = { Pill(Fields.fmtNum(n.toDouble()), if (n == 0) Good else Warn) },
                ) {
                    if (samples.isNotEmpty()) Column(Modifier.fillMaxWidth().clickable { open = !open }) {
                        if (open) samples.forEach { m ->
                            val name = (m["Name"] ?: m["DeviceName"] ?: m["Client"])?.toString().orEmpty()
                            val extra = listOfNotNull(
                                (m["Type"] as? String), (m["ProductionYear"] as? Double)?.toInt()?.toString(),
                                (m["Client"] as? String)?.takeIf { m["DeviceName"] != null },
                                (m["Transcodes"] as? Double)?.let { t -> "${t.toInt()} of ${(m["Plays"] as? Double)?.toInt() ?: "?"} plays transcoded" },
                                (m["Result"] as? String), (m["TimeRun"] as? String)?.let { Fields.formatString(it) },
                            ).joinToString(" · ")
                            Text("• $name" + if (extra.isNotBlank()) "  ($extra)" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Issues(c: Ctx, reload: Int) {
    var replying by remember { mutableStateOf<dev.nerdytech.jellyglance.data.SeerrIssue?>(null) }
    var showResolved by remember { mutableStateOf(false) }
    Loaded(reload, { c.vm.parity.issues() }) { list ->
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${list.count { it.open }} open · ${list.count { !it.open }} resolved", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { showResolved = !showResolved }) { Text(if (showResolved) "Hide resolved" else "Show resolved") }
            }
        }
        val shown = list.filter { showResolved || it.open }
        if (shown.isEmpty()) empty(if (list.isEmpty()) "No issues reported" else "No open issues")
        items(shown.size) { i ->
            val it = shown[i]
            val where = listOfNotNull(it.type.ifBlank { null }, if (it.season > 0) "S${it.season}" + (if (it.episode > 0) "E${it.episode}" else "") else null).joinToString(" · ")
            Row2(
                it.title, listOfNotNull(where.ifBlank { null }, "by ${it.createdBy.ifBlank { "someone" }}", it.createdAt?.let { d -> Fields.formatString(d) }).joinToString(" · ") +
                    (if (it.message.isNotBlank()) "\n“${it.message}”" else ""),
                trailing = { Pill(it.status, if (it.open) Warn else Good) },
            ) {
                if (it.comments.size > 1) Column(Modifier.padding(top = 6.dp)) {
                    it.comments.drop(1).takeLast(3).forEach { (who, msg) ->
                        Text("${who.ifBlank { "Reply" }}: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (it.open) FilledTonalButton(onClick = { c.act("Resolved ${it.title}") { c.vm.parity.issueAction(it, "resolve") } }) { Text("Resolve") }
                    else OutlinedButton(onClick = { c.act("Reopened ${it.title}") { c.vm.parity.issueAction(it, "reopen") } }) { Text("Reopen") }
                    OutlinedButton(onClick = { replying = it }) { Text("Reply") }
                    TextButton(onClick = {
                        c.confirm(Confirm("Delete this issue?", "It's removed from ${it.source} for everyone.", "Delete") {
                            c.act("Deleted") { c.vm.parity.issueAction(it, "delete") }
                        })
                    }) { Text("Delete", color = Alert) }
                }
            }
        }
    }
    replying?.let { i ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { replying = null },
            title = { Text("Reply on ${i.title}") },
            text = { androidx.compose.material3.OutlinedTextField(text, { text = it }, minLines = 3, placeholder = { Text("Visible to ${i.createdBy.ifBlank { "the reporter" }} in ${i.source}") }) },
            confirmButton = { TextButton(onClick = { replying = null; c.act("Reply sent") { c.vm.parity.issueAction(i, "comment", text.trim()) } }, enabled = text.isNotBlank()) { Text("Send") } },
            dismissButton = { TextButton(onClick = { replying = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Jobs(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.parity.jellyfinJobs() }) { jobs ->
        jobs.groupBy { it.category.ifBlank { "Other" } }.forEach { (cat, list) ->
            item(key = "h-$cat") { Text(cat, style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 8.dp)) }
            items(list.size, key = { list[it].id }) { i ->
                val j = list[i]
                val running = j.state.equals("Running", true)
                Row2(
                    j.name,
                    listOfNotNull(
                        j.about.ifBlank { null },
                        listOfNotNull(
                            j.lastStatus.ifBlank { null }?.let { s -> "Last: $s" }, j.lastEnd?.let { Fields.formatString(it) },
                            j.everyHours?.let { h -> if (h >= 24) "every ${(h / 24).toInt()}d" else "every ${h.toInt()}h" },
                        ).joinToString(" · ").ifBlank { null },
                    ).joinToString("\n"),
                    trailing = {
                        if (running) Pill("Running", Good)
                        else FilledTonalButton(onClick = { c.act("${j.name} started") { c.vm.parity.runJellyfinJob(j.id) } }) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Run")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Logs(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.parity.logs() }) { logs ->
        if (logs.isEmpty()) empty("No task logs yet")
        items(logs.size, key = { logs[it].id }) { i ->
            val l = logs[i]
            var open by remember { mutableStateOf(false) }
            val failed = l.result.contains("fail", true) || l.lines.any { it.first.equals("red", true) }
            Row2(
                l.name, listOfNotNull(l.trigger.ifBlank { null }, l.ranAt?.let { Fields.formatString(it) }, "${l.seconds}s").joinToString(" · "),
                trailing = { Pill(if (failed) "Failed" else l.result.ifBlank { "Done" }, if (failed) Alert else Good) },
            ) {
                Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(top = 4.dp)) {
                    if (!open) Text("${l.lines.size} lines · tap to read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else l.lines.forEach { (color, msg) ->
                        Text(
                            msg, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            color = when (color.lowercase()) { "red", "crimson" -> Alert; "yellow", "orange" -> Warn; "lawngreen", "green" -> Good; else -> MaterialTheme.colorScheme.onSurface },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Keys(c: Ctx, reload: Int) {
    var creating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<String?>(null) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    Loaded(reload, { c.vm.parity.apiKeys() }) { keys ->
        item {
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Keys let apps and widgets read JellyGlance without signing in.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("New key") }
            }
        }
        items(keys.size, key = { keys[it].key }) { i ->
            val k = keys[i]
            Row2(
                k.name.ifBlank { "Unnamed key" },
                listOfNotNull("••••" + k.key.takeLast(4), k.scope.ifBlank { null }, "last used " + (k.lastUsed?.let { Fields.formatString(it) } ?: "never")).joinToString(" · "),
                trailing = {
                    TextButton(onClick = {
                        c.confirm(Confirm("Revoke ${k.name.ifBlank { "this key" }}?", "Anything using it (widgets, scripts, other apps) stops working.", "Revoke") {
                            c.act("Key revoked") { c.vm.parity.deleteApiKey(k.key) }
                        })
                    }) { Text("Revoke", color = Alert) }
                },
            )
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var scope by remember { mutableStateOf("widgets") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New API key") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Text("Access", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("widgets" to "Widgets only", "full" to "Full access").forEach { (v, l) ->
                            androidx.compose.material3.FilterChip(selected = scope == v, onClick = { scope = v }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    creating = false
                    c.scope.launch {
                        runCatching {
                            val before = c.vm.parity.apiKeys().map { it.key }.toSet()
                            c.vm.parity.createApiKey(name.trim(), scope).firstOrNull { it.key !in before }
                        }.onSuccess { k -> created = k?.key; c.act("Key created") {} }
                            .onFailure { c.act("") { throw it } }
                    }
                }, enabled = name.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
    created?.let { key ->
        AlertDialog(
            onDismissRequest = { created = null },
            title = { Text("Your new key") },
            text = { Text("Copy it now and keep it somewhere safe. Anyone with it can read your JellyGlance.") },
            confirmButton = { TextButton(onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(key)); created = null }) { Text("Copy key") } },
            dismissButton = { TextButton(onClick = { created = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun Libraries(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.parity.libraries() }) { libs ->
        item { Text("Untracked libraries are left out of stats, activity and the dashboard.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) }
        items(libs.size, key = { libs[it].id }) { i ->
            val l = libs[i]
            Row2(l.name, when (l.type.lowercase()) {
                "tvshows" -> "TV shows"; "movies" -> "Movies"; "music" -> "Music"; "books" -> "Books"; "homevideos" -> "Home videos"
                "boxsets" -> "Collections"; "musicvideos" -> "Music videos"; "playlists" -> "Playlists"; else -> l.type.replaceFirstChar { it.uppercase() }
            }, trailing = {
                Switch(l.tracked, { c.act(if (l.tracked) "${l.name} is no longer tracked" else "Tracking ${l.name}") { c.vm.parity.toggleLibrary(l.id) } })
            })
        }
    }
}

@Composable
private fun Monitor(c: Ctx, reload: Int) {
    Loaded(reload, { c.vm.parity.monitor() }) { (active, idle) ->
        item {
            var a by remember(active) { mutableStateOf(active.toString()) }
            var b by remember(idle) { mutableStateOf(idle.toString()) }
            Row2("Polling intervals", "How often JellyGlance asks Jellyfin for sessions, in milliseconds.") {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(a, { a = it.filter(Char::isDigit) }, label = { Text("While playing") }, singleLine = true, modifier = Modifier.weight(1f))
                    androidx.compose.material3.OutlinedTextField(b, { b = it.filter(Char::isDigit) }, label = { Text("When idle") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                FilledTonalButton(
                    onClick = { c.act("Saved") { c.vm.parity.setMonitor(a.toInt(), b.toInt()) } },
                    enabled = (a.toIntOrNull() ?: 0) >= 1000 && (b.toIntOrNull() ?: 0) >= 1000, modifier = Modifier.padding(top = 10.dp),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun WebOnly(c: Ctx) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val base = c.vm.settings.value.baseUrl
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "These are big editors (templates, page builders, sign-in methods) that work best on a bigger screen. They open in JellyGlance on the web.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            )
        }
        listOf(
            "security" to "Security & sign-in", "kiosk" to "Kiosk", "status-page" to "Status page", "newsletter" to "Newsletter",
            "notifications" to "Notifications & alert rules", "imports" to "Imports", "general" to "General",
        ).forEach { (path, label) ->
            item(key = path) {
                Row2(label, "Opens $base/settings/$path".removePrefix("Opens https://").let { "Opens $it" }, trailing = {
                    TextButton(onClick = { dev.nerdytech.jellyglance.ui.Links.browser(ctx, "$base/settings/$path") }) { Text("Open") }
                })
            }
        }
    }
}
