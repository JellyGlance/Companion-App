package dev.nerdytech.jellyglance.data

import kotlinx.coroutines.async
import org.json.JSONObject

/** The web dashboard's Statistics page (the /stats routes): rankings and time series for a window of days. */
data class Ranked(val id: String, val name: String, val value: Int, val seconds: Double = 0.0)
data class Series(val label: String, val count: Int, val minutes: Int)
data class Stats(
    val days: Int, val methods: Map<String, Int>, val libraries: List<Ranked>, val users: List<Ranked>, val clients: List<Ranked>,
    val viewedMovies: List<Ranked>, val viewedSeries: List<Ranked>, val popularMovies: List<Ranked>, val popularSeries: List<Ranked>,
    val daily: List<Series>, val weekdays: List<Series>, val hours: List<Series>, val byType: Map<String, Int>,
) {
    val totalPlays: Int get() = methods.values.sum()
}

class Statistics(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
    private fun Map<*, *>.n(k: String) = (this[k] as? Double) ?: (this[k] as? String)?.toDoubleOrNull() ?: 0.0
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

    private suspend fun post(route: String, body: JSONObject) = maps(api.post(route, body))

    /** Each row is {Key, <library>: {count, duration}}; add the libraries up. */
    private suspend fun series(route: String): List<Series> {
        val m = api.get(route) as? Map<*, *> ?: return emptyList()
        return maps(m["stats"]).map { row ->
            var c = 0; var d = 0
            row.forEach { (k, v) -> if (k != "Key" && v is Map<*, *>) { c += v.n("count").toInt(); d += v.n("duration").toInt() } }
            Series(row.s("Key"), c, d)
        }
    }

    suspend fun load(days: Int): Stats = kotlinx.coroutines.coroutineScope {
        val d = JSONObject().put("days", days)
        fun typed(t: String) = JSONObject().put("days", days).put("type", t)
        val methods = async { post("/stats/getPlaybackMethodStats", d).associate { it.s("Name") to it.n("Count").toInt() } }
        val libs = async { post("/stats/getMostViewedLibraries", d).map { Ranked(it.s("Id"), it.s("Name"), it.n("Plays").toInt()) } }
        val users = async { post("/stats/getMostActiveUsers", d).map { Ranked(it.s("UserId"), it.s("Name"), it.n("Plays").toInt()) } }
        val clients = async { post("/stats/getMostUsedClient", d).map { Ranked(it.s("Client"), it.s("Client"), it.n("Plays").toInt()) } }
        val vm = async { post("/stats/getMostViewedByType", typed("Movie")).map { Ranked(it.s("Id"), it.s("Name"), it.n("Plays").toInt(), it.n("total_playback_duration")) } }
        val vs = async { post("/stats/getMostViewedByType", typed("Series")).map { Ranked(it.s("Id"), it.s("Name"), it.n("Plays").toInt(), it.n("total_playback_duration")) } }
        val pm = async { post("/stats/getMostPopularByType", typed("Movie")).map { Ranked(it.s("Id"), it.s("Name"), it.n("unique_viewers").toInt()) } }
        val ps = async { post("/stats/getMostPopularByType", typed("Series")).map { Ranked(it.s("Id"), it.s("Name"), it.n("unique_viewers").toInt()) } }
        val daily = async { series("/stats/getViewsOverTime?days=$days") }
        val week = async { series("/stats/getViewsByDays?days=$days") }
        val hours = async { series("/stats/getViewsByHour?days=$days") }
        val types = async {
            (api.get("/stats/getViewsByLibraryType?days=$days") as? Map<*, *>)?.mapNotNull { (k, v) -> (v as? Double)?.toInt()?.takeIf { it > 0 }?.let { k.toString() to it } }?.toMap() ?: emptyMap()
        }
        Stats(
            days, methods.await(), libs.await(), users.await(), clients.await(), vm.await(), vs.await(), pm.await(), ps.await(),
            daily.await(), week.await(), hours.await(), types.await(),
        )
    }
}
