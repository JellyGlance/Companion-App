package dev.nerdytech.jellyglance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dev.nerdytech.jellyglance.JellyGlanceApp
import dev.nerdytech.jellyglance.data.ApiException
import dev.nerdytech.jellyglance.data.AppSettings
import dev.nerdytech.jellyglance.data.Catalogue
import dev.nerdytech.jellyglance.data.Fields
import dev.nerdytech.jellyglance.data.RouteState
import dev.nerdytech.jellyglance.work.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {
    private val app = JellyGlanceApp.instance
    val settings: StateFlow<AppSettings> = app.settings.flow
    val routes: StateFlow<Map<String, RouteState>> = app.repository.routes

    suspend fun refresh(routes: Collection<String>) = app.repository.refresh(routes)

    fun refreshAsync(routes: Collection<String>) {
        viewModelScope.launch { app.repository.refresh(routes) }
    }

    fun dashboardRoutes(): List<String> =
        (DASHBOARD_ROUTES + settings.value.dashboard.mapNotNull { Catalogue.get(it)?.route }).distinct()

    fun nowPlaying(routes: Map<String, RouteState>) = app.repository.nowPlaying(routes)

    suspend fun pauseDownload(id: String, paused: Boolean): Result<Unit> = action(listOf(route("downloads"), route("stalled"))) {
        app.api.pauseDownload(id, paused)
    }

    suspend fun requestAction(req: dev.nerdytech.jellyglance.data.Models.Request, action: String): Result<Unit> = action(listOf(route("requests"), Catalogue.FULL_REQUESTS)) {
        app.api.requestAction(req.id, req.sourceId, action)
    }

    suspend fun refreshItem(itemId: String): Result<Unit> = action(emptyList()) { app.api.refreshItem(itemId) }

    suspend fun retryWebhook(webhookId: String): Result<Boolean> = try {
        val ok = app.api.retryWebhook(webhookId)
        delay(800)
        app.repository.refresh(listOf(Catalogue.WEBHOOK_HISTORY, route("health")))
        Result.success(ok)
    } catch (e: ApiException) {
        Result.failure(e)
    }

    fun dismiss(issue: dev.nerdytech.jellyglance.data.Issue) = updateSettings { it.copy(dismissed = it.dismissed + (issue.key to issue.latestMs)) }
    fun restore(key: String) = updateSettings { it.copy(dismissed = it.dismissed - key) }

    fun issues(routes: Map<String, RouteState>): List<dev.nerdytech.jellyglance.data.Issue> = dev.nerdytech.jellyglance.data.Issues.build(
        routes[route("health")]?.data?.let(dev.nerdytech.jellyglance.data.Models::health),
        routes[Catalogue.WEBHOOK_HISTORY]?.data?.let(dev.nerdytech.jellyglance.data.Issues::deliveries),
        routes[route("downloads")]?.data?.let(dev.nerdytech.jellyglance.data.Models::downloads),
    )

    private fun route(id: String) = Catalogue.get(id)!!.route

    private suspend fun action(refresh: List<String>, block: suspend () -> Unit): Result<Unit> = try {
        block()
        delay(600)
        app.repository.refresh(refresh)
        Result.success(Unit)
    } catch (e: ApiException) {
        Result.failure(e)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val before = settings.value
        app.settings.update(transform)
        val after = settings.value
        if (before.baseUrl != after.baseUrl || before.apiKey != after.apiKey || before.sessionToken != after.sessionToken) app.repository.clear()
        if (before.alertMinutes != after.alertMinutes) SyncWorker.schedule(app)
        if (before.baseUrl != after.baseUrl || before.apiKey != after.apiKey || before.insecure != after.insecure) SyncWorker.runNow(app)
    }

    fun toggleDashboard(id: String) = updateSettings { s ->
        s.copy(dashboard = if (id in s.dashboard) s.dashboard - id else s.dashboard + id)
    }

    fun moveDashboard(id: String, delta: Int) = updateSettings { s ->
        val list = s.dashboard.toMutableList()
        val i = list.indexOf(id)
        val j = (i + delta).coerceIn(0, list.size - 1)
        if (i >= 0 && i != j) { list.removeAt(i); list.add(j, id) }
        s.copy(dashboard = list)
    }

    fun pin(id: String, path: String?) = updateSettings { s ->
        s.copy(pinned = if (path == null) s.pinned - id else s.pinned + (id to path))
    }

    /** Checks a URL + key before saving them. Returns a short description of the server. */
    suspend fun testConnection(url: String, key: String, insecure: Boolean): Result<String> {
        val probe = settings.value.copy(baseUrl = url.trim().trimEnd('/'), apiKey = key.trim(), insecure = insecure)
        return try {
            val data = app.api.get("/api/jellyfin/status", probe)
            val flat = Fields.flatten(data)
            val version = flat.leaves.firstOrNull { it.key.contains("version", true) }?.value?.toString()
            // The status route is open on some installs; a widget route proves the key works.
            app.api.get(Catalogue.NOW_PLAYING, probe)
            Result.success(if (version != null) "Connected. Jellyfin $version" else "Connected")
        } catch (e: ApiException) {
            Result.failure(e)
        }
    }

    suspend fun stopStream(sessionId: String): Result<Unit> = try {
        app.api.stopSession(sessionId)
        delay(800)
        app.repository.refresh(listOf(Catalogue.NOW_PLAYING, Catalogue.LIVE_SESSIONS))
        Result.success(Unit)
    } catch (e: ApiException) {
        Result.failure(e)
    }

    suspend fun messageStream(sessionId: String, text: String): Result<Unit> = runCatching { app.api.sendSessionMessage(sessionId, text) }

    suspend fun stopStreamWithMessage(sessionId: String, text: String): Result<Unit> = runCatching {
        app.api.stopWithMessage(sessionId, text)
        delay(6000)
        app.repository.refresh(listOf(Catalogue.NOW_PLAYING, Catalogue.LIVE_SESSIONS))
    }

    val auth get() = app.auth
    val discover get() = app.discover

    private var seerrSource: String? = null

    /** Ratings for a library title from Seerr (IMDb, Rotten Tomatoes...), found by its TMDB id. */
    suspend fun seerrRatings(mediaType: String, tmdbId: String): Map<String, Double> {
        val id = tmdbId.toIntOrNull() ?: return emptyMap()
        val src = seerrSource ?: runCatching { app.discover.rows().first }.getOrNull()?.ifBlank { null }?.also { seerrSource = it } ?: return emptyMap()
        return runCatching { app.discover.detail(src, mediaType, id).ratings }.getOrDefault(emptyMap())
    }
    val library get() = app.library
    val admin get() = app.admin

    /** The title the media page shows; set before navigating there. */
    var selectedMedia: dev.nerdytech.jellyglance.data.MediaItem? = null
    var selectedUser: dev.nerdytech.jellyglance.data.UserRow? = null

    /** Refreshes the request list after a new request so it shows up straight away. */
    fun requestsChanged() = refreshAsync(listOf(route("requests"), Catalogue.FULL_REQUESTS))

    fun saveSession(baseUrl: String, s: dev.nerdytech.jellyglance.data.Auth.Session) = updateSettings {
        it.copy(baseUrl = baseUrl, sessionToken = s.token, sessionUser = s.user, sessionRole = s.role, sessionExp = s.exp, sessionExpired = false, sessionUserId = s.userId)
    }.also { syncTheme() }

    val me get() = app.me
    val insights get() = app.insights
    val stats get() = app.stats
    val extras get() = app.extras
    val jellyfin get() = app.jellyfin
    val requestAdmin get() = app.requestAdmin
    val parity get() = app.parity

    /** Text another app shared to us (a title or link to request). */
    var sharedText: String? = null

    val updater by lazy { dev.nerdytech.jellyglance.data.Updater(app) }
    private val _update = kotlinx.coroutines.flow.MutableStateFlow<dev.nerdytech.jellyglance.data.Updater.Release?>(null)
    /** A newer release on GitHub, once a check has found one. */
    val update: kotlinx.coroutines.flow.StateFlow<dev.nerdytech.jellyglance.data.Updater.Release?> = _update

    /** Checks GitHub now; [quiet] skips the check when the user turned automatic checks off. */
    suspend fun checkForUpdate(quiet: Boolean = false): dev.nerdytech.jellyglance.data.Updater.Check? {
        if (quiet && !settings.value.updateChecks) return null
        val r = updater.check()
        _update.value = (r as? dev.nerdytech.jellyglance.data.Updater.Check.Available)?.release
        return r
    }

    fun skipUpdate(version: String) = updateSettings { it.copy(skippedUpdate = version) }

    /** Remembers where Jellyfin lives (taken from an item's Jellyfin link) so it can be linked later. */
    fun rememberJellyfinUrl(itemUrl: String) {
        val base = dev.nerdytech.jellyglance.data.JellyfinRemote.baseFrom(itemUrl)
        if (base.startsWith("http") && settings.value.jellyfinUrl != base) updateSettings { it.copy(jellyfinUrl = base) }
    }

    /** Learns where Jellyfin lives from JellyGlance's config (for device icons and links). */
    fun discoverJellyfinUrl() = viewModelScope.launch {
        if (!settings.value.signedIn || settings.value.jellyfinUrl.isNotBlank()) return@launch
        val host = runCatching { (app.api.get("/api/getconfig") as? Map<*, *>)?.get("JF_HOST") as? String }.getOrNull()?.trim()?.trimEnd('/')
        if (host != null && host.startsWith("http")) updateSettings { if (it.jellyfinUrl.isBlank()) it.copy(jellyfinUrl = host) else it }
    }

    /** Adopts the user's JellyGlance web theme when it exists. */
    fun syncTheme() = viewModelScope.launch {
        runCatching { app.me.theme() }.getOrNull()?.let { colors ->
            val preset = dev.nerdytech.jellyglance.ui.theme.ThemePresets.all.firstOrNull {
                listOf(it.primary, it.secondary, it.background, it.surface) == colors
            }
            updateSettings { s -> if (preset != null) s.copy(themeName = preset.name, serverTheme = emptyList()) else s.copy(themeName = "Server", serverTheme = colors) }
        }
    }

    fun signOutAccount() = updateSettings { it.copy(sessionToken = "", sessionUser = "", sessionRole = "", sessionUserId = "", sessionExp = 0, sessionExpired = false) }

    fun signOut() {
        app.settings.clearServer()
        app.repository.clear()
    }
}

/** Routes the fixed dashboard sections read. */
val DASHBOARD_ROUTES = listOf("nowplaying", "health", "sessions", "watch", "catalog", "storage", "downloads", "requests", "issues", "calendar", "recent")
    .map { Catalogue.get(it)!!.route } + Catalogue.LIVE_SESSIONS + Catalogue.FULL_REQUESTS + Catalogue.FULL_CALENDAR + Catalogue.WEBHOOK_HISTORY

/** Refreshes [routes] now and then every [seconds] while the app is in the foreground. */
@Composable
fun PollEffect(routes: List<String>, seconds: Int, refresh: suspend (List<String>) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current = rememberUpdatedState(refresh)
    LaunchedEffect(routes, seconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                current.value(routes)
                delay(seconds.coerceAtLeast(5) * 1000L)
            }
        }
    }
}
