package dev.nerdytech.jellyglance.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject

/*
 * Libraries, items, users and play history (Jellystat-style routes in routes/api.js and
 * routes/stats.js). Responses are database rows with PascalCase columns, so every read is lenient.
 */

data class LibraryCard(
    val id: String, val name: String, val type: String, val items: Int, val seasons: Int, val episodes: Int,
    val playSeconds: Double, val lastPlayed: String, val lastActivity: String?,
    /** Items JellyGlance actually has stored for this library (the count above is Jellyfin's last report). */
    val synced: Int = items, val sizeLabel: String = "",
)

data class LibraryEntry(
    val id: String, val name: String, val type: String, val year: Int, val added: String?,
    val plays: Int, val playSeconds: Double, val size: Double, val rating: Double?,
)

data class Paged<T>(val items: List<T>, val pages: Int)

data class ItemStats(
    val name: String, val type: String, val year: Int, val library: String, val overview: String,
    val rating: Double?, val officialRating: String, val runtimeTicks: Long, val size: Double, val path: String,
    val plays: Int, val playSeconds: Double, val lastPlayed: String?, val genres: List<String>,
    val video: String, val audio: String,
    val criticRating: Double? = null, val providerIds: Map<String, String> = emptyMap(),
    val jellyfinUrl: String = "", val arrLinks: List<Pair<String, String>> = emptyList(),
) {
    /** Provider ids are keyed inconsistently (Imdb, IMDb, Tmdb, TheMovieDb...). */
    fun provider(vararg names: String): String? = names.firstNotNullOfOrNull { n -> providerIds.entries.firstOrNull { it.key.equals(n, true) }?.value?.ifBlank { null } }
    val imdb: String? get() = provider("Imdb")
    val tmdb: String? get() = provider("Tmdb", "TheMovieDb")
    val tvdb: String? get() = provider("Tvdb", "TheTVDB")
}

/** One season number; [ids] holds every copy JellyGlance has stored (a series re-added in Jellyfin leaves two). */
data class SeasonRow(val id: String, val name: String, val number: Int, val episodes: Int, val size: Double, val ids: List<String> = listOf(id))
data class EpisodeRow(val id: String, val name: String, val season: Int, val number: Int, val aired: String?, val runtimeTicks: Long, val size: Double)

data class HistoryRow(
    val user: String, val title: String, val series: String, val client: String, val device: String,
    val method: String, val seconds: Double, val at: String?,
    val itemId: String = "", val userId: String = "", val paused: Boolean = false,
) {
    val display: String get() = if (series.isNotBlank() && !title.startsWith(series)) "$series · $title" else title
}

data class UserRow(
    val id: String, val name: String, val admin: Boolean, val plays: Int, val watchSeconds: Double,
    val lastWatched: String, val lastClient: String, val lastSeen: String?,
)

