package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.RouteState
import dev.nerdytech.jellyglance.data.WidgetDef
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.IconChip
import dev.nerdytech.jellyglance.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: AppViewModel, openWidget: (String) -> Unit, openRoute: (String) -> Unit, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("/api/widgets/") }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Load every widget once so the list can preview live values.
    LaunchedEffect(Unit) { vm.refresh(Catalogue.all.map { it.route }.filter { routes[it] == null }) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Widgets") }, scrollBehavior = scroll,
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    listOf("All widgets", "Pinned").forEachIndexed { i, label ->
                        SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                    }
                }
            }
            if (tab == 0) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Search widgets") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    )
                }
                val filtered = Catalogue.all.filter {
                    query.isBlank() || it.name.contains(query, true) || it.tip.contains(query, true) || it.category.label.contains(query, true)
                }
                filtered.groupBy { it.category }.forEach { (cat, defs) ->
                    item(key = "h-${cat.name}") { SectionHeader(cat.label, Modifier.padding(top = 12.dp)) }
                    items(defs, key = { it.id }) { def ->
                        WidgetRow(def, routes[def.route], settings.pinned[def.id], onOpen = { openWidget(def.id) }) {
                            IconButton(onClick = { vm.toggleDashboard(def.id) }) {
                                if (def.id in settings.dashboard) Icon(Icons.Rounded.CheckCircle, "Remove from dashboard", tint = MaterialTheme.colorScheme.primary)
                                else Icon(Icons.Rounded.AddCircleOutline, "Add to dashboard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    SectionHeader("Custom route", Modifier.padding(top = 16.dp))
                    Text(
                        "Open any JellyGlance widget route, for example /api/widgets/health.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = custom, onValueChange = { custom = it }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (custom.length > 1) openRoute(custom.trim()) }),
                        trailingIcon = {
                            IconButton(onClick = { if (custom.length > 1) openRoute(custom.trim()) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open") }
                        },
                    )
                }
            } else {
                item {
                    Text(
                        "Pinned widgets appear at the bottom of Home. Reorder them here; pin more from All widgets.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                val mine = settings.dashboard.mapNotNull { Catalogue.get(it) }
                items(mine, key = { "d-" + it.id }) { def ->
                    val i = settings.dashboard.indexOf(def.id)
                    WidgetRow(def, routes[def.route], settings.pinned[def.id], onOpen = { openWidget(def.id) }) {
                        IconButton(onClick = { vm.moveDashboard(def.id, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { vm.moveDashboard(def.id, 1) }, enabled = i < settings.dashboard.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetRow(
    def: WidgetDef, state: RouteState?, pinned: String?,
    onOpen: () -> Unit, actions: @Composable () -> Unit,
) {
    val head = state?.flat?.let { Fields.headline(it, def, pinned) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(def, size = 40)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                if (head != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(head.value, style = MaterialTheme.typography.labelLarge, color = Color(def.category.color), maxLines = 1)
                } else if (state?.error != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(state.error.kind.short, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
            Text(def.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
