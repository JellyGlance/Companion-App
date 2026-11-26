package dev.nerdytech.jellyglance.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.BuildConfig
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ServerUpdate
import dev.nerdytech.jellyglance.ui.AppViewModel
import dev.nerdytech.jellyglance.ui.Links
import dev.nerdytech.jellyglance.ui.theme.Good
import dev.nerdytech.jellyglance.ui.theme.LocalBrand

/** The JellyGlance project's public places. */
object Project {
    const val WEBSITE = "https://jellyglance.com"
    const val DOCS = "https://docs.jellyglance.com/"
    const val GETTING_STARTED = "https://docs.jellyglance.com/guide/getting-started/"
    const val TROUBLESHOOTING = "https://docs.jellyglance.com/guide/troubleshooting/"
    const val DISCORD = "https://discord.gg/dMGhv8j2kx"
    const val GITHUB = "https://github.com/JellyGlance/Server"
    const val RELEASES = "https://github.com/JellyGlance/Server/releases"
    const val ISSUES = "https://github.com/JellyGlance/Server/issues/new/choose"
    const val DISCUSSIONS = "https://github.com/JellyGlance/Server/discussions"
    const val DEMO = "https://demo.jellyglance.com/"
    const val JELLYSTAT = "https://github.com/CyferShepard/Jellystat"
}

private val Discord = Color(0xFF5865F2)
private val GitHubInk = Color(0xFF24292F)
// JellyGlance's own purple and teal, fixed so the buttons stay readable in any theme.
private val JgPurple = Color(0xFF8B5CC8)
private val JgTeal = Color(0xFF2F8FA6)

/** Loads the server's version / update state once per screen. */
@Composable
fun rememberServerUpdate(vm: AppViewModel): ServerUpdate? {
    var u by remember { mutableStateOf<ServerUpdate?>(null) }
    LaunchedEffect(Unit) { u = runCatching { vm.extras.updates() }.getOrNull() }
    return u
}

/**
 * The JellyGlance hero: logo, server version and update state, then the big community buttons
 * (Discord, GitHub, website, docs) and smaller links. [compact] drops the secondary links.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectCard(vm: AppViewModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    val brand = LocalBrand.current
    val update = rememberServerUpdate(vm)
    fun go(url: String) = Links.browser(ctx, url)

    Column(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(brand.primary.copy(alpha = 0.35f), brand.secondary.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceContainer)))
            .border(1.dp, brand.primary.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF14111F)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(56.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("JellyGlance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "A modern Jellyfin dashboard",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    update?.current?.takeIf { it.isNotBlank() }?.let { Text("Server v$it", style = MaterialTheme.typography.labelMedium) }
                    update?.stars?.takeIf { it > 0 }?.let {
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                        Text("$it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 0.dp))
                    }
                }
            }
        }
        update?.let { u ->
            if (u.current.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                val color = if (u.available) brand.primary else Good
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.14f))
                        .clickable { go(u.releasesUrl) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.NewReleases, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (u.available) "Update available: v${u.latest}" else "Server is up to date",
                        style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.weight(1f),
                    )
                    Text("Releases", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton("Discord", painterResource(R.drawable.brand_discord), Discord, Modifier.weight(1f)) { go(Project.DISCORD) }
            BrandButton("GitHub", painterResource(R.drawable.brand_github), GitHubInk, Modifier.weight(1f)) { go(Project.GITHUB) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton("Website", rememberVectorPainter(Icons.Rounded.Language), JgPurple, Modifier.weight(1f)) { go(Project.WEBSITE) }
            BrandButton("Docs", rememberVectorPainter(Icons.AutoMirrored.Rounded.MenuBook), JgTeal, Modifier.weight(1f)) { go(Project.DOCS) }
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkChip("Getting started", Icons.Rounded.PlayCircle) { go(Project.GETTING_STARTED) }
                LinkChip("Troubleshooting", Icons.Rounded.SupportAgent) { go(Project.TROUBLESHOOTING) }
                LinkChip("Report a bug", Icons.Rounded.BugReport) { go(Project.ISSUES) }
                LinkChip("Discussions", Icons.Rounded.Forum) { go(Project.DISCUSSIONS) }
                LinkChip("Live demo", Icons.AutoMirrored.Rounded.OpenInNew) { go(Project.DEMO) }
            }
        }
    }
}

@Composable
private fun BrandButton(label: String, icon: Painter, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick, label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** A one-line community footer for the bottom of long pages. */
@Composable
fun CommunityFooter(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FooterLink(painterResource(R.drawable.brand_discord), "Discord", Discord) { Links.browser(ctx, Project.DISCORD) }
            FooterLink(painterResource(R.drawable.brand_github), "GitHub", MaterialTheme.colorScheme.onSurface) { Links.browser(ctx, Project.GITHUB) }
            FooterLink(rememberVectorPainter(Icons.AutoMirrored.Rounded.MenuBook), "Docs", MaterialTheme.colorScheme.onSurface) { Links.browser(ctx, Project.DOCS) }
            FooterLink(rememberVectorPainter(Icons.Rounded.Language), "Website", MaterialTheme.colorScheme.onSurface) { Links.browser(ctx, Project.WEBSITE) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "JellyGlance Companion ${BuildConfig.VERSION_NAME} · jellyglance.com",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FooterLink(icon: Painter, label: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** A screen title with the JellyGlance mark in front, for the main tabs. */
@Composable
fun BrandTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF14111F)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(34.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

/** Like the web sidebar's footer: JellyGlance version, Discord and GitHub stars. */
@Composable
fun BrandStrip(vm: AppViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val update = rememberServerUpdate(vm)
    val brand = LocalBrand.current
    Row(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(JgPurple.copy(alpha = 0.22f), JgTeal.copy(alpha = 0.12f))))
            .border(1.dp, JgPurple.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dev.nerdytech.jellyglance.ui.components.JellyMark(44.dp, animate = false)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            dev.nerdytech.jellyglance.ui.components.Wordmark(18.dp)
            Text(
                listOfNotNull(update?.current?.takeIf { it.isNotBlank() }?.let { "Server v$it" }, if (update?.available == true) "update available" else null)
                    .joinToString(" · ").ifBlank { "Companion ${BuildConfig.VERSION_NAME}" },
                style = MaterialTheme.typography.labelMedium, color = if (update?.available == true) brand.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Discord).clickable { Links.browser(ctx, Project.DISCORD) }.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.brand_discord), null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text("Join Discord", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { Links.browser(ctx, Project.GITHUB) }.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${update?.stars?.takeIf { it > 0 } ?: "Star"} on GitHub", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
