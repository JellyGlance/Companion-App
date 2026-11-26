package dev.nerdytech.jellyglance.data

/*
 * Typed views of the JellyGlance widget responses (apps/api/classes/command-center.js in the
 * JellyGlance server). Every reader is lenient: a missing field becomes 0, "" or null, so an
 * older or newer server degrades to empty values instead of crashing.
 */

private fun Map<*, *>.str(key: String): String = when (val v = this[key]) {
    is String -> v
    is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    is Boolean -> v.toString()
    else -> ""
}

private fun Map<*, *>.num(key: String): Double = when (val v = this[key]) {
    is Double -> v
    is String -> v.toDoubleOrNull() ?: 0.0
    else -> 0.0
}

private fun Map<*, *>.int(key: String): Int = num(key).toInt()
private fun Map<*, *>.bool(key: String): Boolean = this[key] == true
private fun Map<*, *>.obj(key: String): Map<*, *> = this[key] as? Map<*, *> ?: emptyMap<String, Any?>()
private fun Map<*, *>.maps(key: String): List<Map<*, *>> = (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
private fun Any?.asMap(): Map<*, *> = this as? Map<*, *> ?: emptyMap<String, Any?>()

data class Stream(
    val sessionId: String,
    val viewer: String,
    val title: String,
    val subtitle: String = "",
    val client: String,
    val device: String,
    val method: String,
    val itemId: String? = null,
    val posterId: String? = null,
    val userId: String? = null,
    val progress: Float? = null,
    val positionTicks: Long = 0,
    val runtimeTicks: Long = 0,
    val paused: Boolean = false,
    val transcodeReason: String = "",
    val resolution: String = "",
    /** From Jellyfin's session: null when unknown (widget data only). */
    val remoteControl: Boolean? = null,
    val mediaControl: Boolean? = null,
    val commands: List<String> = emptyList(),
) {
    /** Jellyfin can pop a message up on this client. */
    val canMessage: Boolean? get() = remoteControl?.let { it && (commands.isEmpty() || "DisplayMessage" in commands) }
    val transcoding: Boolean get() = method.contains("transcode", ignoreCase = true)
    val methodLabel: String get() = when {
        transcoding -> "Transcode"
        method.contains("directstream", true) -> "Direct stream"
        method.contains("direct", true) -> "Direct play"
        else -> method
    }
}

data class NowPlaying(val playing: Int, val transcoding: Int, val streams: List<Stream>, val live: Boolean)

object Models {
    fun nowPlaying(data: Any?): NowPlaying {
        val m = data.asMap()
        val items = m.maps("items").map {
            Stream(
                sessionId = it.str("sessionId"), viewer = it.str("viewer"), title = it.str("title"),
                client = it.str("client"), device = dev.nerdytech.jellyglance.ui.components.cleanDeviceName(it.str("device")), method = it.str("method"),
            )
        }
        return NowPlaying(m.int("playing").coerceAtLeast(items.size), m.int("transcoding"), items, live = false)
    }

    /** Raw Jellyfin sessions from /proxy/getSessions (full-scope keys only): progress, posters, pause state. */
    fun liveSessions(data: Any?): NowPlaying? {
        val list = (data as? List<*>)?.filterIsInstance<Map<*, *>>() ?: return null
        val streams = list.filter { it["NowPlayingItem"] is Map<*, *> }.map { s ->
            val item = s.obj("NowPlayingItem")
            val play = s.obj("PlayState")
            val trans = s.obj("TranscodingInfo")
            val episode = item.str("Type") == "Episode" && item.str("SeriesName").isNotBlank()
            val pos = play.num("PositionTicks").toLong()
            val run = item.num("RunTimeTicks").toLong()
            val method = play.str("PlayMethod").ifBlank { s.str("PlayMethod") }
            val sub = when {
                episode -> listOfNotNull(
                    item.int("ParentIndexNumber").takeIf { it > 0 }?.let { "S$it" },
                    item.int("IndexNumber").takeIf { it > 0 }?.let { "E$it" },
                ).joinToString("") + " · " + item.str("Name")
                item.int("ProductionYear") > 0 -> item.int("ProductionYear").toString()
                else -> ""
            }.trim(' ', '·')
            val height = (item.maps("MediaStreams").firstOrNull { it.str("Type") == "Video" }?.int("Height")) ?: 0
            Stream(
                sessionId = s.str("Id"),
                viewer = s.str("UserName").ifBlank { "User" },
                title = if (episode) item.str("SeriesName") else item.str("Name"),
                subtitle = sub,
                client = s.str("Client"),
                device = dev.nerdytech.jellyglance.ui.components.cleanDeviceName(s.str("DeviceName")),
                method = method,
                itemId = item.str("Id").ifBlank { null },
                posterId = (if (episode) item.str("SeriesId") else item.str("Id")).ifBlank { null },
                userId = s.str("UserId").ifBlank { null },
                progress = if (run > 0) (pos.toFloat() / run).coerceIn(0f, 1f) else null,
                positionTicks = pos,
                runtimeTicks = run,
                paused = play.bool("IsPaused"),
                transcodeReason = (trans["TranscodeReasons"] as? List<*>)?.joinToString(", ") { r -> Fields.humanize(r.toString()) } ?: "",
                resolution = when {
                    height >= 2000 -> "4K"; height >= 1000 -> "1080p"; height >= 700 -> "720p"; height > 0 -> "${height}p"; else -> ""
                },
                remoteControl = s["SupportsRemoteControl"] as? Boolean,
                mediaControl = s["SupportsMediaControl"] as? Boolean,
                commands = ((s["Capabilities"] as? Map<*, *>)?.get("SupportedCommands") as? List<*>)?.map { it.toString() }.orEmpty(),
            )
        }
        return NowPlaying(streams.size, streams.count { it.transcoding }, streams, live = true)
    }

    data class Playback(val recent: Int, val today: Int, val last24h: Int, val viewersToday: Int, val users: Int)
    fun playback(data: Any?) = data.asMap().let { Playback(it.int("recent"), it.int("today"), it.int("last24h"), it.int("viewersToday"), it.int("users")) }

    data class Watch(val secondsToday: Double, val secondsWeek: Double, val secondsAll: Double)
    fun watch(data: Any?) = data.asMap().let { Watch(it.num("secondsToday"), it.num("secondsWeek"), it.num("secondsAll")) }

    data class Catalog(val movies: Int, val shows: Int, val episodes: Int, val libraries: Int, val addedWeek: Int)
    fun catalog(data: Any?) = data.asMap().let { Catalog(it.int("movies"), it.int("shows"), it.int("episodes"), it.int("libraries"), it.int("addedWeek")) }

    data class Storage(val label: String, val bytes: Double, val upcomingCount: Int, val upcomingEstimate: String)
    fun storage(data: Any?) = data.asMap().let { Storage(it.str("totalLabel"), it.num("totalBytes"), it.int("upcomingCount"), it.str("upcomingEstimate")) }

    data class Download(val id: String, val name: String, val state: String, val progress: Float, val stalled: Boolean) {
        val paused: Boolean get() = state.contains("pause", true)
    }
    data class Downloads(val active: Int, val stalled: Int, val total: Int, val items: List<Download>)
    fun downloads(data: Any?) = data.asMap().let { m ->
        Downloads(
            m.int("active"), m.int("stalled"), m.int("total"),
            m.maps("items").map { Download(it.str("id"), it.str("name"), it.str("state"), (it.num("progress") / 100.0).toFloat().coerceIn(0f, 1f), it.bool("stalled")) },
        )
    }

    data class Request(
        val id: String, val sourceId: String, val title: String, val status: String, val source: String, val requestedBy: String,
        val posterUrl: String? = null, val mediaType: String = "", val createdAt: String? = null,
        val backdropUrl: String? = null, val overview: String = "", val genres: List<String> = emptyList(),
        val rating: Double? = null, val runtime: Int = 0, val jellyfinItemId: String? = null,
    ) {
        /** The widget falls back to the word "Request" when Seerr's list has no media title. */
        val hasTitle: Boolean get() = title.isNotBlank() && title != "Request" && title != "Unknown request"
        /** Seerr MediaRequestStatus: 1 pending, 2 approved, 3 declined, 4 failed, 5 completed. */
        val statusLabel: String get() = when (status.lowercase()) {
            "1", "pending" -> "Pending"; "2", "approved" -> "Approved"; "3", "declined" -> "Declined"
            "4", "failed" -> "Failed"; "5", "completed", "available" -> "Available"; else -> status.replaceFirstChar { it.uppercase() }
        }
        val pending: Boolean get() = statusLabel == "Pending"
        val failed: Boolean get() = statusLabel == "Failed"
    }
    data class Requests(val total: Int, val pending: Int, val approved: Int, val available: Int, val items: List<Request>)
    fun requests(data: Any?) = data.asMap().let { m ->
        Requests(
            m.int("total"), m.int("pending"), m.int("approved"), m.int("available"),
            m.maps("items").map { Request(it.str("id"), it.str("sourceId"), it.str("title"), it.str("status"), it.str("source"), it.str("requestedBy")) },
        )
    }

    /** Full request list from /api/requests, newest first. */
    fun fullRequests(data: Any?): List<Request> = data.asMap().maps("requests").map {
        Request(
            it.str("requestId").ifBlank { it.str("id") }, it.str("sourceId"), it.str("title"), it.str("status"), it.str("source"),
            it.str("requestedBy"), it.str("posterUrl").ifBlank { null }, it.str("mediaType"), it.str("createdAt").ifBlank { null },
            backdropUrl = it.str("backdropUrl").ifBlank { null }, overview = it.str("overview"),
            genres = (it["genres"] as? List<*>)?.map { g -> g.toString() } ?: emptyList(),
            rating = it.num("rating").takeIf { r -> r > 0 }, runtime = it.int("runtime"),
            jellyfinItemId = it.str("jellyfinItemId").ifBlank { it.str("jellyfinMediaId") }.ifBlank { null },
        )
    }.sortedByDescending { it.createdAt ?: "" }

    data class Release(
        val title: String, val service: String, val date: String?, val subtitle: String = "",
        val posterUrl: String? = null, val hasFile: Boolean = false,
        val overview: String = "", val network: String = "",
    ) {
        val instant: java.time.Instant? get() = date?.let { d ->
            runCatching { java.time.OffsetDateTime.parse(d).toInstant() }
                .recoverCatching { java.time.LocalDate.parse(d.take(10)).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant() }
                .getOrNull()
        }
    }
    data class Calendar(val upcoming: Int, val today: Int, val items: List<Release>)
    fun calendar(data: Any?) = data.asMap().let { m ->
        Calendar(m.int("upcoming"), m.int("today"), m.maps("items").map { Release(it.str("title"), it.str("service"), it.str("date").ifBlank { null }, hasFile = it.bool("hasFile")) })
    }

    /** Every synced release from /api/integrations/calendar. */
    fun fullCalendar(data: Any?): List<Release> = data.asMap().maps("releases").map {
        val sub = it.str("subtitle").takeUnless { s -> s == "Movie release" || s == "Upcoming release" } ?: ""
        Release(
            it.str("title"), it.str("service"), it.str("date").ifBlank { null }, sub, it.str("posterUrl").ifBlank { null }, it.bool("hasFile"),
            overview = it.str("overview"), network = it.str("network"),
        )
    }

    /** Splits releases into upcoming (today onward, soonest first) and missed (past, still no file). */
    fun splitReleases(all: List<Release>): Pair<List<Release>, List<Release>> {
        val todayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val dated = all.filter { it.instant != null }
        val upcoming = dated.filter { !it.instant!!.isBefore(todayStart) }.sortedBy { it.instant }
        val missed = dated.filter { it.instant!!.isBefore(todayStart) && !it.hasFile }.sortedByDescending { it.instant }
        return upcoming to missed
    }

    data class Glance(
        val id: String, val name: String, val type: String, val year: Int, val library: String,
        val request: Request?, val download: Download?, val arr: Release?, val tools: List<String>,
    )

    /** /api/item-glance/:id — a library title joined to its request, download and Arr entry. */
    fun glance(data: Any?): Glance? {
        val m = data.asMap()
        val item = m.obj("item").takeIf { it.isNotEmpty() } ?: return null
        val req = m.obj("request").takeIf { it.isNotEmpty() }?.let {
            Request(it.str("id"), "", it.str("title"), it.str("status"), it.str("source"), "", mediaType = it.str("mediaType"))
        }
        val dl = m.obj("download").takeIf { it.isNotEmpty() }?.let {
            Download(it.str("id"), it.str("name"), it.str("state"), (it.num("progress") / 100.0).toFloat().coerceIn(0f, 1f), false)
        }
        val arr = m.obj("arr").takeIf { it.isNotEmpty() }?.let { Release(it.str("title"), it.str("service"), it.str("date").ifBlank { null }, hasFile = it.bool("hasFile")) }
        val tools = listOf("tdarr", "maintainerr", "kometa", "unpackerr").mapNotNull { k ->
            m.obj(k).takeIf { it.bool("connected") }?.str("name")?.ifBlank { k }
        }
        return Glance(item.str("id"), item.str("name"), item.str("type"), item.int("year"), item.str("library"), req, dl, arr, tools)
    }

    data class Play(val viewer: String, val title: String, val method: String, val client: String, val playedAt: String?)
    fun activity(data: Any?) = data.asMap().maps("items").map {
        Play(it.str("viewer"), it.str("title"), it.str("method"), it.str("client"), it.str("playedAt").ifBlank { null })
    }

    data class TopTitle(val title: String, val viewers: Int)
    data class TopViewer(val name: String, val plays: Int)
    data class Stats(val titles: List<TopTitle>, val people: List<TopViewer>)
    fun statistics(data: Any?) = data.asMap().let { m ->
        Stats(m.maps("items").map { TopTitle(it.str("title"), it.int("viewers")) }, m.maps("people").map { TopViewer(it.str("name"), it.int("plays")) })
    }

    data class User(val name: String, val lastAt: String?, val admin: Boolean)
    data class Users(val total: Int, val activeToday: Int, val admins: Int, val items: List<User>)
    fun users(data: Any?) = data.asMap().let { m ->
        Users(m.int("users"), m.int("activeToday"), m.int("admins"), m.maps("items").map { User(it.str("name"), it.str("lastAt").ifBlank { null }, it.bool("admin")) })
    }

    fun backdropUrl(baseUrl: String, id: String?, width: Int = 900): String? =
        id?.let { "${baseUrl.trimEnd('/')}/proxy/Items/Images/Backdrop?id=${java.net.URLEncoder.encode(it, "UTF-8")}&fillWidth=$width&quality=80" }

    /** Relative proxy paths (/proxy/...) need the server prefix; absolute URLs pass through. */
    fun absolute(baseUrl: String, url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("http") -> url
        else -> baseUrl.trimEnd('/') + (if (url.startsWith("/")) url else "/$url")
    }

    data class DigestItem(val type: String, val label: String)
    data class Health(
        val digestOk: Boolean, val digest: Int, val items: List<DigestItem>,
        val jellyfinOk: Boolean, val jellyfinName: String, val jellyfinVersion: String, val backupAt: String?,
    ) {
        val ok: Boolean get() = jellyfinOk && digestOk
        /** The digest repeats identical lines (one per failed delivery); collapse them with a count. */
        val grouped: List<Pair<String, Int>> get() = items.groupingBy { it.label }.eachCount().toList()
    }
    fun health(data: Any?) = data.asMap().let { m ->
        Health(
            m.bool("digestOk"), m.int("digest"), m.maps("items").map { DigestItem(it.str("type"), it.str("label")) },
            m.bool("jellyfinOk"), m.str("jellyfinName"), m.str("jellyfinVersion"), m.str("backupAt").ifBlank { null },
        )
    }

    data class RecentItem(val id: String, val title: String, val type: String, val year: Int, val added: String?)
    fun recent(data: Any?) = data.asMap().maps("items").map {
        RecentItem(it.str("id"), it.str("title"), it.str("type"), it.int("year"), it.str("added").ifBlank { null })
    }

    data class Issues(val total: Int, val open: Int)
    fun issues(data: Any?) = data.asMap().let { Issues(it.int("total"), it.int("open")) }

    /** Jellyfin profile picture via JellyGlance's proxy; 404s (no picture set) leave the initial showing. */
    fun userImageUrl(baseUrl: String, userId: String?, width: Int = 200): String? =
        userId?.takeIf { it.isNotBlank() && baseUrl.isNotBlank() }
            ?.let { "${baseUrl.trimEnd('/')}/proxy/Users/Images/Primary?id=${java.net.URLEncoder.encode(it, "UTF-8")}&fillWidth=$width&quality=80" }

    fun posterUrl(baseUrl: String, id: String?, width: Int = 300): String? =
        id?.let { "${baseUrl.trimEnd('/')}/proxy/Items/Images/Primary?id=${java.net.URLEncoder.encode(it, "UTF-8")}&fillWidth=$width&quality=90" }

    fun ticksToClock(ticks: Long): String {
        val s = ticks / 10_000_000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
