package dev.nerdytech.jellyglance.data

enum class Category(val label: String, val color: Long) {
    PLAYBACK("Playback", 0xFF7C5CE8),
    LIBRARY("Library", 0xFF1597C6),
    DOWNLOADS("Downloads", 0xFF3B7EE0),
    REQUESTS("Requests", 0xFFC2549F),
    CALENDAR("Calendar", 0xFFD9822F),
    HEALTH("Health", 0xFF23A85E),
    OPS("Ops", 0xFF5B7C9C),
}

/**
 * One JellyGlance widget route. JellyGlance documents routes but not response bodies, so
 * [main] only ranks which discovered field is shown first (regexes, case-insensitive).
 */
data class WidgetDef(
    val id: String,
    val name: String,
    val route: String,
    val icon: String,
    val category: Category,
    val tip: String,
    val main: List<String> = emptyList(),
    val noun: String = "items",
    val warnPositive: Boolean = false,
)

object Catalogue {
    val all: List<WidgetDef> = listOf(
        WidgetDef("nowplaying", "Now Playing", "/api/widgets/nowplaying", "play", Category.PLAYBACK, "Live Jellyfin sessions playing right now.", listOf("^playing\$"), "streams"),
        WidgetDef("sessions", "Playback", "/api/widgets/sessions", "chart", Category.PLAYBACK, "Recent, today and 24 hour playback counts.", listOf("^today\$")),
        WidgetDef("viewers", "Viewers", "/api/widgets/viewers", "users", Category.PLAYBACK, "Distinct viewers today and synced user count.", listOf("^viewersToday\$")),
        WidgetDef("watch", "Watch Time", "/api/widgets/watch", "clock", Category.PLAYBACK, "Watch-time totals for today, this week and all time.", listOf("^secondsToday\$")),
        WidgetDef("activity", "Activity", "/api/widgets/activity", "list", Category.PLAYBACK, "Latest playback rows.", listOf("^count\$"), "plays"),
        WidgetDef("statistics", "Top Stats", "/api/widgets/statistics", "chart", Category.PLAYBACK, "Top titles and users this week.", listOf("^topTitle\$")),
        WidgetDef("users", "Users", "/api/widgets/users", "users", Category.PLAYBACK, "User roster with last activity.", listOf("^users\$"), "users"),
        WidgetDef("devices", "Devices", "/api/widgets/devices", "monitor", Category.PLAYBACK, "Known Jellyfin client devices.", listOf("^devices\$"), "devices"),

        WidgetDef("catalog", "Catalog", "/api/widgets/catalog", "film", Category.LIBRARY, "Movies, shows, episodes and items added this week.", listOf("^movies\$")),
        WidgetDef("recent", "Recently Added", "/api/widgets/recent", "film", Category.LIBRARY, "Recently added library titles.", listOf("^count\$"), "titles"),
        WidgetDef("libraries", "Libraries", "/api/widgets/libraries", "storage", Category.LIBRARY, "Per-library sizes and catalog totals.", listOf("^totalLabel\$"), "libraries"),
        WidgetDef("storage", "Storage", "/api/widgets/storage", "storage", Category.LIBRARY, "Library storage total and upcoming Arr estimate.", listOf("^totalLabel\$")),

        WidgetDef("downloads", "Downloads", "/api/widgets/downloads", "download", Category.DOWNLOADS, "Download queue counts and the items in it.", listOf("^active\$")),
        WidgetDef("stalled", "Stalled", "/api/widgets/stalled", "download", Category.DOWNLOADS, "Stalled or failed download items.", listOf("^stalled\$"), "stalled", warnPositive = true),
        WidgetDef("stitched", "Queue", "/api/widgets/stitched", "download", Category.DOWNLOADS, "Cached download queue matched to requests.", listOf("^total\$")),

        WidgetDef("requests", "Requests", "/api/widgets/requests", "plus", Category.REQUESTS, "Seerr request counts by status.", listOf("^pending\$")),
        WidgetDef("issues", "Issues", "/api/widgets/issues", "err", Category.REQUESTS, "Seerr issue counts by status.", listOf("^open\$"), warnPositive = true),

        WidgetDef("calendar", "Calendar", "/api/widgets/calendar", "calendar", Category.CALENDAR, "Arr releases today and upcoming.", listOf("^upcoming\$"), "releases"),
        WidgetDef("today", "Releases Today", "/api/widgets/today", "calendar", Category.CALENDAR, "Arr releases due today.", listOf("^today\$"), "releases"),

        WidgetDef("health", "Health", "/api/widgets/health", "health", Category.HEALTH, "Digest, live Jellyfin ping and backup hint.", listOf("^jellyfinOk\$")),
        WidgetDef("jellyfinstatus", "Jellyfin", "/api/jellyfin/status", "health", Category.HEALTH, "Live Jellyfin reachability and version.", listOf("^ok\$")),

        WidgetDef("transcodes", "Tdarr", "/api/widgets/transcodes", "gear", Category.OPS, "Tdarr active, queued and error counts.", listOf("^active\$")),
        WidgetDef("maintainerr", "Maintainerr", "/api/widgets/maintainerr", "trash", Category.OPS, "Maintainerr cleanup counts and reclaimable size.", listOf("^reclaimable\$")),
        WidgetDef("digest", "Ops Digest", "/api/widgets/digest", "list", Category.OPS, "Ops digest items that need attention.", listOf("^digest\$"), warnPositive = true),
        WidgetDef("repair", "Repair", "/api/widgets/repair", "gear", Category.OPS, "Missing posters, runtime and unmatched imports.", listOf("^issues\$"), warnPositive = true),
        WidgetDef("invites", "Invites", "/api/widgets/invites", "plus", Category.OPS, "Cached Wizarr invite links.", listOf("^active\$"), "invites"),
        WidgetDef("autobrr", "autobrr", "/api/widgets/autobrr", "list", Category.OPS, "Cached autobrr release hits.", listOf("^autobrr\$"), "hits"),
        WidgetDef("automation", "Integrations", "/api/widgets/automation", "gear", Category.OPS, "Latest integration health results.", listOf("^failing\$"), warnPositive = true),
        WidgetDef("webhooks", "Webhooks", "/api/widgets/webhooks", "list", Category.OPS, "Recent webhook deliveries.", listOf("^failed\$"), "deliveries", warnPositive = true),
        WidgetDef("jobs", "Glance Jobs", "/api/widgets/jobs", "clock", Category.OPS, "Latest JellyGlance task runs.", listOf("^failed\$"), "jobs", warnPositive = true),
        WidgetDef("jellyfinjobs", "Jellyfin Tasks", "/api/widgets/jellyfin-jobs", "clock", Category.OPS, "Live Jellyfin scheduled tasks.", listOf("^running\$"), "tasks"),
        WidgetDef("newsletter", "Newsletter", "/api/widgets/newsletter", "list", Category.OPS, "Last newsletter send and next digest.", listOf("^nextAt\$")),
        WidgetDef("backup", "Backup", "/api/widgets/backup", "storage", Category.OPS, "Last backup hint and destination kind.", listOf("^backupAt\$")),

        WidgetDef("cleanuparr", "Cleanuparr", "/api/widgets/cleanuparr", "trash", Category.DOWNLOADS, "Cleanuparr strikes, removals and items needing attention.", listOf("^needsAttention\$"), warnPositive = true),

        WidgetDef("homepage", "Snapshot", "/api/widgets/homepage", "eye", Category.PLAYBACK, "The all-in-one dashboard snapshot.", listOf("^sessionsToday\$")),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): WidgetDef? = byId[id]

