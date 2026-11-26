package dev.nerdytech.jellyglance.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Request management for a signed-in session, mirroring the web Requests queue: the full list with
 * pipeline state, per-request and bulk approve/decline/retry, editing where Seerr sends a request
 * (server, quality profile, root folder, tags, 4K, seasons) and the request rules (quotas).
 */

data class ManagedRequest(
    val key: String, val requestId: String, val sourceId: String, val source: String,
    val title: String, val mediaType: String, val status: String, val requestedBy: String, val requesterJellyfinId: String,
    val createdAt: String?, val year: String, val posterUrl: String?, val backdropUrl: String?, val overview: String,
    val genres: List<String>, val rating: Double?, val runtime: Int,
    val seasonCount: Int, val requestedSeasons: List<SeasonState>,
    val tmdbId: String, val tvdbId: String, val imdbId: String,
    val serverId: Int?, val profileId: Int?, val rootFolder: String, val languageProfileId: Int?, val is4k: Boolean, val tags: List<Int>,
    val availability: String, val availabilityNote: String, val jellyfinItemId: String?,
    val pipelineLabel: String, val timeline: List<Stage>, val openUrl: String,
) {
    data class SeasonState(val number: Int, val status: Int)
    data class Stage(val label: String, val state: String)

    val statusLabel: String get() = status.replaceFirstChar { it.uppercase() }
    val pending get() = status.equals("pending", true)
    val failed get() = status.equals("failed", true)
    val approved get() = status.equals("approved", true)
    val available get() = status.equals("available", true)
    val declined get() = status.equals("declined", true)
    val isTv get() = mediaType == "tv"
    /** Approved and waiting on Seerr / the *arrs: what the web counts as "in progress". */
    val inProgress get() = approved
}

data class RequestStats(
    val total: Int, val pending: Int, val approved: Int, val available: Int, val failed: Int,
    val topRequesters: List<Pair<String, Int>>, val movies: Int, val tv: Int,
)

data class RequestQueue(val requests: List<ManagedRequest>, val stats: RequestStats, val readOnly: Boolean, val sources: List<String>, val syncedAt: String?)

data class RequestOptions(val serviceType: String, val servers: List<Server>, val error: String = "") {
    data class Server(
        val id: Int, val name: String, val is4k: Boolean, val isDefault: Boolean,
        val activeDirectory: String, val activeProfileId: Int?, val activeLanguageProfileId: Int?, val activeTags: List<Int>,
        val profiles: List<Pair<Int, String>>, val rootFolders: List<Pair<String, String>>,
        val languageProfiles: List<Pair<Int, String>>, val tags: List<Pair<Int, String>>,
    )
}

data class RequestEdit(
    val serverId: Int?, val profileId: Int?, val rootFolder: String, val languageProfileId: Int?,
    val tags: List<Int>, val is4k: Boolean, val seasons: List<Int>,
)

data class RequestRules(
    val enabled: Boolean, val movieLimit: Int, val movieDays: Int, val tvLimit: Int, val tvDays: Int,
    val exemptAdmins: Boolean, val autoApproveAll: Boolean, val requestAsSeerrUser: Boolean,
    /** Per-person overrides keyed by Jellyfin user id. */
    val users: Map<String, UserRule>,
    /** Everything else (per-user overrides, routing) is kept as-is and sent back untouched. */
    val raw: JSONObject,
) {
    data class Usage(val user: String, val movies: Int, val tv: Int, val key: String = "")
    /** mode: default / custom / unlimited; autoApprove: default / always / never. */
    data class UserRule(val mode: String = "default", val autoApprove: String = "default", val movieLimit: Int? = null, val tvLimit: Int? = null)
}

data class BulkResult(val succeeded: Int, val failed: Int, val errors: List<String>)

