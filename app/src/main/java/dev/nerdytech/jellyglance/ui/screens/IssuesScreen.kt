package dev.nerdytech.jellyglance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Issue
import dev.nerdytech.jellyglance.data.Issues
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.Pill
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.Warn
import kotlinx.coroutines.launch
import java.time.Instant

/** What needs attention, with the details JellyGlance records and what can be done about each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val health = Catalogue.get("health")!!.route
    val watched = listOf(health, Catalogue.get("downloads")!!.route, Catalogue.WEBHOOK_HISTORY)
    val all = vm.issues(routes)
    val visible = Issues.visible(all, settings.dismissed)
    val hidden = all.filter { it !in visible }
    val fullLog = routes[Catalogue.WEBHOOK_HISTORY]?.data != null
    var showHidden by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    PollEffect(watched, settings.refreshSeconds) { vm.refresh(it) }

    fun openWeb(path: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.baseUrl + path))) }

    fun retry(issue: Issue) {
        val id = issue.webhookId ?: return
        busy = issue.key
        scope.launch {
            vm.retryWebhook(id)
                .onSuccess { ok -> snackbar.showSnackbar(if (ok) "Test delivery sent. The webhook is working again." else "Still failing. Check the error below.") }
                .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Couldn't send a test delivery") }
            busy = null
        }
    }

    fun dismiss(issue: Issue) {
        vm.dismiss(issue)
        scope.launch {
            val r = snackbar.showSnackbar("Dismissed. It comes back if it happens again.", actionLabel = "Undo", withDismissAction = true)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restore(issue.key)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Needs attention") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { openWeb("/settings/health") }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in JellyGlance") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(watched); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                routes[health]?.error?.let { e -> item { ErrorBanner(e, col) } }
                if (visible.isEmpty() && routes[health]?.data != null) item {
                    Column(col.padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Good, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("All clear", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (hidden.isEmpty()) "Nothing needs attention." else "${hidden.size} dismissed.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(visible, key = { it.key }) { issue ->
                    IssueCard(
                        issue, col, busy = busy == issue.key,
                        onRetry = issue.webhookId?.let { { retry(issue) } },
                        onPause = issue.downloadId?.let { id ->
                            {
                                scope.launch {
                                    vm.pauseDownload(id, !issue.downloadPaused)
                                        .onSuccess { snackbar.showSnackbar(if (issue.downloadPaused) "Resumed" else "Paused") }
                                        .onFailure { e -> snackbar.showSnackbar((e as? ApiException)?.kind?.message ?: "Failed") }
                                }
                            }
                        },
                        onDismiss = { dismiss(issue) },
                        onOpen = {
                            openWeb(when (issue.kind) { Issue.Kind.WEBHOOK -> "/settings/webhooks"; Issue.Kind.DOWNLOAD -> "/downloads"; else -> "/settings/health" })
                        },
                    )
                }
                if (!fullLog && all.any { it.kind == Issue.Kind.WEBHOOK }) item {
                    Text(
                        "Error details and test deliveries need a full-access API key.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col,
                    )
                }
                if (hidden.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showHidden = !showHidden }, modifier = col) {
                            Text(if (showHidden) "Hide dismissed" else "Show ${hidden.size} dismissed")
                        }
                    }
                    if (showHidden) items(hidden, key = { "h-" + it.key }) { issue ->
                        Row(col.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(issue.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { vm.restore(issue.key) }) { Text("Restore") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueCard(
    issue: Issue, modifier: Modifier, busy: Boolean,
    onRetry: (() -> Unit)?, onPause: (() -> Unit)?, onDismiss: () -> Unit, onOpen: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val icon: ImageVector = when (issue.kind) {
        Issue.Kind.WEBHOOK -> Icons.Rounded.Webhook
        Issue.Kind.DOWNLOAD -> Icons.Rounded.Download
        else -> Icons.Rounded.ErrorOutline
    }
    val tone = if (issue.kind == Issue.Kind.WEBHOOK || issue.kind == Issue.Kind.AUDIT) Alert else Warn
    Card(
        onClick = { expanded = !expanded }, modifier = modifier, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = tone, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(issue.title, style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Pill(issue.kind.label, tone)
                        if (issue.count > 1) Text("×${issue.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (issue.latestMs > 0) Text("last " + Fields.relative(Instant.ofEpochMilli(issue.latestMs)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (issue.summary.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    issue.summary, style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (issue.kind == Issue.Kind.WEBHOOK && issue.webhookId != null) FontFamily.Monospace else null,
                    maxLines = if (expanded) 12 else 2, overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    issue.details.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                            Text(v, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                    if (issue.occurrences.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Recent failures", style = MaterialTheme.typography.labelLarge)
                        issue.occurrences.forEach { o ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(o.at?.let { Fields.formatString(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                                Text(o.text.ifBlank { "Failed" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onRetry != null) {
                    FilledTonalButton(onClick = onRetry, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send test")
                    }
                }
                if (onPause != null) {
                    FilledTonalButton(onClick = onPause) {
                        Icon(if (issue.downloadPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (issue.downloadPaused) "Resume" else "Pause")
                    }
                }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in JellyGlance", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
