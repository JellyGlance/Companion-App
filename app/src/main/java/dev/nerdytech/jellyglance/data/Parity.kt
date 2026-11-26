package dev.nerdytech.jellyglance.data

import org.json.JSONObject

/*
 * The rest of the web dashboard's admin and statistics pages: Repair hub, Seerr issues,
 * Jellyfin scheduled tasks, API keys, task logs, activity-monitor intervals, tracked libraries,
 * and the Statistics tabs (Recap, Taste map, Up next, Library health, Streams, Devices).
 */

data class RepairHub(val counts: Map<String, Int>, val samples: Map<String, List<Map<*, *>>>, val checkedAt: String?, val unmatched: List<Map<*, *>>)
data class SeerrIssue(
    val id: String, val sourceId: String, val source: String, val type: String, val status: String, val title: String,
    val mediaType: String, val season: Int, val episode: Int, val message: String, val posterUrl: String?,
    val comments: List<Pair<String, String>>, val createdBy: String, val createdAt: String?, val openUrl: String,
) { val open get() = !status.equals("resolved", true) }
data class JellyfinJob(val id: String, val name: String, val about: String, val category: String, val state: String, val lastStatus: String, val lastEnd: String?, val everyHours: Double?)
data class ApiKeyRow(val name: String, val key: String, val scope: String, val lastUsed: String?)
data class TaskLog(val id: String, val name: String, val type: String, val trigger: String, val result: String, val seconds: Int, val ranAt: String?, val lines: List<Pair<String, String>>)
data class TrackedLibrary(val id: String, val name: String, val type: String, val tracked: Boolean)

class Parity(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String): String = when (val v = this[k]) {
        is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); is Boolean -> v.toString(); else -> ""
    }
    private fun Map<*, *>.n(k: String): Double = (this[k] as? Double) ?: (this[k] as? String)?.toDoubleOrNull() ?: 0.0
    private fun Map<*, *>.m(k: String): Map<*, *> = this[k] as? Map<*, *> ?: emptyMap<Any, Any>()
    private fun Map<*, *>.maps(k: String) = (this[k] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // --- Repair hub ---
    suspend fun repair(): RepairHub {
        val m = api.get("/stats/repair-hub") as? Map<*, *> ?: emptyMap<Any, Any>()
        val unmatched = runCatching { maps((api.get("/tautulli/unmatched?limit=6") as? Map<*, *>)?.let { it["items"] ?: it["rows"] ?: it["unmatched"] } ?: api.get("/tautulli/unmatched?limit=6")) }.getOrDefault(emptyList())
        return RepairHub(
            m.m("counts").entries.associate { (k, v) -> k.toString() to ((v as? Double)?.toInt() ?: 0) },
            m.m("samples").entries.associate { (k, v) -> k.toString() to maps(v) },
            m.s("checkedAt").ifBlank { null }, unmatched,
        )
    }

    suspend fun scanLibraries() { api.post("/api/jellyfin/refresh-library", JSONObject()) }

    // --- Seerr issues ---
    suspend fun issues(): List<SeerrIssue> = (api.get("/api/requests/issues") as? Map<*, *>)?.maps("issues").orEmpty().map {
        SeerrIssue(
            it.s("id"), it.s("sourceId"), it.s("source"), it.s("issueType"), it.s("status"), it.s("title"), it.s("mediaType"),
            it.n("season").toInt(), it.n("episode").toInt(), it.s("message"), it.s("posterUrl").ifBlank { null },
            it.maps("comments").map { c -> c.s("user") to c.s("message") }, it.s("createdBy"), it.s("createdAt").ifBlank { null }, it.s("openUrl"),
        )
    }

    /** action: resolve, reopen, comment (with [comment]) or delete. */
    suspend fun issueAction(i: SeerrIssue, action: String, comment: String? = null) {
        val body = JSONObject().put("sourceId", i.sourceId).put("action", action)
        if (comment != null) body.put("comment", comment)
        api.post("/api/requests/issues/${enc(i.id)}/actions", body)
    }

    // --- Jellyfin scheduled tasks ---
    suspend fun jellyfinJobs(): List<JellyfinJob> = (api.get("/api/server-management/status") as? Map<*, *>)?.maps("jellyfinTasks").orEmpty().map {
        val last = it.m("lastExecutionResult")
        val every = it.maps("triggers").firstOrNull { t -> t.s("Type") == "IntervalTrigger" }?.n("IntervalTicks")?.takeIf { t -> t > 0 }?.let { t -> t / 36_000_000_000.0 }
        JellyfinJob(it.s("id"), it.s("name"), it.s("description"), it.s("category"), it.s("state"), last.s("Status"), last.s("EndTimeUtc").ifBlank { null }, every)
    }.sortedWith(compareBy({ it.category }, { it.name }))

    suspend fun runJellyfinJob(id: String) {
        api.post("/api/server-management/action", JSONObject().put("action", "runJellyfinTask").put("taskId", id))
    }

    // --- API keys ---
    suspend fun apiKeys(): List<ApiKeyRow> = maps(api.get("/api/keys")).map { ApiKeyRow(it.s("name"), it.s("key"), it.s("scope"), it.s("lastUsed").ifBlank { null }) }

    /** Creates a key; returns the list the server sends back (with the new key in it). */
    suspend fun createApiKey(name: String, scope: String): List<ApiKeyRow> =
        maps(api.post("/api/keys", JSONObject().put("name", name).put("scope", scope))).map { ApiKeyRow(it.s("name"), it.s("key"), it.s("scope"), it.s("lastUsed").ifBlank { null }) }

    suspend fun deleteApiKey(key: String) { api.request("DELETE", "/api/keys", JSONObject().put("key", key)) }

    // --- Task logs ---
    suspend fun logs(): List<TaskLog> = maps(api.get("/logs/getLogs")).map {
        TaskLog(
            it.s("Id"), it.s("Name"), it.s("Type"), it.s("ExecutionType"), it.s("Result"), it.n("Duration").toInt(), it.s("TimeRun").ifBlank { null },
            it.maps("Log").map { l -> l.s("color") to l.s("Message") },
        )
    }

    // --- Activity monitor ---
    suspend fun monitor(): Pair<Int, Int> = (api.get("/api/getActivityMonitorSettings") as? Map<*, *>).let { (it?.n("activeSessionsInterval")?.toInt() ?: 0) to (it?.n("idleInterval")?.toInt() ?: 0) }

    suspend fun setMonitor(active: Int, idle: Int) {
        api.post("/api/setActivityMonitorSettings", JSONObject().put("activeSessionsInterval", active).put("idleInterval", idle))
    }

    // --- Tracked libraries ---
    suspend fun libraries(): List<TrackedLibrary> = maps(api.get("/api/TrackedLibraries")).map {
        TrackedLibrary(it.s("Id"), it.s("Name"), it.s("CollectionType").ifBlank { it.s("Type") }, it["Tracked"] != false && it["Excluded"] != true)
    }

    /** JellyGlance toggles a library in or out of tracking with this one call. */
    suspend fun toggleLibrary(id: String) { api.post("/api/setExcludedLibraries", JSONObject().put("libraryID", id)) }

    // --- Statistics tabs ---
    suspend fun insights(path: String): Map<*, *> = api.get("/insights-data/$path") as? Map<*, *> ?: emptyMap<Any, Any>()

    /** People JellyGlance knows (for per-person request rules). */
    suspend fun people(): List<Pair<String, String>> =
        (api.get("/insights-data/wrapped/years") as? Map<*, *>)?.maps("users").orEmpty().map { it.s("id") to it.s("name") }
}
