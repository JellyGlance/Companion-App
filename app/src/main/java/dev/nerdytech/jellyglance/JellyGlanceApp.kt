package dev.nerdytech.jellyglance

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.nerdytech.jellyglance.data.JellyGlanceApi
import dev.nerdytech.jellyglance.data.Repository
import dev.nerdytech.jellyglance.data.SettingsStore
import dev.nerdytech.jellyglance.work.SyncWorker

class JellyGlanceApp : Application(), coil.ImageLoaderFactory {
    /** Jellyfin's device icons are SVGs. */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .crossfade(true)
        .build()

    lateinit var settings: SettingsStore
        private set
    lateinit var api: JellyGlanceApi
        private set
    lateinit var repository: Repository
        private set
    lateinit var auth: dev.nerdytech.jellyglance.data.Auth
        private set
    lateinit var discover: dev.nerdytech.jellyglance.data.Discover
        private set
    lateinit var library: dev.nerdytech.jellyglance.data.Library
        private set
    lateinit var admin: dev.nerdytech.jellyglance.data.Admin
        private set
    lateinit var me: dev.nerdytech.jellyglance.data.MyGlance
        private set
    lateinit var insights: dev.nerdytech.jellyglance.data.HomeInsights
        private set
    lateinit var stats: dev.nerdytech.jellyglance.data.Statistics
        private set
    lateinit var extras: dev.nerdytech.jellyglance.data.Extras
        private set
    lateinit var jellyfin: dev.nerdytech.jellyglance.data.JellyfinRemote
        private set
    lateinit var requestAdmin: dev.nerdytech.jellyglance.data.RequestAdmin
        private set
    lateinit var parity: dev.nerdytech.jellyglance.data.Parity
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        api = JellyGlanceApi({ settings.value }, onSessionRejected = {
            settings.update { it.copy(sessionToken = "", sessionExp = 0, sessionExpired = true) }
        })
        repository = Repository(api, java.io.File(filesDir, "route-cache"))
        auth = dev.nerdytech.jellyglance.data.Auth(api)
        discover = dev.nerdytech.jellyglance.data.Discover(api)
        library = dev.nerdytech.jellyglance.data.Library(api)
        admin = dev.nerdytech.jellyglance.data.Admin(api)
        me = dev.nerdytech.jellyglance.data.MyGlance(api)
        insights = dev.nerdytech.jellyglance.data.HomeInsights(api)
        stats = dev.nerdytech.jellyglance.data.Statistics(api)
        extras = dev.nerdytech.jellyglance.data.Extras(api)
        requestAdmin = dev.nerdytech.jellyglance.data.RequestAdmin(api)
        parity = dev.nerdytech.jellyglance.data.Parity(api)
        jellyfin = dev.nerdytech.jellyglance.data.JellyfinRemote({ settings.value }) {
            runCatching { android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME) }.getOrNull()
        }

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Server alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Health failures, stalled downloads and open issues"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_STREAMS, "New streams", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Someone started playing on Jellyfin"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REQUESTS, "New requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Someone requested a movie or series (Approve / Decline from the notification)"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
            description = "A new version of JellyGlance Companion is out"
        })
        SyncWorker.schedule(this)
        // Refresh widgets and the Quick Settings tile whenever the app starts.
        if (settings.value.configured) SyncWorker.runNow(this)
        dev.nerdytech.jellyglance.work.UpdateWorker.schedule(this)
    }

    companion object {
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_STREAMS = "streams"
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_REQUESTS = "requests"
        lateinit var instance: JellyGlanceApp
            private set
    }
}
