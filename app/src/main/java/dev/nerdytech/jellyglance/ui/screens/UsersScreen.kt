package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.HistoryRow
import dev.nerdytech.jellyglance.data.UserRow
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.Pill
import kotlinx.coroutines.launch

@Composable
fun UserAvatar(vm: AppViewModel, id: String, name: String, size: Dp) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        AsyncImage(vm.library.userImage(settings.baseUrl, id), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/** Every Jellyfin user with their plays, watch time and when they were last around. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(vm: AppViewModel, openUser: (UserRow) -> Unit, onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<UserRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { vm.library.users() }.onSuccess { users = it }
            .onFailure { error = (it as? ApiException)?.let { e -> e.detail.ifBlank { e.kind.message } } ?: it.message }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Users") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = col) } }
            if (users == null && error == null) item { dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(32.dp)) }
            items(users ?: emptyList(), key = { it.id }) { u ->
                Card(
                    onClick = { openUser(u) }, modifier = col, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(vm, u.id, u.name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(u.name, style = MaterialTheme.typography.titleSmall)
                                if (u.admin) { Spacer(Modifier.width(8.dp)); Pill("Admin", MaterialTheme.colorScheme.primary) }
                            }
                            Text(
                                "${Fields.fmtNum(u.plays.toDouble())} plays · ${Fields.fmtDuration(u.watchSeconds)} watched",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (u.lastWatched.isNotBlank()) Text(
                                "Last: ${u.lastWatched}" + if (u.lastClient.isNotBlank()) " · ${u.lastClient}" else "",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        u.lastSeen?.let { Text(Fields.formatString(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/** One user's totals and their full play history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(vm: AppViewModel, user: UserRow, onBack: () -> Unit) {
    val rows = remember { mutableStateListOf<HistoryRow>() }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun more() {
        if (loading || page >= pages) return
        loading = true
        scope.launch {
            runCatching { vm.library.userHistory(user.id, page + 1) }.onSuccess { r -> page += 1; pages = r.pages; rows.addAll(r.items) }.onFailure { pages = 0 }
            loading = false
        }
    }
    LaunchedEffect(user.id) { more() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(user.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
        ) {
            val col = Modifier.widthIn(max = 640.dp).fillMaxWidth()
            item {
                Column(col, horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(vm, user.id, user.name, 88.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(user.name, style = MaterialTheme.typography.headlineSmall)
                    if (user.admin) Pill("Admin", MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fields.fmtNum(user.plays.toDouble()), style = MaterialTheme.typography.titleLarge)
                            Text("Plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fields.fmtDuration(user.watchSeconds), style = MaterialTheme.typography.titleLarge)
                            Text("Watched", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(user.lastSeen?.let { Fields.formatString(it) } ?: "–", style = MaterialTheme.typography.titleLarge)
                            Text("Last seen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Watch history", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                }
            }
            items(rows.size) { i -> Box(col) { HistoryLine(rows[i], showTitle = true, showUser = false) } }
            item {
                when {
                    loading -> dev.nerdytech.jellyglance.ui.components.JellyLoading(Modifier.padding(16.dp))
                    page < pages -> TextButton(onClick = ::more) { Text("Load more") }
                    rows.isEmpty() -> Text("No plays recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
