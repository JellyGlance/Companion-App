package dev.nerdytech.jellyglance.data

/*
 * The extra sections of the web dashboard's Home page (apps/web/src/pages/home.jsx): peak hours,
 * disk runway, hall of fame, the week's pulse, milestones, watch tonight and app health.
 * These routes need a signed-in session.
 */

data class PeakHours(val grid: List<List<Int>>, val max: Int, val total: Int, val days: Int, val busiest: Triple<Int, Int, Int>?, val quietest: Triple<Int, Int, Int>?)
data class Runway(
    val freeBytes: Double, val totalBytes: Double, val daysUntilFull: Int?, val range: List<Int>, val beyondYear: Boolean,
    val confidence: String, val bytesPerDay: Double, val status: String, val libraries: List<Pair<String, Double>>,
)
data class Famer(val userId: String, val name: String, val plays: Int, val watchSeconds: Double)
data class Tonight(val itemId: String, val name: String, val type: String, val imageId: String, val users: List<String>, val reasons: List<String>, val progress: Int)
data class Pulse(val topItem: String, val topPlays: Int, val topSeconds: Double, val mostActive: String, val mostActivePlays: Int, val quietUsers: List<String>)
data class Trend(val plays: Int, val users: Int, val seconds: Double)
data class Milestone(val title: String, val value: String, val detail: String, val progress: Float, val unlocked: Boolean)
data class AppHealth(val name: String, val ok: Boolean, val version: String, val message: String)
data class LastPlay(val user: String, val title: String, val series: String, val device: String, val client: String, val endedAt: String?)
data class Dashboard(
    val totalPlays: Int, val viewers: Int, val watchSeconds: Double, val catalogItems: Int, val catalogBytes: Double,
    val activeLibraries: Int, val hallOfFame: List<Famer>, val pulse: Pulse?, val today: Trend?, val lastWeek: Trend?,
    val libraryIssues: Int, val missingPosters: Int, val watchTonight: List<Tonight>, val balanceLabel: String, val concentration: Double,
)

