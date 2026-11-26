package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.AppHealth
import dev.nerdytech.jellyglance.data.Dashboard
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.LastPlay
import dev.nerdytech.jellyglance.data.Milestone
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.PeakHours
import dev.nerdytech.jellyglance.data.Runway
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.MiniProgress
import dev.nerdytech.jellyglance.ui.components.Poster
import dev.nerdytech.jellyglance.ui.theme.Alert
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.LocalBrand
import dev.nerdytech.jellyglance.ui.theme.Warn

private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private fun hourLabel(h: Int) = when (h) { 0 -> "12am"; 12 -> "12pm"; in 1..11 -> "${h}am"; else -> "${h - 12}pm" }

@Composable
private fun InsightCard(title: String, modifier: Modifier, trailing: String? = null, content: @Composable () -> Unit) {
    Card(modifier, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun LastPlaybackLine(last: LastPlay?) {
    last ?: return
    val what = if (last.series.isNotBlank() && last.series != last.title) "${last.series} · ${last.title}" else last.title
    Text(
        "Last stream ${last.endedAt?.let { Fields.formatString(it) } ?: ""}: ${last.user} watched $what on ${last.device.ifBlank { last.client }}.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Plays by weekday and hour, like the web dashboard's heatmap. */
@Composable
fun PeakHoursCard(p: PeakHours, modifier: Modifier) {
    val brand = LocalBrand.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    InsightCard("Peak hours", modifier, "Last ${p.days} days · ${Fields.fmtNum(p.total.toDouble())} plays") {
        Row {
            Column(Modifier.width(30.dp).padding(top = 2.dp), verticalArrangement = Arrangement.SpaceBetween) {
                DAYS.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(16.dp)) }
            }
            Canvas(Modifier.weight(1f).height((7 * 16).dp)) {
                val cw = size.width / 24f
                val ch = size.height / 7f
                p.grid.forEachIndexed { d, row ->
                    row.forEachIndexed { h, v ->
                        val a = if (v == 0) 0f else 0.18f + 0.82f * (v.toFloat() / p.max)
                        drawRoundRect(
                            color = if (v == 0) empty else brand.primary.copy(alpha = a),
                            topLeft = Offset(h * cw + 1f, d * ch + 1f), size = Size(cw - 2f, ch - 2f), cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                }
                p.busiest?.let { (d, h, _) ->
                    drawRoundRect(Color.White, Offset(h * cw + 1f, d * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3f, 3f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp)) {
            listOf(0, 6, 12, 18).forEach { h -> Text(hourLabel(h), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
        p.busiest?.let { (d, h, n) -> Text("Busiest ${DAYS[d]} ${hourLabel(h)} · $n plays", style = MaterialTheme.typography.bodySmall) }
        p.quietest?.let { (d, h, _) -> Text("Quietest ${DAYS[d]} ${hourLabel(h)} · good for maintenance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun RunwayCard(r: Runway, modifier: Modifier) {
    val used = if (r.totalBytes > 0) ((r.totalBytes - r.freeBytes) / r.totalBytes).toFloat() else 0f
    val headline = when {
        r.beyondYear -> "Over a year of space left"
        r.daysUntilFull != null -> "About ${r.daysUntilFull} days of space left"
        else -> "Not enough history to forecast yet"
    }
    InsightCard("Disk runway", modifier) {
        Text(headline, style = MaterialTheme.typography.titleSmall, color = if (r.daysUntilFull != null && r.daysUntilFull < 60 && !r.beyondYear) Warn else MaterialTheme.colorScheme.onSurface)
        Text(
            listOfNotNull(
                r.range.takeIf { it.size == 2 }?.let { "Range ${it[0]}–${it[1]} days" },
                r.bytesPerDay.takeIf { it > 0 }?.let { "${Fields.fmtBytes(it)} a day" },
                r.confidence.takeIf { it.isNotBlank() }?.let { "$it confidence" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        MiniProgress(used, if (used > 0.9f) Alert else if (used > 0.75f) Warn else LocalBrand.current.primary, Modifier.height(8.dp))
        Text("${Fields.fmtBytes(r.freeBytes)} free of ${Fields.fmtBytes(r.totalBytes)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        if (r.libraries.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            val max = r.libraries.maxOf { it.second }.coerceAtLeast(1.0)
            r.libraries.take(6).forEach { (name, bytes) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((bytes / max).toFloat()).background(LocalBrand.current.secondary, RoundedCornerShape(4.dp)))
                    }
                    Text(Fields.fmtBytes(bytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp).padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
fun HallOfFameCard(vm: AppViewModel, d: Dashboard, modifier: Modifier) {
    if (d.hallOfFame.isEmpty()) return
    val medals = listOf(Color(0xFFF5C542), Color(0xFFC9D1DB), Color(0xFFD08B4A))
    InsightCard("Hall of fame", modifier, "${Fields.fmtNum(d.totalPlays.toDouble())} plays · ${Fields.fmtNum(Math.floor(d.watchSeconds / 3600))} h") {
        d.hallOfFame.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${i + 1}", style = MaterialTheme.typography.labelLarge, color = medals.getOrNull(i) ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
                UserAvatar(vm, f.userId, f.name, 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(f.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${Fields.fmtNum(f.plays.toDouble())} plays", style = MaterialTheme.typography.labelLarge)
                    Text(Fields.fmtDuration(f.watchSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WeekCard(d: Dashboard, modifier: Modifier) {
    val p = d.pulse ?: return
    InsightCard("This week", modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact("Top title", p.topItem.ifBlank { "–" }, "${p.topPlays} plays · ${Fields.fmtDuration(p.topSeconds)}", Modifier.weight(1f))
            Fact("Most active", p.mostActive.ifBlank { "–" }, "${p.mostActivePlays} plays", Modifier.weight(1f))
        }
        if (p.quietUsers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Quiet this week: ${p.quietUsers.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val t = d.today; val w = d.lastWeek
        if (t != null && w != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Delta("Plays today", t.plays.toString(), t.plays - w.plays, Modifier.weight(1f))
                Delta("Hours today", Fields.fmtDuration(t.seconds), ((t.seconds - w.seconds) / 3600).toInt(), Modifier.weight(1f), "h")
                Delta("Active today", t.users.toString(), t.users - w.users, Modifier.weight(1f))
            }
            Text("Compared with the same day last week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Fact(label: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Delta(label: String, value: String, delta: Int, modifier: Modifier, unit: String = "") {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (if (delta > 0) "+" else "") + "$delta$unit", style = MaterialTheme.typography.labelSmall,
            color = when { delta > 0 -> Good; delta < 0 -> Warn; else -> MaterialTheme.colorScheme.onSurfaceVariant },
        )
    }
}

@Composable
fun MilestonesCard(list: List<Milestone>, modifier: Modifier) {
    InsightCard("Milestones", modifier, "${list.count { it.unlocked }} of ${list.size} unlocked") {
        list.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (m.unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null, tint = if (m.unlocked) Warn else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(m.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(m.value, style = MaterialTheme.typography.labelLarge)
                    }
                    MiniProgress(m.progress, LocalBrand.current.primary, Modifier.padding(vertical = 3.dp))
                    Text(m.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WatchTonightRail(d: Dashboard, baseUrl: String, openItem: (String, String) -> Unit, modifier: Modifier) {
    if (d.watchTonight.isEmpty()) return
    Column(modifier) {
        Text("Watch tonight", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
        Text("In progress around the house", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(d.watchTonight) { t ->
                Column(Modifier.width(104.dp).clickable { openItem(t.itemId, t.name) }) {
                    Poster(Models.posterUrl(baseUrl, t.imageId, 240), 104.dp)
                    if (t.progress in 1..99) MiniProgress(t.progress / 100f, LocalBrand.current.primary, Modifier.padding(top = 4.dp))
                    Text(t.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text(t.users.joinToString(", ").ifBlank { t.reasons.firstOrNull().orEmpty() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun QuickActionsCard(modifier: Modifier, onSync: () -> Unit, onTest: () -> Unit, onBackup: () -> Unit, onRequests: () -> Unit) {
    InsightCard("Quick actions", modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quick(Icons.Rounded.Sync, "Sync Jellyfin", Modifier.weight(1f), onSync)
            Quick(Icons.Rounded.Hub, "Test integrations", Modifier.weight(1f), onTest)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quick(Icons.Rounded.Backup, "Run backup", Modifier.weight(1f), onBackup)
            Quick(Icons.Rounded.Inbox, "Open requests", Modifier.weight(1f), onRequests)
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppHealthCard(apps: List<AppHealth>, modifier: Modifier) {
    if (apps.isEmpty()) return
    val failing = apps.count { !it.ok }
    InsightCard("Connected apps", modifier, if (failing == 0) "All ${apps.size} healthy" else "$failing of ${apps.size} failing") {
        apps.sortedBy { it.ok }.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (a.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, tint = if (a.ok) Good else Alert, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(a.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(if (a.ok) a.version else a.message, style = MaterialTheme.typography.labelSmall, color = if (a.ok) MaterialTheme.colorScheme.onSurfaceVariant else Alert, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthInMax())
            }
        }
    }
}

private fun Modifier.widthInMax() = this.then(Modifier.width(150.dp))

