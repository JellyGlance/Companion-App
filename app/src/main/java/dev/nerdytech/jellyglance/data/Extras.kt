package dev.nerdytech.jellyglance.data

import org.json.JSONObject

/* Tdarr (Active Transcodes), Recently Added shelves and the activity Timeline from the web dashboard. */

data class TdarrJob(
    val id: String, val title: String, val nodeId: String, val workKind: String, val status: String, val from: String, val to: String,
    val progress: Int, val sizeBefore: Double, val savedBytes: Double, val savedPercent: Double, val reason: String, val imageId: String,
)
data class TdarrNode(val id: String, val name: String, val paused: Boolean)
data class Tdarr(
    val connected: Boolean, val version: String, val active: List<TdarrJob>, val queued: List<TdarrJob>, val history: List<TdarrJob>,
    val nodes: List<TdarrNode>, val processed: Int, val errored: Int, val queue: Int, val savedBytes: Double,
)
data class Shelf(val id: String, val name: String, val items: List<ShelfItem>)
data class ShelfItem(val id: String, val name: String, val series: String, val type: String, val added: String?, val imageId: String)
data class TimelineRow(val title: String, val season: String, val episodes: Int, val first: String?, val last: String?, val seconds: Double, val type: String, val itemId: String)

data class ServerUpdate(val current: String, val latest: String, val available: Boolean, val stars: Int, val releasesUrl: String)

class Extras(private val api: JellyGlanceApi) {
    /** JellyGlance's own version check (also gives the GitHub star count). */
    suspend fun updates(): ServerUpdate? {
        val m = api.get("/api/CheckForUpdates") as? Map<*, *> ?: return null
        return ServerUpdate(
            (m["current_version"] as? String).orEmpty(), (m["latest_version"] as? String).orEmpty(),
            m["update_available"] == true, (m["stars"] as? Double)?.toInt() ?: 0,
            (m["releases_url"] as? String).orEmpty().ifBlank { "https://github.com/JellyGlance/Server/releases" },
        )
    }

    private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> if (v == "undefined") "" else v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
    private fun Map<*, *>.n(k: String) = (this[k] as? Double) ?: 0.0
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

    private fun job(m: Map<*, *>) = TdarrJob(
        m.s("fileId").ifBlank { m.s("id") }, m.s("title"), m.s("nodeId"), m.s("workKind"), m.s("status"), m.s("from"), m.s("to"),
        m.n("progress").toInt(), m.n("sizeBefore"), m.n("savedBytes"), m.n("savedPercent"), m.s("reason"), m.s("imageId").ifBlank { m.s("itemId") },
    )

    suspend fun tdarr(): Tdarr? {
        val m = api.get("/api/tdarr/transcodes") as? Map<*, *> ?: return null
        val st = m["stats"] as? Map<*, *> ?: emptyMap<String, Any?>()
        return Tdarr(
            m["connected"] == true, (m["source"] as? Map<*, *>)?.s("version").orEmpty(),
            maps(m["active"]).map(::job), maps(m["queued"]).map(::job), maps(m["history"]).map(::job),
            maps(m["nodes"]).map { TdarrNode(it.s("id"), it.s("name"), it["paused"] == true) },
            st.n("processed").toInt(), st.n("errored").toInt(), st.n("queue").toInt(), st.n("saved"),
        )
    }

    /** action: pause, resume (with nodeId) or skip (with fileId, and nodeId when running). */
    suspend fun tdarrAction(action: String, nodeId: String = "", fileId: String = "", workKind: String = "") {
        val body = JSONObject().put("action", action)
        if (nodeId.isNotBlank()) body.put("nodeId", nodeId)
        if (fileId.isNotBlank()) body.put("fileId", fileId)
        if (workKind.isNotBlank()) body.put("workKind", workKind)
        api.post("/api/tdarr/actions", body)
    }

    suspend fun shelves(): List<Shelf> = maps(api.get("/api/getRecentlyAddedShelves?limit=20")).map { s ->
        Shelf(s.s("id"), s.s("name"), maps(s["items"]).map {
            ShelfItem(it.s("Id"), it.s("Name"), it.s("SeriesName"), it.s("Type"), it.s("DateCreated").ifBlank { null }, it.s("SeriesId").ifBlank { it.s("Id") })
        }.distinctBy { it.id })
    }.filter { it.items.isNotEmpty() }

    suspend fun timelineUsers(): List<Pair<String, String>> = maps(api.get("/api/getTimelineUsers")).map { it.s("UserId") to it.s("UserName") }

    suspend fun timeline(userId: String): List<TimelineRow> {
        val r = api.post("/api/getActivityTimeLine", JSONObject().put("userId", userId))
        val rows = maps(if (r is List<*>) r else (r as? Map<*, *>)?.get("results"))
        return rows.map {
            TimelineRow(it.s("Title"), it.s("SeasonName"), it.n("EpisodeCount").toInt(), it.s("FirstActivityDate").ifBlank { null },
                it.s("LastActivityDate").ifBlank { null }, it.n("TotalPlaybackDuration"), it.s("MediaType"), it.s("NowPlayingItemId"))
        }.sortedByDescending { it.last ?: "" }
    }
}
