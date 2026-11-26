package dev.nerdytech.jellyglance.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.nerdytech.jellyglance.ui.theme.ThemePresets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.BuildConfig
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.ThemeMode
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.components.SectionHeader
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.work.SyncWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel, onBrowse: () -> Unit, onSignIn: () -> Unit, onAdmin: () -> Unit = {},
    page: String? = null, openPage: (String) -> Unit = {}, onBack: (() -> Unit)? = null,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var url by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var key by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var interval by remember(settings.refreshSeconds) { mutableFloatStateOf(settings.refreshSeconds.toFloat()) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var pendingToggle by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) when (pendingToggle) {
            "alerts" -> vm.updateSettings { it.copy(alerts = true) }
            "streams" -> vm.updateSettings { it.copy(streamAlerts = true) }
            "requests" -> vm.updateSettings { it.copy(requestAlerts = true) }
        }
        pendingToggle = null
    }

    fun enableNotifications(which: String, on: Boolean) {
        val needs = on && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pendingToggle = which
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        vm.updateSettings {
            when (which) { "alerts" -> it.copy(alerts = on); "requests" -> it.copy(requestAlerts = on); else -> it.copy(streamAlerts = on) }
        }
        if (on) SyncWorker.runNow(ctx)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(PAGES.firstOrNull { it.key == page }?.title ?: "Settings") }, scrollBehavior = scroll,
                navigationIcon = { onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } } },
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (page == null) {
                item { ProfileCard(vm, settings, onSignIn) { openPage("account") } }
                val admin = settings.signedIn && settings.sessionRole in setOf("Owner", "Admin")
                item { SectionHeader("App", Modifier.padding(top = 16.dp)) }
                item {
                    Group {
                        PAGES.filter { it.key != "account" }.forEach { p -> PageRow(p.icon, p.title, p.summary(settings)) { openPage(p.key) } }
                    }
                }
                if (admin) {
                    item { SectionHeader("Server", Modifier.padding(top = 12.dp)) }
                    item {
                        Group {
                            PageRow(Icons.Rounded.AdminPanelSettings, "Server admin", "Tasks, downloads, webhooks, backups, invites") { onAdmin() }
                            PageRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open the web dashboard", settings.baseUrl.removePrefix("https://").removePrefix("http://")) {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.baseUrl + "/"))) }
                            }
                        }
                    }
                }
                item { SectionHeader("JellyGlance", Modifier.padding(top = 12.dp)) }
                item { ProjectCard(vm, Modifier.fillMaxWidth().padding(vertical = 6.dp), compact = true) }
                item { CommunityFooter(Modifier.fillMaxWidth()) }
            }

            if (page == "account") item {
                SectionHeader("Account")
                Group {
                    when {
                        settings.signedIn -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                dev.nerdytech.jellyglance.ui.components.Avatar(settings.sessionUser, 40.dp, dev.nerdytech.jellyglance.data.Models.userImageUrl(settings.baseUrl, settings.sessionUserId))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(settings.sessionUser.ifBlank { "Signed in" }, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        listOfNotNull(
                                            settings.sessionRole.takeIf { it.isNotBlank() },
                                            settings.sessionExp.takeIf { it > 0 }?.let { "session ends " + dev.nerdytech.jellyglance.data.Fields.relative(java.time.Instant.ofEpochSecond(it)) },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.signOutAccount() }) { Text("Sign out") }
                            }
                        }
                        else -> {
                            Text(
                                if (settings.sessionExpired) "Your JellyGlance session expired." else "Sign in with your JellyGlance account",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (settings.sessionExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Signing in unlocks everything tied to your user, like request titles. The API key below keeps the home-screen widget and alerts working.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onSignIn) { Text(if (settings.sessionExpired) "Sign in again" else "Sign in") }
                        }
                    }
                }
            }

            if (page == "connection") item {
                SectionHeader("Server", Modifier.padding(top = 12.dp))
                Group {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, label = { Text("JellyGlance URL") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = key, onValueChange = { key = it }, label = { Text("API key (optional)") }, singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle key") }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = !busy && (url != settings.baseUrl || key != settings.apiKey || result?.first != true),
                            onClick = {
                                busy = true; result = null
                                scope.launch {
                                    vm.testConnection(url, key, settings.insecure)
                                        .onSuccess { msg ->
                                            vm.updateSettings { it.copy(baseUrl = url, apiKey = key) }
                                            result = true to msg
                                        }
                                        .onFailure { e -> result = false to ((e as? ApiException)?.kind?.message ?: e.message.orEmpty()) }
                                    busy = false
                                }
                            },
                        ) { Text(if (url != settings.baseUrl || key != settings.apiKey) "Test & save" else "Test connection") }
                        Spacer(Modifier.width(12.dp))
                        if (busy) CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    }
                    result?.let { (ok, msg) ->
                        Spacer(Modifier.height(8.dp))
                        Text(msg, color = if (ok) Good else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Group {
                    Toggle("Allow self-signed certificates", "Skip TLS checks for this server", settings.insecure) { on -> vm.updateSettings { it.copy(insecure = on) } }
                    Spacer(Modifier.height(8.dp))
                    Text("Refresh every ${interval.toInt()} s", style = MaterialTheme.typography.bodyLarge)
                    Text("How often open screens poll JellyGlance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = interval, onValueChange = { interval = (Math.round(it / 5f) * 5).toFloat() },
                        onValueChangeFinished = { vm.updateSettings { it.copy(refreshSeconds = interval.toInt()) } },
                        valueRange = 5f..300f,
                    )
                }
            }

            if (page == "notifications") item {
                SectionHeader("Notifications", Modifier.padding(top = 12.dp))
                Group {
                    Toggle("Server alerts", "Failing health checks, stalled downloads and open issues", settings.alerts) { enableNotifications("alerts", it) }
                    Toggle("New streams", "Tell me when someone starts playing", settings.streamAlerts) { enableNotifications("streams", it) }
                    if (settings.signedIn && settings.sessionRole in setOf("Owner", "Admin")) {
                        Toggle("New requests", "Approve or decline right from the notification", settings.requestAlerts) { enableNotifications("requests", it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Check every", style = MaterialTheme.typography.bodyLarge)
                    Text("Android runs background checks at most every 15 minutes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val opts = listOf(15, 30, 60, 180)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.alertMinutes == m, onClick = { vm.updateSettings { it.copy(alertMinutes = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                            ) { Text(if (m < 60) "${m}m" else "${m / 60}h") }
                        }
                    }
                }
            }

            if (page == "security") item {
                SectionHeader("App lock", Modifier.padding(top = 12.dp))
                val bm = androidx.biometric.BiometricManager.from(ctx)
                val allowed = if (Build.VERSION.SDK_INT >= 30) androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                val canLock = bm.canAuthenticate(allowed) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
                Group {
                    Toggle(
                        "Lock JellyGlance",
                        if (canLock) "Ask for your fingerprint, face or screen lock when opening the app, and after 30 seconds away"
                        else "Set up a screen lock or fingerprint in Android's settings first",
                        settings.appLock && canLock,
                    ) { on -> if (canLock || !on) vm.updateSettings { it.copy(appLock = on) } }
                }
                Text(
                    "The app can stop streams and approve requests, so a lock is worth it on a shared phone or tablet.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }

            if (page == "appearance") item {
                SectionHeader("Mode", Modifier.padding(top = 4.dp))
                Group {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = settings.theme == m, onClick = { vm.updateSettings { it.copy(theme = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Spacer(Modifier.height(8.dp))
                        Toggle("Material You colours", "Use your wallpaper colours instead of a JellyGlance theme", settings.dynamicColor) { on ->
                            vm.updateSettings { it.copy(dynamicColor = on) }
                        }
                    }
                }
            }

            if (page == "appearance" && !settings.dynamicColor) item {
                SectionHeader("JellyGlance theme · ${if (settings.themeName == "Server") "from your web dashboard" else settings.themeName}", Modifier.padding(top = 12.dp))
                if (settings.signedIn) Group {
                    PageRow(Icons.Rounded.Sync, "Match my web dashboard", "Use the theme saved in your JellyGlance account") { vm.syncTheme() }
                }
                Group { ThemePicker(settings.themeName) { name -> vm.updateSettings { it.copy(themeName = name) } } }
            }

            if (page == "dashboard") item {
                SectionHeader("Dashboard", Modifier.padding(top = 12.dp))
                Group {
                    Action(Icons.Rounded.GridView, "All widgets and pinned cards") { onBrowse() }
                    Action(Icons.Rounded.Widgets, "Refresh the home-screen widget") { SyncWorker.runNow(ctx) }
                }
                Text(
                    "Add the JellyGlance widget from your launcher's widget picker. It updates in the background with your alerts.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                )
            }

            if (page == "about") item {
                ProjectCard(vm, Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp))
                SectionHeader("App updates", Modifier.padding(top = 12.dp))
                Group { UpdateSettings(vm) }
                SectionHeader("This app", Modifier.padding(top = 12.dp))
                Group {
                    Action(Icons.AutoMirrored.Rounded.OpenInNew, "Open JellyGlance in the browser") {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.baseUrl + "/"))) }
                    }
                    Action(Icons.AutoMirrored.Rounded.Logout, "Disconnect from this server") { confirmSignOut = true }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "JellyGlance Companion ${BuildConfig.VERSION_NAME}\nUnofficial companion app for JellyGlance. MIT licensed.\n" +
                        "JellyGlance grew out of Jellystat. Brand logos from Simple Icons (CC0).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                TextButton(onClick = { dev.nerdytech.jellyglance.ui.Links.browser(ctx, Project.JELLYSTAT) }) { Text("Jellystat on GitHub") }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Disconnect?") },
            text = { Text("This removes the server URL, API key and your dashboard layout from this device.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Disconnect") } },
            dismissButton = { OutlinedButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePicker(selected: String, onPick: (String) -> Unit) {
    var group by remember { mutableStateOf(ThemePresets.byName(selected).group) }
    Text(
        "The same presets as the JellyGlance web dashboard. Pick the one you use there.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemePresets.groups.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) }) }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemePresets.all.filter { it.group == group }.forEach { p ->
            val isSel = p.name == selected
            Column(
                Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(p.name) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .border(if (isSel) 3.dp else 1.dp, if (isSel) Color(p.primary) else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(if (isSel) 5.dp else 3.dp).clip(CircleShape).background(Color(p.background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(p.primary), Color(p.secondary)))),
                    )
                    if (isSel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(p.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private data class SettingsPage(val key: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val summary: (dev.nerdytech.jellyglance.data.AppSettings) -> String)

private val PAGES = listOf(
    SettingsPage("account", "Account", Icons.Rounded.Person) { if (it.signedIn) "Signed in as ${it.sessionUser}" else "Not signed in" },
    SettingsPage("connection", "Connection", Icons.Rounded.Link) { s ->
        listOf(s.baseUrl.removePrefix("https://").removePrefix("http://"), "refresh ${s.refreshSeconds}s", if (s.apiKey.isNotBlank()) "API key set" else "no API key").joinToString(" · ")
    },
    SettingsPage("appearance", "Appearance", Icons.Rounded.Palette) { s ->
        listOf(s.theme.name.lowercase().replaceFirstChar { it.uppercase() }, if (s.dynamicColor) "Material You" else if (s.themeName == "Server") "Web dashboard theme" else s.themeName).joinToString(" · ")
    },
    SettingsPage("notifications", "Notifications", Icons.Rounded.Notifications) { s ->
        listOfNotNull("Alerts".takeIf { s.alerts }, "New streams".takeIf { s.streamAlerts }).joinToString(" & ").ifBlank { "Off" } + " · every ${s.alertMinutes} min"
    },
    SettingsPage("security", "Privacy & security", Icons.Rounded.Lock) { if (it.appLock) "App lock on" else "App lock off" },
    SettingsPage("dashboard", "Dashboard & widget", Icons.Rounded.Dashboard) { "${it.dashboard.size} pinned widgets" },
    SettingsPage("about", "About & disconnect", Icons.Rounded.Info) { "Version ${BuildConfig.VERSION_NAME}" },
)

@Composable
private fun PageRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileCard(vm: AppViewModel, s: dev.nerdytech.jellyglance.data.AppSettings, onSignIn: () -> Unit, onOpen: () -> Unit) {
    val brand = dev.nerdytech.jellyglance.ui.theme.LocalBrand.current
    Card(
        onClick = if (s.signedIn) onOpen else onSignIn, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(brand.primary.copy(alpha = 0.35f), brand.secondary.copy(alpha = 0.18f))))) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (s.signedIn && s.sessionUserId.isNotBlank()) UserAvatar(vm, s.sessionUserId, s.sessionUser, 56.dp)
                else dev.nerdytech.jellyglance.ui.components.Avatar(s.sessionUser.ifBlank { "?" }, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (s.signedIn) s.sessionUser else "Not signed in", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (s.signedIn) listOfNotNull(s.sessionRole.ifBlank { null }, s.baseUrl.removePrefix("https://").removePrefix("http://")).joinToString(" · ")
                        else if (s.sessionExpired) "Your session ended. Tap to sign in again." else "Tap to sign in with your JellyGlance account",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.signedIn && s.sessionExp > 0) Text(
                        "Session ends " + dev.nerdytech.jellyglance.data.Fields.relative(java.time.Instant.ofEpochSecond(s.sessionExp)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
    }
}
