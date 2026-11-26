package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.Stream
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.theme.Alert
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The same on-screen message presets as the JellyGlance web dashboard. */
private data class Preset(
    val id: String, val label: String, val emoji: String, val group: String, val text: String,
    val needsTime: Boolean = false, val minutes: Long = 15, val tonight: Boolean = false,
)

private val PRESETS = listOf(
    Preset("custom", "Custom", "✏️", "Write your own", ""),
    Preset("pause", "Please pause", "⏸️", "Playback", "{user}, please pause when you can."),
    Preset("enjoy", "No rush", "🎬", "Playback", "No rush — enjoy {title}."),
    Preset("credits", "Skip credits", "⏭️", "Playback", "Skip the credits when you can so the next episode does not autoplay."),
    Preset("volume", "Volume down", "🔉", "Playback", "Please turn the volume down a bit."),
    Preset("buffer", "Buffering?", "🔄", "Playback", "If {title} is buffering, try pausing for a few seconds or dropping quality."),
    Preset("stopping", "Stopping soon", "⏹️", "Playback", "Playback may be stopped at {time}.", needsTime = true, minutes = 10),
    Preset("dinner", "Dinner's ready", "🍽️", "Household", "Dinner's ready — pause {title} when you get to a good spot."),
    Preset("bedtime", "Bedtime", "🌙", "Household", "Heading to bed around {time}. Please wrap up {title} soon.", needsTime = true, minutes = 20),
    Preset("downstairs", "Come downstairs", "🏠", "Household", "{user}, can you come downstairs when you hit a pause?"),
    Preset("phone", "Someone needs you", "📞", "Household", "{user}, someone needs you — pause when you can."),
    Preset("leaving", "Leaving soon", "🚗", "Household", "We are heading out at {time}. Please pause {title} and wrap up.", needsTime = true, minutes = 30),
    Preset("checkin", "Check in", "👋", "Household", "Hi {user} on {device} — can you check in when you get a moment?"),
    Preset("quality", "Lower quality", "📉", "Server", "This stream is loading the server. Please drop quality or switch to direct play if you can."),
    Preset("busy", "Server is busy", "🔥", "Server", "The server is busy right now. Please pause or lower quality on {device}."),
    Preset("direct", "Direct play please", "📡", "Server", "Please switch to Direct Play if your client allows it — transcoding is heavy right now."),
    Preset("wifi", "Wi-Fi issue", "📶", "Server", "Wi-Fi looks unhappy. Pause {title}, move closer to the access point, then resume."),
    Preset("maintenance", "Maintenance soon", "🛠️", "Maintenance", "Server maintenance starts at {time}. Please pause {title} and save your place.", needsTime = true, minutes = 20),
    Preset("restart", "Restarting Jellyfin", "🔁", "Maintenance", "Jellyfin is restarting at {time}. Playback on {device} will drop — pause before then.", needsTime = true, minutes = 10),
    Preset("tonight", "Restart tonight", "🌙", "Maintenance", "The server restarts tonight at {time}. Finish {title} before then if you can.", needsTime = true, tonight = true),
    Preset("update", "Update in progress", "⬆️", "Maintenance", "The server is updating. {title} may stop. You can resume after maintenance."),
    Preset("library-scan", "Library scan", "📚", "Maintenance", "A library scan is running, so streams may hitch. Pause if it gets choppy."),
)

/** Reasons for "stop with a message" (shown, then playback stops after 5 seconds). */
private val STOP_REASONS = listOf(
    "Server maintenance" to "The server is going down for maintenance. Playback will stop in a few seconds.",
    "Too many streams" to "This account has too many streams running. Playback will stop in a few seconds.",
    "Please use direct play" to "Please switch to direct play. This transcode will be stopped in a few seconds.",
    "Account sharing" to "This session looks like account sharing. Playback will stop in a few seconds.",
    "Custom message" to "",
)

private fun fill(text: String, s: Stream, time: LocalDateTime): String = text
    .replace("{user}", s.viewer.ifBlank { "there" })
    .replace("{device}", s.device.ifBlank { s.client }.ifBlank { "your device" })
    .replace("{title}", s.title.ifBlank { "this" })
    .replace("{time}", time.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")))

private fun defaultTime(p: Preset): LocalDateTime {
    val now = LocalDateTime.now()
    if (p.tonight) return now.withHour(23).withMinute(0).withSecond(0).let { if (it.isBefore(now)) it.plusDays(1) else it }
    return now.plusMinutes(p.minutes).withSecond(0)
}

@Composable
private fun SheetHeader(title: String, s: Stream) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF14111F)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                listOf(s.viewer, s.device.ifBlank { s.client }, s.title).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
        }
    }
}

@Composable
private fun ClientLimit(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0x33F5A623)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = androidx.compose.ui.graphics.Color(0xFFF5A623), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun clientName(s: Stream) = s.client.ifBlank { "This app" }

