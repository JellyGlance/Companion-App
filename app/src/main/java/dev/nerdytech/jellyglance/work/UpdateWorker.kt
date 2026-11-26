package dev.nerdytech.jellyglance.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.nerdytech.jellyglance.JellyGlanceApp
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R
import dev.nerdytech.jellyglance.data.Updater
import java.util.concurrent.TimeUnit

/** Once a day: is there a newer Companion release on GitHub? Posts one notification per new version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JellyGlanceApp
        val s = app.settings.value
        if (!s.updateChecks) return Result.success()
        val r = (Updater(applicationContext).check() as? Updater.Check.Available)?.release ?: return Result.success()
        if (r.version == s.skippedUpdate) return Result.success()
        val prefs = applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getString("notified", "") == r.version) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val open = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_TAB, "dashboard"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(applicationContext, JellyGlanceApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_jelly)
            .setColor(0xFF8B5CF8.toInt())
            .setContentTitle("JellyGlance Companion ${r.version} is available")
            .setContentText("Open the app and tap Update now.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, n)
            prefs.edit().putString("notified", r.version).apply()
        } catch (_: SecurityException) {
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "jellyglance-update-check"
        private const val NOTIFICATION_ID = 1003

        fun schedule(context: Context) {
            if (!Updater.canSelfUpdate) return
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
