package dev.nerdytech.jellyglance.work

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.nerdytech.jellyglance.JellyGlanceApp
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.ManagedRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * New pending requests become notifications with Approve / Decline buttons (admins only).
 * Each request is notified once; its notification id is derived from the request so the button
 * can replace it with the outcome.
 */
object RequestNotifier {
    private const val PREFS = "request-alerts"
    private const val ACTION = "dev.nerdytech.jellyglance.REQUEST_ACTION"

    private fun idFor(r: String) = 20_000 + (r.hashCode() and 0x7FFF)

    fun check(context: Context, pending: List<ManagedRequest>) {
        val app = context.applicationContext as JellyGlanceApp
        val s = app.settings.value
        if (!s.requestAlerts || !s.signedIn || s.sessionRole !in setOf("Owner", "Admin")) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet()) ?: emptySet()
        // First run: remember what's already waiting instead of flooding the shade.
        if (!prefs.getBoolean("primed", false)) {
            prefs.edit().putBoolean("primed", true).putStringSet("seen", pending.map { it.key }.toSet()).apply()
            return
        }
        pending.filter { it.key !in seen }.take(5).forEach { notify(context, it) }
        prefs.edit().putStringSet("seen", (seen + pending.map { it.key }).toList().takeLast(300).toSet()).apply()
    }

    private fun actionIntent(context: Context, r: ManagedRequest, action: String) = PendingIntent.getBroadcast(
        context, (r.key + action).hashCode(),
        Intent(context, RequestActionReceiver::class.java).setAction(ACTION)
            .putExtra("key", r.key).putExtra("requestId", r.requestId).putExtra("sourceId", r.sourceId)
            .putExtra("title", r.title).putExtra("action", action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(context: Context, r: ManagedRequest) {
        val open = PendingIntent.getActivity(
            context, idFor(r.key),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_TAB, "requests"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val what = listOf(if (r.isTv) "Series" else "Movie", r.year).filter { it.isNotBlank() }.joinToString(" · ")
        val n = NotificationCompat.Builder(context, JellyGlanceApp.CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat_jelly)
            .setColor(0xFF8B5CF8.toInt())
            .setContentTitle("${r.requestedBy.ifBlank { "Someone" }} requested ${r.title}")
            .setContentText(what)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Approve", actionIntent(context, r, "approve"))
            .addAction(0, "Decline", actionIntent(context, r, "decline"))
            .build()
        try { NotificationManagerCompat.from(context).notify(idFor(r.key), n) } catch (_: SecurityException) {}
    }

    fun showResult(context: Context, key: String, text: String) {
        val n = NotificationCompat.Builder(context, JellyGlanceApp.CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat_jelly).setColor(0xFF8B5CF8.toInt())
            .setContentTitle(text).setTimeoutAfter(8000).setAutoCancel(true).setOnlyAlertOnce(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(idFor(key), n) } catch (_: SecurityException) {}
    }
}

/** Handles the Approve / Decline buttons on a request notification. */
class RequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as JellyGlanceApp
        val key = intent.getStringExtra("key") ?: return
        val requestId = intent.getStringExtra("requestId") ?: return
        val sourceId = intent.getStringExtra("sourceId").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val action = intent.getStringExtra("action") ?: return
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val r = runCatching { app.requestAdmin.actById(requestId, sourceId, action) }
                RequestNotifier.showResult(
                    context, key,
                    if (r.isSuccess) "${if (action == "approve") "Approved" else "Declined"} $title"
                    else "Couldn't ${action} $title: ${r.exceptionOrNull()?.message ?: "error"}",
                )
            } finally {
                done.finish()
            }
        }
    }
}
