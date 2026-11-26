package dev.nerdytech.jellyglance.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.nerdytech.jellyglance.data.Level
import dev.nerdytech.jellyglance.data.ThemeMode

val Warn = Color(0xFFE5A50A)
val Alert = Color(0xFFE5484D)
val Good = Color(0xFF2FBF71)

/** The two JellyGlance accent colours of the active theme, for gradients and branded surfaces. */
data class Brand(val primary: Color, val secondary: Color)

val LocalBrand = staticCompositionLocalOf { Brand(Color(0xFF9B6AC8), Color(0xFF4AA8BC)) }

fun levelColor(level: Level, normal: Color): Color = when (level) {
    Level.ALERT -> Alert
    Level.WARN -> Warn
    Level.OK -> normal
}

private fun onColor(c: Color) = if (c.luminance() > 0.45f) Color(0xFF111111) else Color.White

/** A dark Material scheme built the way the JellyGlance web app layers its four theme colours. */
private fun darkScheme(p: ThemePreset): androidx.compose.material3.ColorScheme {
    val primary = Color(p.primary)
    val secondary = Color(p.secondary)
    val bg = Color(p.background)
    val surface = Color(p.surface)
    val text = Color(0xFFE9E7F0)
    return darkColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = lerp(bg, primary, 0.32f),
        onPrimaryContainer = lerp(primary, Color.White, 0.6f),
        secondary = secondary,
        onSecondary = onColor(secondary),
        secondaryContainer = lerp(surface, primary, 0.22f),
        onSecondaryContainer = text,
        tertiary = secondary,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = lerp(surface, Color.White, 0.06f),
        onSurfaceVariant = lerp(text, bg, 0.36f),
        surfaceContainerLowest = bg,
        surfaceContainerLow = lerp(bg, surface, 0.5f),
        surfaceContainer = surface,
        surfaceContainerHigh = lerp(surface, Color.White, 0.05f),
        surfaceContainerHighest = lerp(surface, Color.White, 0.09f),
        outline = lerp(surface, Color.White, 0.22f),
        outlineVariant = lerp(surface, Color.White, 0.1f),
        error = Color(0xFFFF8A8D),
        errorContainer = Color(0xFF5C1B1E),
        onErrorContainer = Color(0xFFFFDAD9),
    )
}

/** Light mode keeps the theme's accents on a pale surface tinted with the primary colour. */
private fun lightScheme(p: ThemePreset): androidx.compose.material3.ColorScheme {
    val primary = lerp(Color(p.primary), Color.Black, 0.18f)
    val secondary = lerp(Color(p.secondary), Color.Black, 0.2f)
    val bg = lerp(Color.White, Color(p.primary), 0.04f)
    return lightColorScheme(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = lerp(Color.White, primary, 0.18f),
        secondary = secondary,
        tertiary = secondary,
        secondaryContainer = lerp(Color.White, primary, 0.12f),
        background = bg,
        surface = bg,
        surfaceContainerLow = lerp(bg, primary, 0.03f),
        surfaceContainer = Color.White,
        surfaceContainerHigh = lerp(Color.White, primary, 0.07f),
        surfaceContainerHighest = lerp(Color.White, primary, 0.11f),
    )
}

/** Poppins, JellyGlance's heading face on the web. Body text stays the system font, as on the web. */
val Poppins = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(dev.nerdytech.jellyglance.R.font.poppins_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(dev.nerdytech.jellyglance.R.font.poppins_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(dev.nerdytech.jellyglance.R.font.poppins_bold, FontWeight.Bold),
)

private val Type = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Poppins, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
    )
}

@Composable
fun JellyGlanceTheme(mode: ThemeMode, dynamic: Boolean, preset: ThemePreset = ThemePresets.default, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkScheme(preset)
        else -> lightScheme(preset)
    }
    val brand = if (dynamic && Build.VERSION.SDK_INT >= 31) Brand(scheme.primary, scheme.tertiary) else Brand(scheme.primary, scheme.secondary)
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