/** Send an on-screen message to a viewer, like the web's "Send message". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageSheet(vm: AppViewModel, s: Stream, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var preset by remember { mutableStateOf(PRESETS[0]) }
    var time by remember { mutableStateOf(LocalDateTime.now().plusMinutes(15)) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun pick(p: Preset) {
        preset = p
        if (p.id != "custom") { time = defaultTime(p); text = fill(p.text, s, time) }
    }
    fun shift(minutes: Long) {
        time = time.plusMinutes(minutes)
        if (preset.id != "custom") text = fill(preset.text, s, time)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetHeader("Send message", s)
            val blocked = s.canMessage == false
            if (blocked) ClientLimit(
                "${clientName(s)} doesn't accept messages. Jellyfin reports this session can't be remote-controlled, " +
                    "so nothing would appear on screen. Messages work on Jellyfin Web, Android, Android TV and most other clients.",
            )
            Spacer(Modifier.height(16.dp))
            PRESETS.groupBy { it.group }.forEach { (group, list) ->
                Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEach { p -> FilterChip(selected = preset.id == p.id, onClick = { pick(p) }, label = { Text("${p.emoji}  ${p.label}") }) }
                }
            }
            if (preset.needsTime) {
                Spacer(Modifier.height(10.dp))
                Text("When: ${time.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))}", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-15L, -5L, 5L, 15L, 30L, 60L).forEach { m ->
                        OutlinedButton(onClick = { shift(m) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                            Text(if (m > 0) "+${m}m" else "${m}m")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { text = it; if (preset.id != "custom") preset = PRESETS[0] },
                modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Keep it short — this pops up over playback.") },
                label = { Text("Message from JellyGlance") },
            )
            error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            sending = true; error = null
                            vm.messageStream(s.sessionId, text.trim())
                                .onSuccess { onDone("Message sent to ${s.viewer.ifBlank { "the viewer" }}") }
                                .onFailure { e -> error = (e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: e.message ?: "Unable to send message" }
                            sending = false
                        }
                    },
                    enabled = !sending && !blocked && text.isNotBlank() && s.sessionId.isNotBlank(), modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (sending) "Sending…" else "Send message")
                }
            }
        }
    }
}

/** Stop a stream now, or show a reason first and stop it 5 seconds later (the web's "stop with a message"). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StopSheet(vm: AppViewModel, s: Stream, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var withMessage by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf(STOP_REASONS[0].first) }
    var text by remember { mutableStateOf(STOP_REASONS[0].second) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            SheetHeader("Stream controls", s)
            val linked = vm.settings.value.jellyfinLinked
            if (s.mediaControl == false) ClientLimit(
                "${clientName(s)} doesn't accept remote playback commands, so Pause/Resume aren't available " +
                    "and a stop may not take effect on that screen.",
            )
            if (linked && s.mediaControl != false) {
                Spacer(Modifier.height(14.dp))
                Text("Playback", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Pause" to Icons.Rounded.Pause, "Unpause" to Icons.Rounded.PlayArrow).forEach { (cmd, icon) ->
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true; error = null
                                    runCatching { vm.jellyfin.playstate(s.sessionId, cmd) }
                                        .onSuccess { onDone(if (cmd == "Pause") "Paused ${s.title}" else "Resumed ${s.title}") }
                                        .onFailure { e -> error = e.message ?: "Jellyfin didn't accept that" }
                                    busy = false
                                }
                            },
                            enabled = !busy && s.sessionId.isNotBlank(), modifier = Modifier.weight(1f),
                        ) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (cmd == "Pause") "Pause" else "Resume") }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Stop", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !withMessage, onClick = { withMessage = false }, label = { Text("Stop now") })
                FilterChip(selected = withMessage, onClick = { withMessage = true }, label = { Text("Stop with a message") })
            }
            Spacer(Modifier.height(10.dp))
            if (withMessage) {
                Text(
                    "Jellyfin shows this on screen, then stops the stream after 5 seconds.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    STOP_REASONS.forEach { (label, t) ->
                        FilterChip(selected = reason == label, onClick = { reason = label; if (t.isNotBlank()) text = t }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    text, { text = it; reason = "Custom message" }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    shape = RoundedCornerShape(14.dp), label = { Text("On-screen message") },
                )
            } else {
                Text(
                    "Ends playback of ${s.title} for ${s.viewer.ifBlank { "this viewer" }} right away.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            error?.let { Text(it, color = Alert, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            val r = if (withMessage) vm.stopStreamWithMessage(s.sessionId, text.trim()) else vm.stopStream(s.sessionId)
                            r.onSuccess { onDone("Stopped ${s.title}") }
                                .onFailure { e -> error = (e as? ApiException)?.let { it.detail.ifBlank { it.kind.message } } ?: e.message ?: "Unable to stop this session" }
                            busy = false
                        }
                    },
                    enabled = !busy && s.sessionId.isNotBlank() && (!withMessage || text.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = Alert),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Stopping…" else if (withMessage) "Confirm and stop" else "Stop")
                }
            }
        }
    }
}
