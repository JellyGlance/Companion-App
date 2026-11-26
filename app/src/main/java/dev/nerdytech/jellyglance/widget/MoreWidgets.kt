package dev.nerdytech.jellyglance.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.nerdytech.jellyglance.JellyGlanceApp
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ManagedRequest
import dev.nerdytech.jellyglance.data.Stream
import org.json.JSONArray
import org.json.JSONObject

/** Lists the extra widgets show, saved by SyncWorker next to [WidgetSnapshot]. */
object WidgetLists {
    data class Play(val title: String, val sub: String, val viewer: String, val poster: String?, val progress: Int, val paused: Boolean)
    data class Pending(val key: String, val requestId: String, val sourceId: String, val title: String, val who: String, val poster: String?)

    private fun prefs(c: Context) = c.getSharedPreferences("widget-lists", Context.MODE_PRIVATE)

    fun saveStreams(c: Context, baseUrl: String, list: List<Stream>) {
        val a = JSONArray()
        list.take(4).forEach { s ->
            a.put(
                JSONObject().put("title", s.title).put("sub", s.subtitle).put("viewer", listOf(s.viewer, s.device.ifBlank { s.client }).filter { it.isNotBlank() }.joinToString(" · "))
                    .put("poster", dev.nerdytech.jellyglance.data.Models.posterUrl(baseUrl, s.posterId, 160) ?: "")
                    .put("progress", ((s.progress ?: 0f) * 100).toInt()).put("paused", s.paused),
            )
        }
        prefs(c).edit().putString("streams", a.toString()).apply()
    }

    fun savePending(c: Context, list: List<ManagedRequest>?) {
        if (list == null) { prefs(c).edit().remove("pending").apply(); return }
        val a = JSONArray()
        list.take(4).forEach { r ->
            a.put(JSONObject().put("key", r.key).put("requestId", r.requestId).put("sourceId", r.sourceId).put("title", r.title).put("who", r.requestedBy).put("poster", r.posterUrl ?: ""))
        }
        prefs(c).edit().putString("pending", a.toString()).putInt("pendingTotal", list.size).apply()
    }

    fun streams(c: Context): List<Play> = runCatching {
        val a = JSONArray(prefs(c).getString("streams", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Play(it.optString("title"), it.optString("sub"), it.optString("viewer"), it.optString("poster").ifBlank { null }, it.optInt("progress"), it.optBoolean("paused")) } }
    }.getOrDefault(emptyList())

    /** null when this account can't manage requests. */
    fun pending(c: Context): Pair<List<Pending>, Int>? {
        val raw = prefs(c).getString("pending", null) ?: return null
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Pending(it.optString("key"), it.optString("requestId"), it.optString("sourceId"), it.optString("title"), it.optString("who"), it.optString("poster").ifBlank { null }) } } to
                prefs(c).getInt("pendingTotal", a.length())
        }.getOrNull()
    }

    /** Glance keeps widget sessions alive, so bump a version in each widget's state to make it reload. */
    val VERSION = androidx.datastore.preferences.core.longPreferencesKey("v")

    suspend fun updateAll(c: Context) {
        val now = System.currentTimeMillis()
        listOf(GlanceWidget(), NowPlayingWidget(), PendingRequestsWidget(), HealthWidget()).forEach { w ->
            androidx.glance.appwidget.GlanceAppWidgetManager(c).getGlanceIds(w.javaClass).forEach { id ->
                androidx.glance.appwidget.state.updateAppWidgetState(c, id) { it[VERSION] = now }
                w.update(c, id)
            }
        }
    }
}

private val Bg = ColorProvider(Color(0xFF14111F))
private val Fg = ColorProvider(Color(0xFFF2F0FA))
private val Dim = ColorProvider(Color(0xFFA39FBA))
private val Brand = ColorProvider(Color(0xFFB596FF))
private val Good = Color(0xFF2FBF71)
private val Bad = Color(0xFFE5484D)

/** Small posters for the widget, fetched through Coil (Glance needs bitmaps). */
private suspend fun posters(c: Context, urls: List<String?>): Map<String, Bitmap> = urls.filterNotNull().distinct().mapNotNull { u ->
    val r = c.imageLoader.execute(ImageRequest.Builder(c).data(u).size(120, 180).allowHardware(false).build())
    (r as? SuccessResult)?.drawable?.toBitmap(120, 180)?.let { u to it }
}.toMap()

