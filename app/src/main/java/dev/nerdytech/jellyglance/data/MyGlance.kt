package dev.nerdytech.jellyglance.data

import org.json.JSONObject

/*
 * The web dashboard's "My Glance" page (apps/web/src/pages/my-glance.jsx): the signed-in user's
 * Jellyfin lists, suggestions, achievements and goals. Actions write back to Jellyfin.
 */

data class MyItem(
    val id: String, val name: String, val type: String, val year: String, val seriesName: String,
    val season: Int?, val episode: Int?, val progress: Int, val imageId: String, val favourite: Boolean,
    val played: Boolean, val note: String = "",
) {
    val title: String get() = if (type == "Episode" && seriesName.isNotBlank()) seriesName else name
    val subtitle: String get() = when {
        type == "Episode" && season != null && episode != null -> "S$season:E$episode · $name"
        else -> listOf(type, year).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

data class MyLists(
    val continueWatching: List<MyItem>, val nextUp: List<MyItem>, val watchlist: List<MyItem>, val favourites: List<MyItem>,
    val recentlyWatched: List<MyItem>, val staleWatchlist: List<MyItem>, val tasteGenres: List<String>,
)

data class Because(val seed: String, val items: List<MyItem>)
data class ForYou(val picks: List<MyItem>, val because: List<Because>)

data class Badge(val name: String, val description: String, val unit: String, val value: Int, val tier: String?, val tierIndex: Int, val maxTier: Int, val next: Int?, val progress: Int)
data class Goal(val key: String, val target: Int?, val done: Int, val percent: Int?, val status: String)
data class Achievements(val year: Int, val earned: Int, val total: Int, val badges: List<Badge>, val goals: List<Goal>, val movies: Int, val episodes: Int, val hours: Int)

class MyGlance(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
    private fun Map<*, *>.i(k: String) = (this[k] as? Double)?.toInt()
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun item(m: Map<*, *>) = MyItem(
        id = m.s("id").ifBlank { m.s("Id") }, name = m.s("name").ifBlank { m.s("Name") }, type = m.s("type").ifBlank { m.s("Type") },
        year = m.s("year").ifBlank { m.s("ProductionYear") }, seriesName = m.s("seriesName"),
        season = m.i("seasonNumber"), episode = m.i("episodeNumber"), progress = m.i("progress") ?: 0,
        imageId = m.s("imageId").ifBlank { m.s("id").ifBlank { m.s("Id") } }, favourite = m["favourite"] == true, played = m["played"] == true,
        note = m.s("reason"),
    )

    suspend fun lists(userId: String): MyLists {
        val m = api.get("/api/users/${enc(userId)}/media-lists") as? Map<*, *> ?: throw ApiException(ApiException.Kind.NOT_JSON)
        val taste = ((m["taste"] as? Map<*, *>)?.get("genres") as? List<*>)?.mapNotNull {
            when (it) { is Map<*, *> -> it.s("name").ifBlank { it.s("value") }.ifBlank { it.s("label") }; is String -> it; else -> null }
        }?.filter { it.isNotBlank() } ?: emptyList()
        return MyLists(
            maps(m["continueWatching"] ?: m["resumable"]).map(::item), maps(m["nextEpisodes"]).map(::item),
            maps(m["watchlist"]).map(::item), maps(m["favourites"]).map(::item), maps(m["recentlyWatched"]).map(::item),
            maps(m["staleWatchlist"]).map(::item), taste.take(8),
        )
    }

    suspend fun forYou(userId: String): ForYou {
        val m = api.get("/insights-data/for-you?userId=" + enc(userId)) as? Map<*, *> ?: return ForYou(emptyList(), emptyList())
        return ForYou(
            maps(m["picks"]).map(::item),
            maps(m["because"]).map { b -> Because((b["seed"] as? Map<*, *>)?.s("name").orEmpty(), maps(b["items"]).map(::item)) }.filter { it.items.isNotEmpty() },
        )
    }

    suspend fun achievements(userId: String, year: Int): Achievements? {
        val m = api.get("/insights-data/achievements?userId=${enc(userId)}&year=$year") as? Map<*, *> ?: return null
        val summary = m["summary"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return Achievements(
            year, m.i("earned") ?: 0, m.i("total") ?: 0,
            maps(m["badges"]).map {
                Badge(it.s("name"), it.s("description"), it.s("unit"), it.i("value") ?: 0, it.s("tier").ifBlank { null },
                    it.i("tierIndex") ?: -1, it.i("maxTier") ?: 0, it.i("next"), it.i("progress") ?: 0)
            },
            maps(m["goals"]).map { Goal(it.s("key"), it.i("target"), it.i("done") ?: 0, it.i("percent"), it.s("status")) },
            summary.i("movies") ?: 0, summary.i("episodes") ?: 0, summary.i("hours") ?: 0,
        )
    }

    suspend fun saveGoals(year: Int, movies: Int?, episodes: Int?, hours: Int?) {
        val body = JSONObject().put("year", year)
        movies?.let { body.put("movies", it) }; episodes?.let { body.put("episodes", it) }; hours?.let { body.put("hours", it) }
        api.request("PUT", "/insights-data/achievements/goals", body)
    }

    /** addWatchlist, removeWatchlist, favourite, unfavourite, markWatched, markUnwatched. */
    suspend fun action(userId: String, itemId: String, action: String) {
        api.post("/api/users/${enc(userId)}/media/${enc(itemId)}/actions", JSONObject().put("action", action))
    }

    /** The user's JellyGlance theme (the web dashboard's palette), if they have saved one. */
    suspend fun theme(): List<Long>? {
        val t = (api.get("/api/preferences") as? Map<*, *>)?.get("theme") as? Map<*, *> ?: return null
        return listOf("primary", "secondary", "background", "surface").map { k ->
            val hex = t.s(k).removePrefix("#")
            if (hex.length != 6) return null
            0xFF000000 or hex.toLong(16)
        }
    }
}