class RequestAdmin(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String): String = when (val v = this[k]) {
        is String -> v
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        else -> ""
    }
    private fun Map<*, *>.i(k: String): Int? = (this[k] as? Double)?.toInt() ?: (this[k] as? String)?.toIntOrNull()
    private fun Map<*, *>.d(k: String): Double? = (this[k] as? Double) ?: (this[k] as? String)?.toDoubleOrNull()
    private fun Map<*, *>.m(k: String): Map<*, *> = this[k] as? Map<*, *> ?: emptyMap<Any, Any>()
    private fun Map<*, *>.maps(k: String) = (this[k] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun parse(r: Map<*, *>): ManagedRequest {
        val ext = r.m("externalIds")
        val avail = r.m("availability")
        return ManagedRequest(
            key = r.s("id").ifBlank { r.s("sourceId") + "-" + r.s("requestId") },
            requestId = r.s("requestId").ifBlank { r.s("id") }, sourceId = r.s("sourceId"), source = r.s("source"),
            title = r.s("title"), mediaType = r.s("mediaType"), status = r.s("status"),
            requestedBy = r.s("requestedBy"), requesterJellyfinId = r.m("requester").s("jellyfinUserId"),
            createdAt = r.s("createdAt").ifBlank { null }, year = r.s("year"),
            posterUrl = r.s("posterUrl").ifBlank { null }, backdropUrl = r.s("backdropUrl").ifBlank { null },
            overview = r.s("overview"), genres = (r["genres"] as? List<*>)?.map { it.toString() }.orEmpty(),
            rating = r.d("rating")?.takeIf { it > 0 }, runtime = r.i("runtime") ?: 0,
            seasonCount = r.i("seasons") ?: 0,
            requestedSeasons = r.maps("requestedSeasons").mapNotNull { s -> s.i("seasonNumber")?.let { ManagedRequest.SeasonState(it, s.i("status") ?: 0) } },
            tmdbId = ext.s("tmdbId"), tvdbId = ext.s("tvdbId"), imdbId = ext.s("imdbId"),
            serverId = r.i("serverId"), profileId = r.i("profileId"), rootFolder = r.s("rootFolder"),
            languageProfileId = r.i("languageProfileId"), is4k = r["is4k"] == true,
            tags = (r["tags"] as? List<*>)?.mapNotNull { (it as? Double)?.toInt() }.orEmpty(),
            availability = avail.s("status"), availabilityNote = avail.s("message"),
            jellyfinItemId = avail.s("jellyfinItemId").ifBlank { null },
            pipelineLabel = r.s("pipelineLabel"),
            timeline = r.maps("timeline").map { ManagedRequest.Stage(it.s("label"), it.s("state")) },
            openUrl = r.s("openUrl"),
        )
    }

    suspend fun queue(): RequestQueue {
        val m = api.get("/api/requests") as? Map<*, *> ?: emptyMap<Any, Any>()
        val st = m.m("stats")
        val types = st.m("mediaTypeCounts")
        return RequestQueue(
            requests = m.maps("requests").map(::parse),
            stats = RequestStats(
                st.i("total") ?: 0, st.i("pending") ?: 0, st.i("approved") ?: 0, st.i("available") ?: 0, st.i("failed") ?: 0,
                st.maps("topRequesters").map { it.s("name") to (it.i("count") ?: 0) }, types.i("movie") ?: 0, types.i("tv") ?: 0,
            ),
            readOnly = m["readOnly"] == true,
            sources = m.maps("sources").map { it.s("name") + if (it["connected"] == false) " (offline)" else "" },
            syncedAt = m.s("syncedAt").ifBlank { null },
        )
    }

    suspend fun detail(r: ManagedRequest): ManagedRequest =
        (api.get("/api/requests/${enc(r.requestId)}/detail?sourceId=${enc(r.sourceId)}") as? Map<*, *>)?.let(::parse) ?: r

    /** approve, decline or retry. A retry also asks the *arr to search again, like the web does. */
    suspend fun act(r: ManagedRequest, action: String) {
        if (action == "retry") runCatching {
            val grab = JSONObject().put("title", r.title).put("requestId", r.requestId).put("sourceId", r.sourceId).put("mediaType", r.mediaType)
            r.tmdbId.toIntOrNull()?.let { grab.put("tmdb", it) }
            r.tvdbId.toIntOrNull()?.let { grab.put("tvdb", it) }
            api.post("/api/retry-grab", grab)
        }
        api.post("/api/requests/${enc(r.requestId)}/actions", JSONObject().put("sourceId", r.sourceId).put("action", action))
    }

    /** Approve / decline straight from a notification, where only the ids are known. */
    suspend fun actById(requestId: String, sourceId: String, action: String) {
        api.post("/api/requests/${enc(requestId)}/actions", JSONObject().put("sourceId", sourceId).put("action", action))
    }

    suspend fun bulk(action: String, items: List<ManagedRequest>): BulkResult {
        val arr = JSONArray()
        items.forEach { r ->
            val o = JSONObject().put("requestId", r.requestId).put("sourceId", r.sourceId).put("title", r.title).put("mediaType", r.mediaType)
            r.tmdbId.toIntOrNull()?.let { o.put("tmdb", it) }
            r.tvdbId.toIntOrNull()?.let { o.put("tvdb", it) }
            arr.put(o)
        }
        val m = api.post("/api/requests/bulk", JSONObject().put("action", action).put("items", arr)) as? Map<*, *> ?: emptyMap<Any, Any>()
        val results = m.maps("results")
        val ok = m.i("succeeded") ?: results.count { it["ok"] == true || it["success"] == true }
        val bad = m.i("failed") ?: (results.size - ok).coerceAtLeast(0)
        val errors = results.filter { it["ok"] == false || it["success"] == false }
            .map { listOf(it.s("title"), it.s("error")).filter(String::isNotBlank).joinToString(": ") }
        return BulkResult(ok, bad, errors)
    }

    suspend fun options(r: ManagedRequest): RequestOptions {
        val m = api.get("/api/requests/options?sourceId=${enc(r.sourceId)}&mediaType=${enc(r.mediaType)}") as? Map<*, *>
            ?: return RequestOptions("", emptyList(), "No options returned")
        return RequestOptions(
            m.s("serviceType"),
            m.maps("servers").map { e ->
                val sv = e.m("server")
                RequestOptions.Server(
                    sv.i("id") ?: 0, sv.s("name"), sv["is4k"] == true, sv["isDefault"] == true,
                    sv.s("activeDirectory"), sv.i("activeProfileId"), sv.i("activeLanguageProfileId"),
                    (sv["activeTags"] as? List<*>)?.mapNotNull { (it as? Double)?.toInt() }.orEmpty(),
                    e.maps("profiles").map { (it.i("id") ?: 0) to it.s("name") },
                    e.maps("rootFolders").map { it.s("path") to it.s("freeSpaceLabel") },
                    e.maps("languageProfiles").map { (it.i("id") ?: 0) to it.s("name") },
                    e.maps("tags").map { (it.i("id") ?: 0) to it.s("label") },
                )
            },
            m.s("error"),
        )
    }

    suspend fun edit(r: ManagedRequest, e: RequestEdit) {
        val body = JSONObject().put("sourceId", r.sourceId).put("mediaType", r.mediaType)
            .put("serverId", e.serverId ?: JSONObject.NULL).put("profileId", e.profileId ?: JSONObject.NULL)
            .put("rootFolder", e.rootFolder).put("languageProfileId", e.languageProfileId ?: JSONObject.NULL)
            .put("tags", JSONArray(e.tags)).put("is4k", e.is4k).put("seasons", JSONArray(e.seasons.filter { it > 0 }))
        api.request("PUT", "/api/requests/${enc(r.requestId)}/edit", body)
    }

    private fun rulesFrom(m: Map<*, *>?): Pair<RequestRules, List<RequestRules.Usage>> {
        val r = m?.m("rules") ?: emptyMap<Any, Any>()
        @Suppress("UNCHECKED_CAST") val raw = JSONObject(r as Map<String, Any?>)
        return RequestRules(
            r["enabled"] == true, r.i("movieLimit") ?: 0, r.i("movieDays") ?: 7, r.i("tvLimit") ?: 0, r.i("tvDays") ?: 7,
            r["exemptAdmins"] == true, r["autoApproveAll"] == true, r["requestAsSeerrUser"] == true,
            r.m("users").entries.associate { (k, v) ->
                val u = v as? Map<*, *> ?: emptyMap<Any, Any>()
                k.toString() to RequestRules.UserRule(u.s("mode").ifBlank { "default" }, u.s("autoApprove").ifBlank { "default" }, u.i("movieLimit"), u.i("tvLimit"))
            },
            raw,
        ) to (m?.maps("usage") ?: emptyList()).map {
            RequestRules.Usage(it.s("user_name").ifBlank { it.s("name") }.ifBlank { it.s("user_key") }, it.i("movies") ?: it.i("movie") ?: 0, it.i("tv") ?: 0, it.s("user_key"))
        }
    }

    suspend fun rules() = rulesFrom(api.get("/api/requests/rules") as? Map<*, *>)

    suspend fun saveRules(r: RequestRules): Pair<RequestRules, List<RequestRules.Usage>> {
        val body = JSONObject(r.raw.toString())
            .put("enabled", r.enabled).put("movieLimit", r.movieLimit).put("movieDays", r.movieDays)
            .put("tvLimit", r.tvLimit).put("tvDays", r.tvDays).put("exemptAdmins", r.exemptAdmins)
            .put("autoApproveAll", r.autoApproveAll).put("requestAsSeerrUser", r.requestAsSeerrUser)
            .put("users", JSONObject().also { o ->
                r.users.forEach { (k, u) ->
                    val j = JSONObject().put("mode", u.mode).put("autoApprove", u.autoApprove)
                    u.movieLimit?.let { j.put("movieLimit", it) }; u.tvLimit?.let { j.put("tvLimit", it) }
                    o.put(k, j)
                }
            })
        return rulesFrom(api.request("PUT", "/api/requests/rules", body) as? Map<*, *>)
    }
}
