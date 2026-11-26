package dev.nerdytech.jellyglance.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.nerdytech.jellyglance.data.Models
import dev.nerdytech.jellyglance.data.Stream
import dev.nerdytech.jellyglance.ui.theme.Warn

/** 2:3 poster from JellyGlance's public image proxy, with a film icon while loading or missing. */
@Composable
fun Poster(url: String?, width: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.width(width).height(width * 1.5f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(width / 2.5f))
        if (url != null) {
            AsyncImage(
                ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Jellyfin's address, when known; device icons are loaded from its web client. */
val LocalJellyfinUrl = androidx.compose.runtime.staticCompositionLocalOf { "" }

/** A coloured logo for a Jellyfin client: a bundled brand mark, or a tinted icon when none exists. */
data class DeviceMark(val drawable: Int? = null, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null, val tint: Color = Color.Unspecified)

fun deviceMark(client: String, device: String): DeviceMark {
    val c = client.lowercase(); val d = device.lowercase()
    fun res(id: Int) = DeviceMark(drawable = id)
    return when {
        "jellyglance" in c -> DeviceMark(drawable = dev.nerdytech.jellyglance.R.mipmap.ic_launcher_foreground)
        "home assistant" in c || "hacs" in d -> res(dev.nerdytech.jellyglance.R.drawable.dev_homeassistant)
        "samsung" in c || "tizen" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_samsung)
        "webos" in c || c == "lg" -> res(dev.nerdytech.jellyglance.R.drawable.dev_lg)
        "xbox" in c || "xbox" in d || "uwp" in c -> DeviceMark(icon = androidx.compose.material.icons.Icons.Rounded.SportsEsports, tint = Color(0xFF107C10))
        "playstation" in c || "ps4" in c || "ps5" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_playstation)
        "kodi" in c || "jellycon" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_kodi)
        "roku" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_roku)
        "ios" in c || "ipad" in c || "iphone" in c || "infuse" in c || "swiftfin" in c || "apple" in c || "tvos" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_apple)
        "android" in c || "findroid" in c || "wholphin" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_android)
        "media player" in c || "desktop" in c || "windows" in c -> DeviceMark(icon = androidx.compose.material.icons.Icons.Rounded.DesktopWindows, tint = Color(0xFF0078D4))
        "web" in c || listOf("chrome", "firefox", "safari", "edge", "opera").any { it in d } -> when {
            "opera" in d -> res(dev.nerdytech.jellyglance.R.drawable.dev_opera)
            "edge" in d -> DeviceMark(icon = androidx.compose.material.icons.Icons.Rounded.Language, tint = Color(0xFF1FA2E0))
            "chrome" in d -> res(dev.nerdytech.jellyglance.R.drawable.dev_googlechrome)
            "firefox" in d -> res(dev.nerdytech.jellyglance.R.drawable.dev_firefoxbrowser)
            "safari" in d -> res(dev.nerdytech.jellyglance.R.drawable.dev_safari)
            else -> res(dev.nerdytech.jellyglance.R.drawable.dev_html5)
        }
        "jellyfin" in c || "streamyfin" in c || "finamp" in c -> res(dev.nerdytech.jellyglance.R.drawable.dev_jellyfin)
        else -> DeviceMark(icon = androidx.compose.material.icons.Icons.Rounded.Devices, tint = Color(0xFF9AA0A6))
    }
}

/** Jellyfin sends some device names URL-encoded ("Chrome%20Android", "Sarah's+Tab"). */
fun cleanDeviceName(name: String): String {
    var s = name
    // Some clients encode twice ("Sarah's%2BTab%2BA9%2B"), so unwrap up to two layers.
    repeat(2) {
        if ('%' !in s && '+' !in s) return s.trim()
        s = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    }
    return s.trim()
}

@Composable
fun DeviceIcon(client: String, device: String, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    if (client.isBlank() && device.isBlank()) return
    val m = remember(client, device) { deviceMark(client, device) }
    when {
        m.drawable != null -> androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(m.drawable), contentDescription = client.ifBlank { device }, modifier = modifier.size(size),
        )
        m.icon != null -> Icon(m.icon, contentDescription = client.ifBlank { device }, tint = m.tint, modifier = modifier.size(size))
    }
}

@Composable
fun Avatar(name: String, size: Dp = 20.dp, imageUrl: String? = null) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        )
        if (imageUrl != null) AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

/** One live stream: poster, title, viewer, device, playback method and progress. */
@Composable
fun StreamTile(s: Stream, baseUrl: String, posterWidth: Dp = 52.dp, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Poster(Models.posterUrl(baseUrl, s.posterId), posterWidth)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title.ifBlank { "Unknown title" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.subtitle.isNotBlank()) {
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Avatar(s.viewer, imageUrl = Models.userImageUrl(baseUrl, s.userId))
                DeviceIcon(s.client, s.device, 16.dp)
                Text(
                    listOf(s.viewer, s.device.ifBlank { s.client }).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (s.methodLabel.isNotBlank()) Pill(s.methodLabel, if (s.transcoding) Warn else MaterialTheme.colorScheme.primary)
            }
            s.progress?.let { p ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.paused) {
                        Icon(Icons.Rounded.Pause, "Paused", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    LinearProgressIndicator(
                        progress = { p }, modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Models.ticksToClock(s.positionTicks)} / ${Models.ticksToClock(s.runtimeTicks)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

@Composable
fun MiniProgress(progress: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress }, color = color, modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {},
    )
}

@androidx.compose.runtime.Composable
fun StatusBarScrim(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().windowInsetsTopHeight(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
    )
}
