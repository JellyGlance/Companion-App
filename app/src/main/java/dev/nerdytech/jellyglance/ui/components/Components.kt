package dev.nerdytech.jellyglance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Level
import dev.nerdytech.jellyglance.data.RouteState
import dev.nerdytech.jellyglance.data.WidgetDef
import dev.nerdytech.jellyglance.ui.theme.levelColor

fun iconFor(name: String): ImageVector = when (name) {
    "play" -> Icons.Rounded.PlayArrow
    "chart" -> Icons.Rounded.BarChart
    "users" -> Icons.Rounded.Group
    "clock" -> Icons.Rounded.Schedule
    "list" -> Icons.AutoMirrored.Rounded.List
    "monitor" -> Icons.Rounded.Tv
    "film" -> Icons.Rounded.Movie
    "storage" -> Icons.Rounded.Storage
    "download" -> Icons.Rounded.Download
    "plus" -> Icons.Rounded.Add
    "err" -> Icons.Rounded.ErrorOutline
    "calendar" -> Icons.Rounded.CalendarMonth
    "gear" -> Icons.Rounded.Settings
    "trash" -> Icons.Rounded.DeleteSweep
    "health" -> Icons.Rounded.Favorite
    "stop" -> Icons.Rounded.Stop
    else -> Icons.Rounded.Visibility
}

@Composable
fun IconChip(def: WidgetDef, tint: Color = Color(def.category.color), size: Int = 32) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(def.icon), contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.6).dp))
    }
}

/** Dashboard tile: icon, name, the headline value and a trend line. */
@Composable
fun WidgetCard(def: WidgetDef, state: RouteState?, pinned: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val flat = state?.flat
    val head = flat?.let { Fields.headline(it, def, pinned) }
    val level = if (flat != null && head != null) Fields.overallLevel(flat, def, head) else Level.OK
    val accent by animateColorAsState(levelColor(level, Color(def.category.color)), label = "accent")
    val history = (head?.path?.let { state.history[it] } ?: emptyList()).takeIf { h -> h.distinct().size >= 3 }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent)),
                ),
            )
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(def, accent, 28)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        def.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    if (state?.error != null && flat != null) StatusDot(MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                when {
                    head != null -> {
                        Text(
                            head.value, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (level == Level.OK) MaterialTheme.colorScheme.onSurface else accent,
                        )
                        Text(head.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    state?.error != null -> {
                        Text(state.error.kind.short, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                        Text(state.error.kind.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    else -> {
                        Text("…", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Loading", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (history != null) {
                    Spacer(Modifier.height(8.dp))
                    Sparkline(history, accent, Modifier.fillMaxWidth().height(22.dp))
                }
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), strokeWidth = 2f, cap = StrokeCap.Round)
            return@Canvas
        }
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = (size.height - 2) - ((v - min) / range * (size.height - 4)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun ErrorBanner(error: ApiException, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Column {
            Text(error.kind.short, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                error.kind.message + if (error.detail.isNotBlank() && error.detail != error.kind.message) " (${error.detail})" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
