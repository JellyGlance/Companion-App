package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Level
import dev.nerdytech.jellyglance.data.WidgetDef
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.IconChip
import dev.nerdytech.jellyglance.ui.components.SectionHeader
import dev.nerdytech.jellyglance.ui.components.Sparkline
import dev.nerdytech.jellyglance.ui.theme.levelColor
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, def: WidgetDef, onBack: () -> Unit, onStreams: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val state = routes[def.route]
    val flat = state?.flat
    val pinned = settings.pinned[def.id]
    val head = flat?.let { Fields.headline(it, def, pinned) }
    val level = if (flat != null && head != null) Fields.overallLevel(flat, def, head) else Level.OK
    val accent = levelColor(level, Color(def.category.color))
    var raw by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val custom = Catalogue.get(def.id) == null

    PollEffect(listOf(def.route), settings.refreshSeconds) { vm.refresh(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(def.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { raw = !raw }) { Icon(Icons.Rounded.Code, if (raw) "Show fields" else "Show raw JSON") }
                    IconButton(onClick = { vm.refreshAsync(listOf(def.route)) }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    if (!custom) {
                        IconButton(onClick = { vm.toggleDashboard(def.id) }) {
                            if (def.id in settings.dashboard) Icon(Icons.Rounded.CheckCircle, "Remove from dashboard", tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Rounded.AddCircleOutline, "Add to dashboard")
                        }
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(listOf(def.route)); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.14f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconChip(def, accent, 36)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(def.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(def.route, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(head?.value ?: "…", style = MaterialTheme.typography.displaySmall, color = if (level == Level.OK) MaterialTheme.colorScheme.onSurface else accent)
                            Text(head?.caption ?: "Loading", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Sparkline(head?.path?.let { state.history[it] } ?: emptyList(), accent, Modifier.fillMaxWidth().height(40.dp))
                            if (state != null && state.updatedAt > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text("Updated " + Fields.relative(Instant.ofEpochMilli(state.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (def.route == Catalogue.NOW_PLAYING) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onStreams, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PlayCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Manage streams")
                        }
                    }
                    state?.error?.let { Spacer(Modifier.height(12.dp)); ErrorBanner(it) }
                }

                if (raw) {
                    item {
                        SectionHeader("Raw JSON", Modifier.padding(top = 16.dp))
                        Text(
                            pretty(state?.data),
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .padding(vertical = 4.dp).horizontalScroll(rememberScrollState()),
                        )
                    }
                } else if (flat != null) {
                    if (flat.leaves.isNotEmpty()) {
                        item { SectionHeader("Fields · tap the pin to show one on the card", Modifier.padding(top = 16.dp)) }
                        items(flat.leaves, key = { "l-" + it.path }) { leaf ->
                            val isPinned = pinned == leaf.path
                            val lv = Fields.levelOf(leaf, def)
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.pin(def.id, if (isPinned) null else leaf.path) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Fields.humanize(leaf.key), style = MaterialTheme.typography.bodyMedium)
                                    if (leaf.path.contains('.')) Text(leaf.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    Fields.format(leaf), style = MaterialTheme.typography.titleSmall,
                                    color = levelColor(lv, MaterialTheme.colorScheme.onSurface), maxLines = 1,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (isPinned) "Unpin" else "Pin",
                                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    flat.lists.forEach { list ->
                        item(key = "h-" + list.path) {
                            SectionHeader("${Fields.humanize(list.key).ifBlank { def.noun }} · ${list.items.size}", Modifier.padding(top = 20.dp)) {
                                val pinnedList = pinned == list.path
                                IconButton(onClick = { vm.pin(def.id, if (pinnedList) null else list.path) }, modifier = Modifier.size(28.dp)) {
                                    Icon(if (pinnedList) Icons.Rounded.PushPin else Icons.Outlined.PushPin, "Pin count", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        if (list.items.isEmpty()) {
                            item(key = "e-" + list.path) { Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(list.items.take(100).withIndex().toList(), key = { "i-${list.path}-${it.index}" }) { (_, item) ->
                            ListItemRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListItemRow(item: Any?) {
    var open by remember { mutableStateOf(false) }
    val title = Fields.itemTitle(item).ifBlank { pretty(item).take(80) }
    val sub = Fields.itemSubtitle(item)
    val progress = Fields.itemProgress(item)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
            .clickable(enabled = item is Map<*, *>) { open = !open }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {})
        }
        if (open) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Fields.itemDetails(item).forEach { (k, v) ->
                    Row {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun pretty(v: Any?): String = when (v) {
    null -> "null"
    is Map<*, *> -> JSONObject(v).toString(2)
    is List<*> -> JSONArray(v).toString(2)
    else -> v.toString()
}
