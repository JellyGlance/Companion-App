package dev.nerdytech.jellyglance.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nerdytech.jellyglance.JellyGlanceApp
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.Level
import dev.nerdytech.jellyglance.data.RouteState
import dev.nerdytech.jellyglance.data.Stream
import dev.nerdytech.jellyglance.widget.GlanceWidget
import dev.nerdytech.jellyglance.widget.WidgetSnapshot
import java.util.concurrent.TimeUnit

/**
 * Background refresh: feeds the home-screen widget and raises notifications for new problems
 * (failed health flags, stalled downloads, open issues) and, optionally, for new streams.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JellyGlanceApp
        val settings = app.settings.value
        if (!settings.configured) return Result.success()

        val ids = listOf("nowplaying", "downloads", "requests", "health", "stalled", "issues")
        val defs = ids.mapNotNull { Catalogue.get(it) }
        app.repository.refresh(defs.map { it.route } + Catalogue.LIVE_SESSIONS)
        val states = app.repository.routes.value

        fun headline(id: String) = Catalogue.get(id)?.let { d ->
            states[d.route]?.flat?.let { Fields.headline(it, d, settings.pinned[d.id]) }
        }

        val health = Catalogue.get("health")!!
        val healthState = states[health.route]
        val healthLevel = healthState?.flat?.let { Fields.overallLevel(it, health, Fields.headline(it, health, null)) }
        val streams = app.repository.nowPlaying(states)?.streams ?: emptyList()
        val anyError = states.values.firstOrNull { it.error != null }?.error

        WidgetSnapshot.save(
            applicationContext,
            WidgetSnapshot(
                streams = headline("nowplaying")?.value ?: "—",
                topStream = streams.firstOrNull()?.let { "${it.title} · ${it.viewer}" } ?: "",
                downloads = headline("downloads")?.value ?: "—",
                requests = headline("requests")?.value ?: "—",
                health = when (healthLevel) { Level.ALERT -> "Problem"; null -> "—"; else -> "Healthy" },
                healthOk = healthLevel != Level.ALERT,
                error = if (healthState?.flat == null) anyError?.kind?.short ?: "" else "",
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val admin = settings.signedIn && settings.sessionRole in setOf("Owner", "Admin")
        val pending = if (admin) runCatching { app.requestAdmin.queue().requests.filter { it.pending } }.getOrNull() else null
        dev.nerdytech.jellyglance.widget.WidgetLists.saveStreams(applicationContext, settings.baseUrl, streams)
        if (!admin || pending != null) dev.nerdytech.jellyglance.widget.WidgetLists.savePending(applicationContext, pending)
        dev.nerdytech.jellyglance.widget.WidgetLists.updateAll(applicationContext)
        dev.nerdytech.jellyglance.tile.StatusTile.refresh(applicationContext, streams.size, pending?.size)

        if (settings.alerts) notifyProblems(states, settings.pinned)
        if (settings.streamAlerts) notifyStreams(streams)
        if (pending != null) RequestNotifier.check(applicationContext, pending)
        return Result.success()
    }

    private fun notifyProblems(states: Map<String, RouteState>, pinned: Map<String, String>) {
        val problems = mutableListOf<Pair<String, String>>()
        val health = Catalogue.get("health")!!
        states[health.route]?.flat?.let { flat ->
            flat.leaves.filter { Fields.levelOf(it, health) == Level.ALERT }.forEach {
                problems += "health:${it.path}" to "Health check failing: ${Fields.humanize(it.key)}"
            }
        }
        if (states[health.route]?.error?.kind == dev.nerdytech.jellyglance.data.ApiException.Kind.OFFLINE) {
            problems += "offline" to "JellyGlance is unreachable"
        }
        for (id in listOf("stalled", "issues")) {
            val d = Catalogue.get(id)!!
            val h = states[d.route]?.flat?.let { Fields.headline(it, d, pinned[id]) } ?: continue
            if ((h.num ?: 0.0) > 0) problems += id to "${d.name}: ${h.value} ${h.caption.lowercase()}"
        }

        val prefs = applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()) ?: emptySet()
        val current = problems.map { it.first }.toSet()
        val fresh = problems.filter { it.first !in seen }
        prefs.edit().putStringSet("seen", current).apply()
        if (fresh.isEmpty()) return
        post(
            ALERT_ID,
            JellyGlanceApp.CHANNEL_ALERTS,
            if (fresh.size == 1) fresh[0].second else "${fresh.size} things need attention",
            fresh.joinToString("\n") { it.second },
        )
    }

    private fun notifyStreams(items: List<Stream>) {
        val prefs = applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("streams", emptySet()) ?: emptySet()
        // Session id plus title, so a new episode in the same session counts as a new stream.
        val byId = items.associateBy { it.sessionId + "|" + it.title + it.subtitle }
        prefs.edit().putStringSet("streams", byId.keys).apply()
        val fresh = byId.filterKeys { it !in seen }.values
        if (fresh.isEmpty()) return
        val lines = fresh.map { "${it.title} — ${it.viewer} on ${it.device.ifBlank { it.client }}" }
        post(STREAM_ID, JellyGlanceApp.CHANNEL_STREAMS, if (fresh.size == 1) "Now playing: ${lines[0]}" else "${fresh.size} new streams", lines.joinToString("\n"), tab = "streams")
    }

    private fun post(id: Int, channel: String, title: String, text: String, tab: String = "dashboard") {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val open = PendingIntent.getActivity(
            applicationContext, id,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_TAB, tab),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_stat_jelly)
            .setColor(0xFF8B5CF8.toInt())
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val PERIODIC = "jellyglance-sync"
        private const val ONCE = "jellyglance-sync-now"
        private const val ALERT_ID = 1001
        private const val STREAM_ID = 1002

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val app = context.applicationContext as JellyGlanceApp
            val minutes = app.settings.value.alertMinutes.coerceAtLeast(15).toLong()
            val req = PeriodicWorkRequestBuilder<SyncWorker>(minutes, TimeUnit.MINUTES).setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