@Composable
private fun Header(title: String, trailing: String = "") {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.jelly_logo), null, GlanceModifier.size(20.dp))
        Spacer(GlanceModifier.width(6.dp))
        Text(title, style = TextStyle(color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
        if (trailing.isNotBlank()) Text(trailing, style = TextStyle(color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(6.dp))
        Image(ImageProvider(android.R.drawable.ic_popup_sync), "Refresh", GlanceModifier.size(18.dp).clickable(actionRunCallback<RefreshAction>()))
    }
}

private fun openTab(c: Context, tab: String) = Intent(c, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_TAB, tab)

/** What's playing right now, with posters and progress. */
class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val v = androidx.glance.currentState(WidgetLists.VERSION) ?: 0L
            var list by remember { mutableStateOf(WidgetLists.streams(context)) }
            var art by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
            LaunchedEffect(v) { list = WidgetLists.streams(context); art = posters(context, list.map { it.poster }) }
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(12.dp).clickable(actionStartActivity(openTab(context, "streams")))) {
                Header("Now playing", if (list.isEmpty()) "" else "${list.size}")
                Spacer(GlanceModifier.height(8.dp))
                if (list.isEmpty()) Text("Nothing is playing", style = TextStyle(color = Dim, fontSize = 13.sp))
                list.take(3).forEach { p ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = p.poster?.let { art[it] }
                        if (bmp != null) Image(ImageProvider(bmp), null, GlanceModifier.size(30.dp, 45.dp).cornerRadius(6.dp), contentScale = ContentScale.Crop)
                        else Box(GlanceModifier.size(30.dp, 45.dp).cornerRadius(6.dp).background(ColorProvider(Color(0xFF2A2638)))) {}
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(p.title, style = TextStyle(color = Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(listOf(p.sub, p.viewer).filter { it.isNotBlank() }.joinToString(" · "), style = TextStyle(color = Dim, fontSize = 11.sp), maxLines = 1)
                            Text((if (p.paused) "⏸ " else "▶ ") + "${p.progress}%", style = TextStyle(color = Brand, fontSize = 10.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Pending requests with an Approve button each (admins). */
class PendingRequestsWidget : GlanceAppWidget() {
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val v = androidx.glance.currentState(WidgetLists.VERSION) ?: 0L
            var loaded by remember { mutableStateOf(WidgetLists.pending(context)) }
            var art by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
            LaunchedEffect(v) { loaded = WidgetLists.pending(context); art = posters(context, loaded?.first.orEmpty().map { it.poster }) }
            val data = loaded
            Column(GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(12.dp).clickable(actionStartActivity(openTab(context, "requests")))) {
                Header("Requests", data?.second?.takeIf { it > 0 }?.let { "$it pending" } ?: "")
                Spacer(GlanceModifier.height(8.dp))
                when {
                    data == null -> Text("Sign in as a JellyGlance admin to approve requests here.", style = TextStyle(color = Dim, fontSize = 12.sp))
                    data.first.isEmpty() -> Text("No requests waiting", style = TextStyle(color = Dim, fontSize = 13.sp))
                }
                data?.first?.take(3)?.forEach { p ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = p.poster?.let { art[it] }
                        if (bmp != null) Image(ImageProvider(bmp), null, GlanceModifier.size(28.dp, 42.dp).cornerRadius(6.dp), contentScale = ContentScale.Crop)
                        Spacer(GlanceModifier.width(8.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(p.title, style = TextStyle(color = Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(p.who, style = TextStyle(color = Dim, fontSize = 11.sp), maxLines = 1)
                        }
                        Box(
                            GlanceModifier.cornerRadius(14.dp).background(ColorProvider(Color(0xFF8B5CC8))).padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable(actionRunCallback<ApproveAction>(actionParametersOf(ApproveAction.Id to p.requestId, ApproveAction.Source to p.sourceId, ApproveAction.Key to p.key))),
                        ) { Text("Approve", style = TextStyle(color = Fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        }
    }
}

class ApproveAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as JellyGlanceApp
        val id = parameters[Id] ?: return
        runCatching { app.requestAdmin.actById(id, parameters[Source].orEmpty(), "approve") }
        // Drop it from the list straight away, then let a sync confirm.
        val left = WidgetLists.pending(context)?.first.orEmpty().filter { it.requestId != id }
        context.getSharedPreferences("widget-lists", Context.MODE_PRIVATE).edit()
            .putString("pending", JSONArray(left.map { JSONObject().put("key", it.key).put("requestId", it.requestId).put("sourceId", it.sourceId).put("title", it.title).put("who", it.who).put("poster", it.poster ?: "") }).toString())
            .apply()
        WidgetLists.updateAll(context)
        dev.nerdytech.jellyglance.work.SyncWorker.runNow(context)
    }

    companion object {
        val Id = ActionParameters.Key<String>("requestId")
        val Source = ActionParameters.Key<String>("sourceId")
        val Key = ActionParameters.Key<String>("key")
    }
}

/** A small tile: health dot and the stream count. */
class HealthWidget : GlanceAppWidget() {
    override val stateDefinition = androidx.glance.state.PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val v = androidx.glance.currentState(WidgetLists.VERSION) ?: 0L
            val s = remember(v) { WidgetSnapshot.load(context) }
            Column(
                GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(10.dp).clickable(actionStartActivity<MainActivity>()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(ImageProvider(R.drawable.jelly_logo), null, GlanceModifier.size(32.dp))
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(9.dp).cornerRadius(5.dp).background(ColorProvider(if (s.error.isNotBlank()) Color(0xFF8A8799) else if (s.healthOk) Good else Bad))) {}
                    Spacer(GlanceModifier.width(5.dp))
                    Text(if (s.error.isNotBlank()) "Offline" else s.health, style = TextStyle(color = Fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Text("${s.streams} streaming", style = TextStyle(color = Dim, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget() }
class PendingRequestsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PendingRequestsWidget() }
class HealthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HealthWidget() }