class Library(private val api: JellyGlanceApi) {
    private fun Any?.m() = this as? Map<*, *> ?: emptyMap<String, Any?>()
    private fun Map<*, *>.s(vararg keys: String): String {
        for (k in keys) when (val v = Fields.getCI(this, k)) {
            is String -> if (v.isNotBlank()) return v
            is Double -> return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        }
        return ""
    }
    private fun Map<*, *>.n(vararg keys: String): Double {
        for (k in keys) when (val v = Fields.getCI(this, k)) {
            is Double -> return v
            is String -> v.toDoubleOrNull()?.let { return it }
        }
        return 0.0
    }
    private fun list(data: Any?) = (data as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

    /** Postgres intervals come back as {days, hours, minutes...}; turn them into an ISO time in the past. */
    private fun intervalAgo(v: Any?): String? {
        val m = v as? Map<*, *> ?: return null
        val secs = (m["days"] as? Double ?: 0.0) * 86400 + (m["hours"] as? Double ?: 0.0) * 3600 +
            (m["minutes"] as? Double ?: 0.0) * 60 + (m["seconds"] as? Double ?: 0.0)
        return java.time.Instant.now().minusSeconds(secs.toLong()).toString()
    }

    /**
     * Library cards with play stats, plus the stored item count and size from the storage widget.
     * The card's own count is Jellyfin's last report, which stays put for libraries that were later
     * removed, so the stored count decides what is really browsable.
     */
    suspend fun libraries(): List<LibraryCard> {
        val storage = runCatching {
            ((api.get("/api/widgets/libraries") as? Map<*, *>)?.get("libraries") as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.associateBy { it.s("id") } ?: emptyMap()
        }.getOrDefault(emptyMap())
        return list(api.get("/stats/getLibraryCardStats")).map {
            val id = it.s("Id")
            val st = storage[id]
            LibraryCard(
                id, it.s("Name"), it.s("CollectionType"), it.n("Library_Count").toInt(), it.n("Season_Count").toInt(),
                it.n("Episode_Count").toInt(), it.n("total_playback_duration"), it.s("ItemName"),
                intervalAgo(Fields.getCI(it, "LastActivity")),
                synced = st?.n("items")?.toInt() ?: it.n("Library_Count").toInt(),
                sizeLabel = st?.s("size")?.takeIf { s -> s != "0 B" }.orEmpty(),
            )
        }.filter { it.id.isNotBlank() }.sortedWith(compareByDescending<LibraryCard> { it.synced > 0 }.thenByDescending { it.synced })
    }

    /** Library contents per library id, kept for the session so sorting and search are instant. */
    private val cache = mutableMapOf<String, List<LibraryEntry>>()

    fun forget(libraryId: String) { cache.remove(libraryId) }

    /** Starts one of JellyGlance's server tasks (JellyfinSync, PartialJellyfinSync, RefreshDashboardStats, ...). */
    suspend fun startTask(task: String) { api.get("/api/startTask?task=" + java.net.URLEncoder.encode(task, "UTF-8"), lenient = true) }

    /**
     * The complete item list comes from /api/getLibraryItems. Play counts and sizes come from the
     * stats view behind /stats/getLibraryItemsWithStats, which is a materialized view that can lag
     * far behind the library, so it only adds numbers to items it knows about.
     */
    private suspend fun all(libraryId: String): List<LibraryEntry> = cache[libraryId] ?: run {
        val body = JSONObject().put("libraryid", libraryId)
        val base = list(api.post("/api/getLibraryItems", body)).filter { Fields.getCI(it, "archived") != true }
        val stats = runCatching {
            list((api.post("/stats/getLibraryItemsWithStats?size=100000&page=1", body) as? Map<*, *>)?.get("results")).associateBy { it.s("Id") }
        }.getOrDefault(emptyMap())
        base.map {
            val id = it.s("Id")
            val st = stats[id]
            LibraryEntry(
                id, it.s("Name"), it.s("Type"), it.n("ProductionYear").toInt(), it.s("DateCreated").ifBlank { null },
                st?.n("times_played")?.toInt() ?: 0, st?.n("total_play_time") ?: 0.0, st?.n("Size") ?: 0.0,
                it.n("CommunityRating").takeIf { r -> r > 0 },
            )
        }.also { cache[libraryId] = it }
    }

    suspend fun items(libraryId: String, page: Int, sort: String, desc: Boolean, search: String): Paged<LibraryEntry> {
        val needle = search.trim().lowercase()
        val article = Regex("^(the|a|an) ", RegexOption.IGNORE_CASE)
        val rows = all(libraryId).filter { needle.isEmpty() || it.name.lowercase().contains(needle) }.let { list ->
            val sorted = when (sort) {
                "Title" -> list.sortedBy { article.replace(it.name, "").lowercase() }
                "Views" -> list.sortedBy { it.plays }
                "WatchTime" -> list.sortedBy { it.playSeconds }
                "Size" -> list.sortedBy { it.size }
                else -> list.sortedBy { it.added ?: "" }
            }
            if (desc) sorted.reversed() else sorted
        }
        val size = 60
        return Paged(rows.drop((page - 1) * size).take(size), maxOf(1, (rows.size + size - 1) / size))
    }

    suspend fun item(id: String): ItemStats? {
        val row = list(api.post("/api/getItemDetails", JSONObject().put("Id", id))).firstOrNull() ?: return null
        val streams = (Fields.getCI(row, "MediaStreams") as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
        val video = streams.firstOrNull { it.s("Type") == "Video" }?.let { listOf(it.s("DisplayTitle"), it.s("Codec").uppercase()).firstOrNull { s -> s.isNotBlank() } }.orEmpty()
        val audio = streams.firstOrNull { it.s("Type") == "Audio" }?.s("DisplayTitle").orEmpty()
        return ItemStats(
            name = row.s("Name"), type = row.s("Type"), year = row.n("ProductionYear").toInt(), library = row.s("LibraryName"),
            overview = row.s("Overview"), rating = row.n("CommunityRating").takeIf { it > 0 }, officialRating = row.s("OfficialRating"),
            runtimeTicks = row.n("RunTimeTicks").toLong(), size = row.n("Size"), path = row.s("Path"),
            plays = row.n("times_played").toInt(), playSeconds = row.n("total_play_time"), lastPlayed = row.s("LastActivityDate").ifBlank { null },
            genres = (Fields.getCI(row, "Genres") as? List<*>)?.map { it.toString() } ?: emptyList(),
            video = video, audio = audio,
            criticRating = row.n("CriticRating").takeIf { it > 0 },
            providerIds = ((Fields.getCI(row, "ProviderIds") ?: Fields.getCI(row, "SeriesProviderIds")) as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (v as? String)?.let { k.toString() to it } }?.toMap() ?: emptyMap(),
            jellyfinUrl = row.s("JellyfinUrl"),
            arrLinks = (Fields.getCI(row, "ArrLinks") as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.mapNotNull { l -> l.s("url").ifBlank { null }?.let { u -> (if (Fields.getCI(l, "matched") == true) "Open in " else "Find in ") + l.s("name") to u } } ?: emptyList(),
        )
    }

    suspend fun seasons(seriesId: String): List<SeasonRow> = list(api.post("/api/getSeasons", JSONObject().put("Id", seriesId)))
        .filter { Fields.getCI(it, "archived") != true }
        .map { SeasonRow(it.s("Id"), it.s("Name"), it.n("IndexNumber").toInt(), it.n("Episodes").toInt(), it.n("Size")) }
        .groupBy { it.number }
        .map { (_, copies) ->
            val best = copies.maxBy { it.size }
            best.copy(episodes = copies.maxOf { it.episodes }, ids = copies.map { it.id })
        }
        .sortedBy { it.number }

    /** Episodes across every stored copy of a season, one row per episode. */
    suspend fun episodes(season: SeasonRow): List<EpisodeRow> = kotlinx.coroutines.coroutineScope {
        season.ids.map { id -> async { runCatching { episodes(id) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
            .distinctBy { listOf(it.number, it.name.lowercase()) }.sortedBy { it.number }
    }

    suspend fun episodes(seasonId: String): List<EpisodeRow> = list(api.post("/api/getEpisodes", JSONObject().put("Id", seasonId))).map {
        EpisodeRow(
            it.s("EpisodeId", "Id"), it.s("Name"), it.n("ParentIndexNumber").toInt(), it.n("IndexNumber").toInt(),
            it.s("PremiereDate").ifBlank { null }, it.n("RunTimeTicks").toLong(), it.n("Size"),
        )
    }.distinctBy { listOf(it.season, it.number, it.name.lowercase()) }.sortedBy { it.number }

    private fun history(r: Any?): Paged<HistoryRow> {
        val m = r.m()
        val rows = list(if (r is List<*>) r else m["results"]).map {
            HistoryRow(
                it.s("UserName"), it.s("NowPlayingItemName", "FullName"), it.s("SeriesName"), it.s("Client"), dev.nerdytech.jellyglance.ui.components.cleanDeviceName(it.s("DeviceName")),
                it.s("PlayMethod"), it.n("PlaybackDuration"), it.s("ActivityDateInserted").ifBlank { null },
                it.s("NowPlayingItemId"), it.s("UserId"), Fields.getCI(it, "IsPaused") == true,
            )
        }
        return Paged(rows, (m["pages"] as? Double)?.toInt() ?: 1)
    }

    /** Everyone's plays, newest first, as on the web Activity page; [search] matches titles and users. */
    suspend fun allHistory(page: Int, search: String = "") = history(
        api.get("/api/getHistory?size=30&page=$page" + (if (search.isNotBlank()) "&search=" + java.net.URLEncoder.encode(search, "UTF-8") else ""))
    )

    suspend fun itemHistory(id: String, page: Int = 1) = history(api.post("/api/getItemHistory?size=25&page=$page", JSONObject().put("itemid", id)))
    suspend fun userHistory(id: String, page: Int = 1) = history(api.post("/api/getUserHistory?size=25&page=$page", JSONObject().put("userid", id)))

    suspend fun users(): List<UserRow> = list(api.get("/stats/getAllUserActivity")).map {
        UserRow(
            it.s("UserId", "Id"), it.s("UserName", "Name"), Fields.getCI(it, "IsAdministrator") == true, it.n("TotalPlays").toInt(),
            it.n("TotalWatchTime"), it.s("LastWatched"), it.s("LastClient"), it.s("LastSeen", "LastActivityDate").ifBlank { null },
        )
    }.sortedByDescending { it.plays }

    fun userImage(baseUrl: String, userId: String) = "${baseUrl.trimEnd('/')}/proxy/Users/Images/Primary?id=${java.net.URLEncoder.encode(userId, "UTF-8")}&fillWidth=200&quality=80"
}
