package dev.nerdytech.jellyglance.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val refreshSeconds: Int = 30,
    val insecure: Boolean = false,
    val dashboard: List<String> = Catalogue.DEFAULT_DASHBOARD,
    val pinned: Map<String, String> = emptyMap(),
    val alerts: Boolean = false,
    val streamAlerts: Boolean = false,
    val alertMinutes: Int = 15,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val themeName: String = "JellyGlance",
    /** The palette from the user's JellyGlance web preferences when themeName is "Server" (primary, secondary, background, surface). */
    val serverTheme: List<Long> = emptyList(),
    /** Dismissed issue key -> epoch ms; a newer occurrence of the same issue shows it again. */
    val dismissed: Map<String, Long> = emptyMap(),
    /** JellyGlance account session (Bearer JWT). The API key stays as the fallback and for background work. */
    val sessionToken: String = "",
    val sessionUser: String = "",
    val sessionRole: String = "",
    /** Jellyfin user id from the session (Quick Connect / Jellyfin sign-in), used by My Glance. */
    val sessionUserId: String = "",
    /** JWT expiry in epoch seconds, 0 when the token has none (local accounts). */
    val sessionExp: Long = 0,
    val sessionExpired: Boolean = false,
    /** Direct Jellyfin link, used to make the Jellyfin app open a title (see JellyfinRemote). */
    val jellyfinUrl: String = "",
    val jellyfinToken: String = "",
    val jellyfinUserId: String = "",
    val jellyfinDeviceId: String = "",
    /** Check GitHub for new app releases (daily in the background, and on launch). */
    val updateChecks: Boolean = true,
    /** Admins: notify about new pending requests, with Approve / Decline buttons. */
    val requestAlerts: Boolean = true,
    /** Ask for fingerprint / face / screen lock when opening the app. */
    val appLock: Boolean = false,
    /** A release version the user chose to skip. */
    val skippedUpdate: String = "",
) {
    val jellyfinLinked: Boolean get() = jellyfinUrl.isNotBlank() && jellyfinToken.isNotBlank() && jellyfinUserId.isNotBlank()
    val configured: Boolean get() = baseUrl.isNotBlank() && (apiKey.isNotBlank() || sessionToken.isNotBlank())
    val signedIn: Boolean get() = sessionToken.isNotBlank() && (sessionExp == 0L || sessionExp * 1000 > System.currentTimeMillis())
}

/** SharedPreferences-backed settings exposed as a StateFlow so Compose and workers see one copy. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val value: AppSettings get() = state.value

    private fun read(): AppSettings {
        val dash = prefs.getString("dashboard2", null)
        return AppSettings(
            baseUrl = prefs.getString("baseUrl", "") ?: "",
            apiKey = prefs.getString("apiKey", "") ?: "",
            refreshSeconds = prefs.getInt("refreshSeconds", 30),
            insecure = prefs.getBoolean("insecure", false),
            dashboard = if (dash == null) Catalogue.DEFAULT_DASHBOARD else dash.split(',').filter { it.isNotBlank() },
            pinned = (prefs.getStringSet("pinned", emptySet()) ?: emptySet()).mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
            }.toMap(),
            alerts = prefs.getBoolean("alerts", false),
            streamAlerts = prefs.getBoolean("streamAlerts", false),
            alertMinutes = prefs.getInt("alertMinutes", 15),
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean("dynamicColor", false),
            themeName = prefs.getString("themeName", "JellyGlance") ?: "JellyGlance",
            serverTheme = (prefs.getString("serverTheme", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.takeIf { it.size == 4 } ?: emptyList(),
            sessionToken = prefs.getString("sessionToken", "") ?: "",
            sessionUser = prefs.getString("sessionUser", "") ?: "",
            sessionRole = prefs.getString("sessionRole", "") ?: "",
            sessionUserId = prefs.getString("sessionUserId", "") ?: "",
            sessionExp = prefs.getLong("sessionExp", 0),
            sessionExpired = prefs.getBoolean("sessionExpired", false),
            jellyfinUrl = prefs.getString("jellyfinUrl", "") ?: "",
            jellyfinToken = prefs.getString("jellyfinToken", "") ?: "",
            jellyfinUserId = prefs.getString("jellyfinUserId", "") ?: "",
            updateChecks = prefs.getBoolean("updateChecks", true),
            requestAlerts = prefs.getBoolean("requestAlerts", true),
            appLock = prefs.getBoolean("appLock", false),
            skippedUpdate = prefs.getString("skippedUpdate", "") ?: "",
            jellyfinDeviceId = prefs.getString("jellyfinDeviceId", null) ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("jellyfinDeviceId", it).apply() },
            dismissed = (prefs.getStringSet("dismissed", emptySet()) ?: emptySet()).mapNotNull {
                val i = it.lastIndexOf('@')
                if (i > 0) it.substring(0, i) to (it.substring(i + 1).toLongOrNull() ?: Long.MAX_VALUE) else null
            }.toMap(),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(state.value)
        prefs.edit()
            .putString("baseUrl", s.baseUrl.trim().trimEnd('/'))
            .putString("apiKey", s.apiKey.trim())
            .putInt("refreshSeconds", s.refreshSeconds)
            .putBoolean("insecure", s.insecure)
            .putString("dashboard2", s.dashboard.joinToString(","))
            .putStringSet("pinned", s.pinned.map { (k, v) -> "$k=$v" }.toSet())
            .putBoolean("alerts", s.alerts)
            .putBoolean("streamAlerts", s.streamAlerts)
            .putInt("alertMinutes", s.alertMinutes)
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putString("themeName", s.themeName)
            .putString("serverTheme", s.serverTheme.joinToString(","))
            .putString("sessionToken", s.sessionToken)
            .putString("sessionUser", s.sessionUser)
            .putString("sessionRole", s.sessionRole)
            .putString("sessionUserId", s.sessionUserId)
            .putLong("sessionExp", s.sessionExp)
            .putBoolean("sessionExpired", s.sessionExpired)
            .putString("jellyfinUrl", s.jellyfinUrl)
            .putString("jellyfinToken", s.jellyfinToken)
            .putString("jellyfinUserId", s.jellyfinUserId)
            .putBoolean("updateChecks", s.updateChecks)
            .putBoolean("requestAlerts", s.requestAlerts)
            .putBoolean("appLock", s.appLock)
            .putString("skippedUpdate", s.skippedUpdate)
            .putStringSet("dismissed", s.dismissed.map { (k, v) -> "$k@$v" }.toSet())
            .apply()
        state.value = read()
    }

    fun clearServer() = update { AppSettings(theme = it.theme, themeName = it.themeName, serverTheme = it.serverTheme, dynamicColor = it.dynamicColor) }
}
