package dev.nerdytech.jellyglance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.work.SyncWorker
import java.time.Instant

/** What the home-screen widget shows; written by [SyncWorker] and the app. */
data class WidgetSnapshot(
    val streams: String = "—",
    val topStream: String = "",
    val downloads: String = "—",
    val requests: String = "—",
    val health: String = "—",
    val healthOk: Boolean = true,
    val error: String = "",
    val updatedAt: Long = 0L,
) {
    companion object {
        private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)
        fun save(c: Context, s: WidgetSnapshot) = prefs(c).edit()
            .putString("streams", s.streams).putString("topStream", s.topStream)
            .putString("downloads", s.downloads).putString("requests", s.requests)
            .putString("health", s.health).putBoolean("healthOk", s.healthOk)
            .putString("error", s.error).putLong("updatedAt", s.updatedAt).apply()

        fun load(c: Context) = prefs(c).let { p ->
            WidgetSnapshot(
                p.getString("streams", "—")!!, p.getString("topStream", "")!!,
                p.getString("downloads", "—")!!, p.getString("requests", "—")!!,
                p.getString("health", "—")!!, p.getBoolean("healthOk", true),
                p.getString("error", "")!!, p.getLong("updatedAt", 0L),
            )
        }
    }
}

class GlanceWidget : GlanceAppWidget() {
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val v = androidx.glance.currentState(WidgetLists.VERSION) ?: 0L
            Content(androidx.compose.runtime.remember(v) { WidgetSnapshot.load(context) })
        }
    }

    @Composable
    private fun Content(s: WidgetSnapshot) {
        val bg = ColorProvider(Color(0xFF1A1B24))
        val fg = ColorProvider(Color(0xFFF2F0FA))
        val dim = ColorProvider(Color(0xFFA39FBA))
        val brand = ColorProvider(Color(0xFFB596FF))
        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.jelly_logo), contentDescription = null, modifier = GlanceModifier.size(22.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text("JellyGlance", style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                Box(
                    modifier = GlanceModifier.size(10.dp).cornerRadius(5.dp)
                        .background(ColorProvider(if (s.error.isNotBlank()) Color(0xFF8A8799) else if (s.healthOk) Color(0xFF2FBF71) else Color(0xFFE5484D))),
                ) {}
                Spacer(GlanceModifier.width(8.dp))
                Image(
                    ImageProvider(android.R.drawable.ic_popup_sync), contentDescription = "Refresh",
                    modifier = GlanceModifier.size(20.dp).clickable(actionRunCallback<RefreshAction>()),
                )
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Stat("Streams", s.streams, brand, dim, GlanceModifier.defaultWeight())
                Stat("Downloads", s.downloads, fg, dim, GlanceModifier.defaultWeight())
                Stat("Requests", s.requests, fg, dim, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.height(8.dp))
            val footer = when {
                s.error.isNotBlank() -> "Can't refresh: ${s.error}"
                s.topStream.isNotBlank() -> "▶ ${s.topStream}"
                else -> s.health
            }
            Text(footer, style = TextStyle(color = dim, fontSize = 12.sp), maxLines = 1)
            if (s.updatedAt > 0) {
                Text("Updated " + Fields.relative(Instant.ofEpochMilli(s.updatedAt)), style = TextStyle(color = dim, fontSize = 10.sp), maxLines = 1)
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String, valueColor: ColorProvider, labelColor: ColorProvider, modifier: GlanceModifier) {
        Column(modifier = modifier) {
            Text(value, style = TextStyle(color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(label, style = TextStyle(color = labelColor, fontSize = 11.sp), maxLines = 1)
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.runNow(context)
    }
}

class GlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlanceWidget()
}
