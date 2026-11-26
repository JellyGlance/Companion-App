package dev.nerdytech.jellyglance.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Seerr search, discover and requesting through JellyGlance (the /requests routes in routes/api.js,
 * classes/request-discover.js). These routes need a signed-in session.
 */

data class MediaItem(
    val sourceId: String, val mediaId: Int, val mediaType: String, val title: String, val year: String,
    val overview: String, val posterUrl: String?, val backdropUrl: String?, val rating: Double?,
    val availability: String, val requested: Boolean, val jellyfinItemId: String?,
) {
    val inLibrary: Boolean get() = availability == "Available"
    val badge: String? get() = when (availability) {
        "Available" -> "In library"; "Partially available" -> "Partly in library"
        "Pending" -> "Requested"; "Processing" -> "Processing"; else -> if (requested) "Requested" else null
    }
}

data class DiscoverRow(val key: String, val title: String, val kind: String, val mediaType: String)
data class Chip(val id: String, val name: String)
data class RowPage(val items: List<MediaItem>, val chips: List<Chip>, val hasMore: Boolean)
data class CastMember(val name: String, val character: String, val imageUrl: String?)
data class Season(val number: Int, val title: String, val episodes: Int)

data class MediaDetail(
    val title: String, val year: String, val overview: String, val genres: List<String>, val runtime: Int,
    val ratings: Map<String, Double>, val cast: List<CastMember>, val availability: String, val jellyfinItemId: String?,
)

data class Quota(val enabled: Boolean, val unlimited: Boolean, val movie: Use?, val tv: Use?) {
    data class Use(val used: Int, val limit: Int?, val days: Int)
}

class Discover(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
    private fun Map<*, *>.d(k: String) = (this[k] as? Double) ?: (this[k] as? String)?.toDoubleOrNull()
    private fun Map<*, *>.maps(k: String) = (this[k] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

    private fun item(m: Map<*, *>) = MediaItem(
        sourceId = m.s("sourceId"), mediaId = m.d("mediaId")?.toInt() ?: 0, mediaType = m.s("mediaType"), title = m.s("title"),
        year = m.s("year"), overview = m.s("overview"), posterUrl = m.s("posterUrl").ifBlank { null },
        backdropUrl = m.s("backdropUrl").ifBlank { null }, rating = m.d("rating")?.takeIf { it > 0 },
        availability = m.s("availability"), requested = m["requested"] == true,
        jellyfinItemId = m.s("jellyfinItemId").ifBlank { null },
    )

    suspend fun search(query: String): List<MediaItem> =
        ((api.get("/api/requests/search?query=" + enc(query)) as? Map<*, *>)?.maps("results") ?: emptyList()).map(::item)

    /** Raw search hits keep TV season lists, which a TV request needs. */
    suspend fun seasons(title: String, mediaId: Int): List<Season> {
        val hit = ((api.get("/api/requests/search?query=" + enc(title)) as? Map<*, *>)?.maps("results") ?: emptyList())
            .firstOrNull { it.d("mediaId")?.toInt() == mediaId && it.s("mediaType") == "tv" } ?: return emptyList()
        return hit.maps("seasons").map { Season(it.d("seasonNumber")?.toInt() ?: 0, it.s("title"), it.d("episodeCount")?.toInt() ?: 0) }
            .filter { it.number > 0 }
    }

    suspend fun rows(): Pair<String, List<DiscoverRow>> {
        val m = api.get("/api/requests/discover") as? Map<*, *> ?: return "" to emptyList()
        return m.s("sourceId") to m.maps("rows").map { DiscoverRow(it.s("key"), it.s("title"), it.s("kind"), it.s("mediaType")) }
    }

    suspend fun row(sourceId: String, key: String, page: Int = 1, chipId: String? = null): RowPage {
        val q = "/api/requests/discover/row?sourceId=${enc(sourceId)}&key=${enc(key)}&page=$page" + (chipId?.let { "&chipId=" + enc(it) } ?: "")
        val m = api.get(q) as? Map<*, *> ?: return RowPage(emptyList(), emptyList(), false)
        return RowPage(m.maps("items").map(::item), m.maps("chips").map { Chip(it.s("id"), it.s("name")) }, m["hasMore"] == true)
    }

    suspend fun detail(sourceId: String, mediaType: String, mediaId: Int): MediaDetail {
        val m = api.get("/api/requests/media-detail?sourceId=${enc(sourceId)}&mediaType=$mediaType&mediaId=$mediaId") as? Map<*, *>
            ?: throw ApiException(ApiException.Kind.NOT_JSON)
        val ratings = (m["ratings"] as? Map<*, *>)?.mapNotNull { (k, v) -> (v as? Double)?.takeIf { it > 0 }?.let { k.toString() to it } }?.toMap() ?: emptyMap()
        val avail = m["availability"] as? Map<*, *>
        return MediaDetail(
            title = m.s("title"), year = m.s("year"), overview = m.s("overview"),
            genres = (m["genres"] as? List<*>)?.map { it.toString() } ?: emptyList(), runtime = m.d("runtime")?.toInt() ?: 0,
            ratings = ratings, cast = m.maps("cast").map { CastMember(it.s("name"), it.s("character"), it.s("imageUrl").ifBlank { null }) },
            availability = avail?.s("status").orEmpty(), jellyfinItemId = avail?.s("jellyfinItemId")?.ifBlank { null },
        )
    }

    suspend fun trailerKey(sourceId: String, mediaType: String, mediaId: Int): String? =
        ((api.get("/api/requests/trailer?sourceId=${enc(sourceId)}&mediaType=$mediaType&mediaId=$mediaId") as? Map<*, *>)?.get("trailer") as? Map<*, *>)
            ?.s("key")?.ifBlank { null }

    suspend fun quota(): Quota? {
        val m = api.get("/api/requests/quota") as? Map<*, *> ?: return null
        val u = m["usage"] as? Map<*, *> ?: emptyMap<String, Any?>()
        fun use(k: String) = (u[k] as? Map<*, *>)?.let { Quota.Use(it.d("used")?.toInt() ?: 0, it.d("limit")?.toInt(), it.d("days")?.toInt() ?: 0) }
        return Quota(m["enabled"] == true, u["unlimited"] == true, use("movie"), use("tv"))
    }

    /** Files a request in Seerr. Returns "approved", "pending" or "" when Seerr didn't say. */
    suspend fun request(item: MediaItem, seasons: List<Int>): String {
        val body = JSONObject().put("sourceId", item.sourceId).put("mediaType", item.mediaType).put("mediaId", item.mediaId)
        if (item.mediaType == "tv") body.put("seasons", JSONArray(seasons))
        val r = api.post("/api/requests/media", body) as? Map<*, *>
        val status = ((r?.get("request") as? Map<*, *>)?.get("status") as? Double)?.toInt()
        return when { r?.get("autoApproved") == true || status == 2 -> "approved"; status == 1 -> "pending"; else -> "" }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