    /** A widget definition for an arbitrary route the user typed in. */
    fun custom(route: String): WidgetDef =
        all.firstOrNull { it.route == route }
            ?: WidgetDef("custom", route.substringAfterLast('/').ifBlank { "Custom" }.replaceFirstChar { it.uppercase() }, route, "eye", Category.OPS, "Custom route")

    /** Extra cards under the fixed dashboard sections. */
    val DEFAULT_DASHBOARD = listOf("viewers", "transcodes")

    const val NOW_PLAYING = "/api/widgets/nowplaying"
    const val STOP_STREAM = "/api/widgets/sessions/stop"
    const val PAUSE_DOWNLOAD = "/api/widgets/downloads/pause"
    const val REQUEST_ACTION = "/api/widgets/requests/actions"
    /** Raw Jellyfin sessions with progress and item ids. Only full-scope keys may read it. */
    const val LIVE_SESSIONS = "/proxy/getSessions"
    /** Seerr requests with titles and posters (full-scope keys only). */
    const val FULL_REQUESTS = "/api/requests"
    /** The whole synced Arr calendar, past 35 days onward (full-scope keys only). */
    const val FULL_CALENDAR = "/api/integrations/calendar"
    /** Recent outbound webhook deliveries with status and error (full-scope keys only). */
    const val WEBHOOK_HISTORY = "/webhooks/delivery-history"
}
