package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Category
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.PollEffect
import dev.nerdytech.jellyglance.ui.components.ErrorBanner
import dev.nerdytech.jellyglance.ui.components.Poster
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The Arr calendar: everything coming up grouped by day, and past releases still missing a file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(vm: AppViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val widgetRoute = Catalogue.get("calendar")!!.route
    val watched = listOf(widgetRoute, Catalogue.FULL_CALENDAR)
    val full = routes[Catalogue.FULL_CALENDAR]?.data?.let(Models::fullCalendar)
    val all = full ?: routes[widgetRoute]?.data?.let(Models::calendar)?.items ?: emptyList()
    val (upcoming, missed) = Models.splitReleases(all)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val base = settings.baseUrl
    val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")

    PollEffect(watched, maxOf(settings.refreshSeconds, 120)) { vm.refresh(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upcoming releases") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; vm.refresh(watched); refreshing = false } },
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            val rows = if (tab == 0) upcoming else missed
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize(),
            ) {
                val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                routes[widgetRoute]?.error?.let { e -> item { ErrorBanner(e, col) } }
                item {
                    SingleChoiceSegmentedButtonRow(col.padding(vertical = 8.dp)) {
                        listOf("Coming up ${upcoming.size}", "Missing ${missed.size}").forEachIndexed { i, label ->
                            SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                        }
                    }
                }
                if (full == null) item {
                    Text(
                        "Showing the few releases the calendar widget includes. A full-access key shows the whole calendar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(bottom = 8.dp),
                    )
                }
                if (rows.isEmpty()) item {
                    Text(if (tab == 0) "Nothing scheduled" else "Nothing missing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = col.padding(top = 24.dp))
                }
                rows.groupBy { it.instant!!.atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (date, dayRows) ->
                    item(key = "d-$tab-$date") {
                        val rel = dayLabel(dayRows.first().date)
                        val label = date.format(dateFmt)
                        Row(col.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (rel in setOf("Today", "Tomorrow", "Yesterday")) "$rel · $label" else label,
                                style = MaterialTheme.typography.labelLarge, color = Color(Category.CALENDAR.color), modifier = Modifier.weight(1f),
                            )
                            Text("${dayRows.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(dayRows) { r ->
                        Row(col.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Poster(Models.absolute(base, r.posterUrl), 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(r.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.subtitle.isNotBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(r.service, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (tab == 0 && r.instant != null) {
                                val ctx = androidx.compose.ui.platform.LocalContext.current
                                androidx.compose.material3.IconButton(onClick = { addToCalendar(ctx, r) }) {
                                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.EventAvailable, "Add to calendar", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Opens the phone's calendar app with the release pre-filled (no calendar permission needed). */
private fun addToCalendar(ctx: android.content.Context, r: Models.Release) {
    val start = r.instant?.toEpochMilli() ?: return
    // Release dates from the *arrs are usually whole days.
    val allDay = r.date?.length == 10 || r.date?.endsWith("T00:00:00Z") == true
    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, listOf(r.title, r.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
        .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, listOf(r.overview, "From ${r.service} via JellyGlance").filter { it.isNotBlank() }.joinToString("\n\n"))
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, start + if (allDay) 86_400_000L else 3_600_000L)
        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
    runCatching { ctx.startActivity(intent) }.onFailure {
        android.widget.Toast.makeText(ctx, "No calendar app found", android.widget.Toast.LENGTH_SHORT).show()
    }
}
