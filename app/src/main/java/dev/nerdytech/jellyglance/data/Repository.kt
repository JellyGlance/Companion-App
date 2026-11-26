package dev.nerdytech.jellyglance.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Latest response for one route, plus the recent numeric history used for trend lines. */
data class RouteState(
    val data: Any? = null,
    val flat: Flat? = null,
    val error: ApiException? = null,
    val updatedAt: Long = 0L,
    val loading: Boolean = false,
    val history: Map<String, List<Double>> = emptyMap(),
    /** Loaded from the on-disk copy, not fetched this session. */
    val cached: Boolean = false,
)

class Repository(private val api: JellyGlanceApi, private val cacheDir: java.io.File? = null) {
    private val _routes = MutableStateFlow<Map<String, RouteState>>(loadDisk())
    val routes: StateFlow<Map<String, RouteState>> = _routes.asStateFlow()

    /** Optional routes the key turned out not to be allowed to read; skipped until settings change. */
    private val blocked = mutableSetOf<String>()

    suspend fun refresh(routes: Collection<String>) = coroutineScope {
        routes.distinct().filter { it !in blocked }.map { async { refresh(it) } }.awaitAll()
        Unit
    }

    suspend fun refresh(route: String): RouteState {
        if (route in blocked) return _routes.value[route] ?: RouteState()
        _routes.update { it + (route to (it[route] ?: RouteState()).copy(loading = true)) }
        val next = try {
            val data = api.get(route)
            val flat = Fields.flatten(data)
            val prev = _routes.value[route]
            val hist = (prev?.history ?: emptyMap()).toMutableMap()
            flat.leaves.forEach { l -> l.num?.let { hist[l.path] = ((hist[l.path] ?: emptyList()) + it).takeLast(HISTORY) } }
            flat.lists.forEach { l -> hist[l.path] = ((hist[l.path] ?: emptyList()) + l.items.size.toDouble()).takeLast(HISTORY) }
            saveDisk(route, data)
            RouteState(data, flat, null, System.currentTimeMillis(), false, hist)
        } catch (e: ApiException) {
            if (route in OPTIONAL && e.kind in setOf(ApiException.Kind.AUTH, ApiException.Kind.FORBIDDEN, ApiException.Kind.MISSING, ApiException.Kind.NOT_JSON)) {
                blocked += route
                _routes.update { it - route }
                return RouteState(error = e)
            }
            (_routes.value[route] ?: RouteState()).copy(error = e, loading = false, updatedAt = System.currentTimeMillis())
        }
        _routes.update { it + (route to next) }
        return next
    }

    fun clear() {
        blocked.clear()
        _routes.value = emptyMap()
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    // Offline cache: the last good response per route, so the app shows something without a connection.
    private fun fileFor(route: String) = cacheDir?.let { java.io.File(it, route.hashCode().toUInt().toString(16) + ".json") }

    private fun saveDisk(route: String, data: Any?) {
        val f = fileFor(route) ?: return
        runCatching {
            cacheDir!!.mkdirs()
            f.writeText(org.json.JSONObject().put("route", route).put("data", org.json.JSONObject.wrap(data)).toString())
        }
    }

    private fun loadDisk(): Map<String, RouteState> = runCatching {
        cacheDir?.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching {
                val j = org.json.JSONObject(f.readText())
                val data = JellyGlanceApi.toKotlin(j.opt("data"))
                j.getString("route") to RouteState(data, Fields.flatten(data), null, f.lastModified(), false, cached = true)
            }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

    /** Live sessions when the key allows it, otherwise the Now Playing widget. */
    fun nowPlaying(routes: Map<String, RouteState>): NowPlaying? {
        routes[Catalogue.LIVE_SESSIONS]?.data?.let { Models.liveSessions(it) }?.let { return it }
        return routes[Catalogue.NOW_PLAYING]?.takeIf { it.flat != null }?.let { Models.nowPlaying(it.data) }
    }

    companion object {
        const val HISTORY = 30
        val OPTIONAL = setOf(Catalogue.LIVE_SESSIONS, Catalogue.FULL_REQUESTS, Catalogue.FULL_CALENDAR, Catalogue.WEBHOOK_HISTORY)
    }
}
