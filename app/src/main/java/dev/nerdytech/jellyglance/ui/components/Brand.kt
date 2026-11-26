package dev.nerdytech.jellyglance.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nerdytech.jellyglance.R

/** The JellyGlance wordmark ("Jelly" purple, "Glance" ink/white to suit the background). */
@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    Image(
        painterResource(if (dark) R.drawable.jg_wordmark_light else R.drawable.jg_wordmark), contentDescription = "JellyGlance",
        contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        modifier = modifier.height(height).aspectRatio(776f / 147f),
    )
}

/** The JellyGlance jellyfish logo, gently bobbing. */
@Composable
fun JellyMark(size: Dp, modifier: Modifier = Modifier, animate: Boolean = true) {
    val bob = if (animate) {
        val t = rememberInfiniteTransition(label = "jelly")
        val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "bob")
        y
    } else 0f
    Image(
        painterResource(R.drawable.jelly_logo), contentDescription = null,
        modifier = modifier.size(size).graphicsLayer { translationY = -bob * size.toPx() * 0.08f; rotationZ = (bob - 0.5f) * 4f },
    )
}

/** Full-area loading state: the jellyfish bobbing with a short line. */
@Composable
fun JellyLoading(modifier: Modifier = Modifier, text: String = "Loading…", size: Dp = 72.dp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        JellyMark(size)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Branded empty / nothing-here state. */
@Composable
fun BrandEmpty(title: String, body: String = "", modifier: Modifier = Modifier, size: Dp = 112.dp) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        JellyMark(size)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body.isNotBlank()) Text(
            body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
    }
}
