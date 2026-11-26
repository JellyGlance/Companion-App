package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nerdytech.jellyglance.BuildConfig
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Updater
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.Links
import dev.nerdytech.jellyglance.ui.theme.Alert
import kotlinx.coroutines.launch

private val UpdatePurple = Color(0xFF8B5CC8)

/** "Update available" card for the top of Home; hidden when there's nothing new or it was skipped. */
@Composable
fun UpdateBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val release by vm.update.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.checkForUpdate(quiet = true) }
    val r = release?.takeIf { it.version != settings.skippedUpdate } ?: return
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(UpdatePurple.copy(alpha = 0.35f), UpdatePurple.copy(alpha = 0.12f))))
            .border(1.dp, UpdatePurple.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable { open = true }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Color(0xFFC9A7E6))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Companion ${r.version} is available", style = MaterialTheme.typography.titleSmall)
            Text("You have ${BuildConfig.VERSION_NAME}. Tap to see what's new.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = { open = true }) { Text("Update") }
    }
    if (open) UpdateSheet(vm, r) { open = false }
}

/** Release notes, then Update now: download with progress, verify, and open Android's installer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(vm: AppViewModel, r: Updater.Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<java.io.File?>(null) }

    fun installOrAsk() {
        val f = downloaded ?: return
        if (!vm.updater.canInstall()) { needsPermission = true; return }
        needsPermission = false
        runCatching { vm.updater.install(f) }.onFailure { error = "Couldn't open the installer: ${it.message}" }
    }
    // Coming back from the "install unknown apps" screen: carry on if it was allowed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (needsPermission && vm.updater.canInstall()) installOrAsk() }

    ModalBottomSheet(onDismissRequest = { if (progress == null) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                dev.nerdytech.jellyglance.ui.components.JellyMark(44.dp, animate = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Update to ${r.version}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull("You have ${BuildConfig.VERSION_NAME}", r.apkSize.takeIf { it > 0 }?.let { Fields.fmtBytes(it.toDouble()) },
                            r.publishedAt?.let { Fields.formatString(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (r.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("What's new", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    r.notes.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { line ->
                        val heading = line.startsWith("#")
                        val text = line.trimStart('#', ' ').replace(Regex("^[-*] "), "• ").replace("**", "")
                            .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                        Text(
                            text, style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = if (heading) 6.dp else 2.dp),
                        )
                    }
                }
            }
            if (!Updater.canSelfUpdate) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This is a debug build, so it can't install release updates. Install the release APK from GitHub instead.",
                    style = MaterialTheme.typography.bodySmall, color = Alert,
                )
            }
            if (needsPermission) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Android needs your OK for JellyGlance to install updates. Turn on \"Allow from this source\", then come back.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { vm.updater.openInstallPermission() }) { Text("Open the setting") }
            }
            progress?.let { p ->
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                Text(
                    when {
                        downloaded == null -> "Downloading ${(p * 100).toInt()}%"
                        needsPermission -> "Downloaded. Waiting for permission to install."
                        else -> "Downloaded and verified. Android's installer will finish the update."
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                )
            }
            error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (downloaded != null) { installOrAsk(); return@Button }
                    scope.launch {
                        error = null; progress = 0f
                        runCatching { vm.updater.download(r) { p -> progress = p } }
                            .onSuccess { downloaded = it; progress = 1f; installOrAsk() }
                            .onFailure { error = it.message ?: "Download failed"; progress = null }
                    }
                },
                enabled = Updater.canSelfUpdate && (progress == null || downloaded != null),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Rounded.SystemUpdate, null); Spacer(Modifier.width(8.dp))
                Text(if (downloaded != null) "Install update" else "Update now")
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { vm.skipUpdate(r.version); onDismiss() }, enabled = progress == null) { Text("Skip this version") }
                TextButton(onClick = { Links.browser(ctx, r.url) }) {
                    Text("GitHub"); Spacer(Modifier.width(4.dp)); Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                }
                TextButton(onClick = onDismiss, enabled = progress == null || downloaded != null) { Text("Later") }
            }
        }
    }
}

/** Settings section: current version, check now, and the automatic-check switch. */
@Composable
fun UpdateSettings(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val release by vm.update.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    status ?: release?.let { "Version ${it.version} is available" } ?: "Releases come from github.com/${BuildConfig.UPDATE_REPO}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (release != null) Button(onClick = { open = true }) { Text("Update") }
            else OutlinedButton(
                onClick = {
                    scope.launch {
                        checking = true
                        status = when (val r = vm.checkForUpdate()) {
                            is Updater.Check.Available -> { open = true; "Version ${r.release.version} is available" }
                            is Updater.Check.UpToDate -> "You're up to date"
                            is Updater.Check.Failed -> r.message
                            null -> null
                        }
                        checking = false
                    }
                },
                enabled = !checking,
            ) { Text(if (checking) "Checking…" else "Check now") }
        }
        Row(Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(updateChecks = !it.updateChecks) } }.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check for updates automatically", style = MaterialTheme.typography.bodyLarge)
                Text("Daily, with a notification when a new version is out", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.updateChecks, { v -> vm.updateSettings { it.copy(updateChecks = v) } })
        }
    }
    release?.let { r -> if (open) UpdateSheet(vm, r) { open = false } }
}