class HomeInsights(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(k: String) = when (val v = this[k]) { is String -> v; is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString(); else -> "" }
    private fun Map<*, *>.n(k: String) = (this[k] as? Double) ?: 0.0
    private fun Map<*, *>.m(k: String) = this[k] as? Map<*, *> ?: emptyMap<String, Any?>()
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

    suspend fun dashboard(): Dashboard? {
        val m = api.get("/stats/getHomeDashboard") as? Map<*, *> ?: return null
        val totals = m.m("totals"); val cat = m.m("catalog"); val pulse = m.m("weekPulse"); val trends = m.m("trends"); val issues = m.m("libraryIssues")
        fun trend(t: Map<*, *>) = Trend(t.n("plays").toInt(), t.n("activeUsers").toInt(), t.n("watchSeconds"))
        return Dashboard(
            totals.n("totalPlaybacks").toInt(), totals.n("uniqueViewers").toInt(), totals.n("totalWatchSeconds"),
            (cat.n("movies") + cat.n("shows") + cat.n("artists")).toInt(), cat.n("size"), cat.n("activeLibraries").toInt(),
            maps(m["hallOfFame"]).map { Famer(it.s("userId"), it.s("userName"), it.n("plays").toInt(), it.n("watchSeconds")) },
            pulse.takeIf { it.isNotEmpty() }?.let { p ->
                Pulse(p.m("topItem").s("name"), p.m("topItem").n("plays").toInt(), p.m("topItem").n("watchSeconds"),
                    p.m("mostActiveViewer").s("userName"), p.m("mostActiveViewer").n("plays").toInt(), maps(p["quietUsers"]).map { it.s("userName") })
            },
            trends.m("today").takeIf { it.isNotEmpty() }?.let(::trend), trends.m("lastWeek").takeIf { it.isNotEmpty() }?.let(::trend),
            issues.values.sumOf { (it as? Double)?.toInt() ?: 0 }, issues.n("missingPosters").toInt(),
            maps(m["watchTonight"]).map {
                Tonight(it.s("itemId"), it.s("name"), it.s("type"), it.s("imageId").ifBlank { it.s("itemId") },
                    (it["users"] as? List<*>)?.map { u -> u.toString() } ?: emptyList(), (it["reasons"] as? List<*>)?.map { r -> r.toString() } ?: emptyList(),
                    it.n("progress").toInt())
            },
            m.m("libraryBalance").s("label"), m.m("libraryBalance").n("concentration"),
        )
    }

    suspend fun peakHours(): PeakHours? {
        val m = api.get("/stats/home/peakHours") as? Map<*, *> ?: return null
        val grid = (m["grid"] as? List<*>)?.map { row -> (row as? List<*>)?.map { (it as? Double)?.toInt() ?: 0 } ?: List(24) { 0 } } ?: return null
        fun cell(k: String) = m.m(k).takeIf { it.isNotEmpty() }?.let { Triple(it.n("day").toInt(), it.n("hour").toInt(), it.n("plays").toInt()) }
        return PeakHours(grid, m.n("max").toInt().coerceAtLeast(1), m.n("total").toInt(), m.n("days").toInt(), cell("busiest"), cell("quietest"))
    }

    suspend fun runway(): Runway? {
        val m = api.get("/stats/home/diskRunway") as? Map<*, *> ?: return null
        val f = m.m("forecast"); val fc = f.m("forecast")
        return Runway(
            f.n("freeBytes"), f.n("totalBytes"), (fc["daysUntilFull"] as? Double)?.toInt(),
            (fc["rangeDays"] as? List<*>)?.mapNotNull { (it as? Double)?.toInt() } ?: emptyList(), fc["beyondYear"] == true,
            fc.s("confidence"), fc.n("bytesPerDay"), fc.s("status"),
            maps(m["libraries"]).map { it.s("name") to it.n("bytes") },
        )
    }

    suspend fun lastPlayback(): LastPlay? {
        val m = api.get("/stats/home/lastPlayback") as? Map<*, *> ?: return null
        if (m.s("title").isBlank()) return null
        return LastPlay(m.s("userName"), m.s("title"), m.s("seriesName"), m.s("deviceName"), m.s("client"), m.s("endedAt").ifBlank { null })
    }

    suspend fun appHealth(): List<AppHealth> = maps((api.get("/api/home/appHealth") as? Map<*, *>)?.get("apps")).map {
        AppHealth(it.s("name"), it["ok"] == true, it.s("version"), it.s("message"))
    }

    suspend fun syncJellyfin() { api.get("/sync/beginSync", lenient = true) }

    companion object {
        private fun next(value: Double, steps: List<Int>) = steps.firstOrNull { value < it } ?: steps.last()

        /** Same milestones and thresholds as the web dashboard's buildHomeMilestones(). */
        fun milestones(d: Dashboard, requestWins: Int, healthOk: Boolean): List<Milestone> {
            val hours = d.watchSeconds / 3600
            val pt = next(d.totalPlays.toDouble(), listOf(10, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000))
            val ht = next(hours, listOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000))
            val ct = next(d.catalogItems.toDouble(), listOf(50, 100, 250, 500, 1000, 2500, 5000, 10000, 25000))
            val top = d.pulse?.topPlays ?: 0
            fun p(v: Double, t: Int) = (v / t).toFloat().coerceIn(0f, 1f)
            return listOf(
                Milestone(if (d.totalPlays >= 10000) "10K Play Club" else "Playback Climber", Fields.fmtNum(d.totalPlays.toDouble()),
                    "${Fields.fmtNum((pt - d.totalPlays).coerceAtLeast(0).toDouble())} plays to ${Fields.fmtNum(pt.toDouble())}", p(d.totalPlays.toDouble(), pt), d.totalPlays >= 10),
                Milestone(if (hours >= 1000) "Thousand-Hour Theatre" else "Time Collector", "${Fields.fmtNum(Math.floor(hours))} h",
                    "${Fields.fmtNum((ht - hours).coerceAtLeast(0.0).let(Math::ceil))} hours to ${Fields.fmtNum(ht.toDouble())}", p(hours, ht), hours >= 10),
                Milestone(if (d.viewers >= 5) "Full House" else "Household Spark", Fields.fmtNum(d.viewers.toDouble()),
                    if (d.viewers >= 5) "Five or more viewers have synced activity" else "${5 - d.viewers} more viewers to Full House", p(d.viewers.toDouble(), 5), d.viewers >= 5),
                Milestone(if (top >= 10) "Binge Beacon" else "Weekly Favourite", Fields.fmtNum(top.toDouble()),
                    "${d.pulse?.topItem?.ifBlank { null } ?: "Nothing"} leads this week", p(top.toDouble(), 10), top >= 10),
                Milestone(if (d.catalogItems >= 1000) "Vault Builder" else "Catalog Builder", Fields.fmtNum(d.catalogItems.toDouble()),
                    "${Fields.fmtNum((ct - d.catalogItems).coerceAtLeast(0).toDouble())} items to ${Fields.fmtNum(ct.toDouble())}", p(d.catalogItems.toDouble(), ct), d.catalogItems >= 50),
                Milestone(if (healthOk && d.libraryIssues == 0) "Clean Bill" else "Care Package", if (healthOk) "Healthy" else "Watch",
                    if (d.libraryIssues == 0) "No library issues" else "${Fields.fmtNum(d.libraryIssues.toDouble())} library issues tracked",
                    if (healthOk && d.libraryIssues == 0) 1f else if (healthOk) 0.65f else 0.28f, healthOk && d.libraryIssues == 0),
                Milestone(if (requestWins >= 25) "Request Hero" else "Request Ripple", Fields.fmtNum(requestWins.toDouble()),
                    "$requestWins requests approved or available", p(requestWins.toDouble(), 25), requestWins >= 25),
            )
        }
    }
}
