package dev.nerdytech.jellyglance.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** A scalar discovered somewhere in a widget response. */
data class Leaf(val path: String, val key: String, val value: Any) {
    val num: Double? get() = value as? Double
    val bool: Boolean? get() = value as? Boolean
}

/** An array discovered somewhere in a widget response. */
data class ListField(val path: String, val key: String, val items: List<Any?>)

data class Flat(val leaves: List<Leaf>, val lists: List<ListField>)

enum class Level { OK, WARN, ALERT }

/** What a card shows: the best field, formatted. */
data class Headline(val value: String, val caption: String, val level: Level, val path: String?, val num: Double?)

object Fields {
    private const val MAX_FIELDS = 80
    private const val MAX_STR = 60
    private val GOOD_BOOL = Regex("(^|[^a-z])(ok|healthy|reachable|online|up|success|connected|enabled|configured|running|alive)$", RegexOption.IGNORE_CASE)
    private val BYTES = Regex("bytes|size|storage|used|free|reclaim", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("seconds|secs", RegexOption.IGNORE_CASE)
    private val HIDDEN = setOf("jellyglance", "updatedAt")

    /** Plain-English captions for JellyGlance's widget keys. */
    private val LABELS = mapOf(
        "playing" to "Streaming now", "transcoding" to "Transcoding", "today" to "Today", "recent" to "Last 15 minutes",
        "last24h" to "Last 24 hours", "viewersToday" to "Viewers today", "users" to "Users", "sessionsToday" to "Plays today",
        "sessionsRecent" to "Plays, last 15 min", "sessionsDay" to "Plays, last 24h",
        "secondsToday" to "Watched today", "secondsWeek" to "Watched this week", "secondsAll" to "Watched all time",
        "hoursToday" to "Hours today", "hoursWeek" to "Hours this week", "hoursAll" to "Hours all time",
        "movies" to "Movies", "shows" to "Shows", "episodes" to "Episodes", "libraries" to "Libraries", "addedWeek" to "Added this week",
        "totalLabel" to "Library size", "totalBytes" to "Library size", "storageBytes" to "Library size", "storage" to "Library size",
        "upcomingCount" to "Upcoming releases", "upcomingEstimate" to "Upcoming size", "upcoming" to "Upcoming releases",
        "active" to "Active", "stalled" to "Stalled", "total" to "Total", "downloadTotal" to "Downloads total",
        "pending" to "Pending", "approved" to "Approved", "available" to "Available", "other" to "Other",
        "open" to "Open", "resolved" to "Resolved", "digest" to "Need attention", "digestOk" to "Ops digest",
        "jellyfinOk" to "Jellyfin", "jellyfinName" to "Server", "jellyfinVersion" to "Version", "backupAt" to "Last backup",
        "reclaimable" to "Reclaimable", "scheduled" to "Scheduled", "collections" to "Collections",
        "failing" to "Failing", "failed" to "Failed", "checked" to "Checked", "connected" to "Connected",
        "running" to "Running", "jobs" to "Tasks", "nextAt" to "Next newsletter", "lastAt" to "Last sent", "lastOk" to "Last send",
        "topTitle" to "Top title this week", "topUser" to "Top viewer this week", "titles" to "Titles", "count" to "Items",
        "autobrr" to "Release hits", "needsAttention" to "Need attention", "strikes" to "Strikes", "removals" to "Removals",
        "invites" to "Invites", "devices" to "Devices", "admins" to "Admins", "activeToday" to "Active today",
        "posters" to "Missing posters", "logos" to "Missing logos", "runtime" to "Missing runtime", "unmatched" to "Unmatched imports",
        "orphaned" to "Orphaned plays", "issues" to "Issues", "queued" to "Queued", "errored" to "Errored", "ok" to "Status",
        "estimate" to "Upcoming size", "kind" to "Destination", "configured" to "Configured", "campaigns" to "Campaigns",
        "frequency" to "Frequency", "enabled" to "Enabled", "nextName" to "Next campaign",
    )
    private val MINUTES = Regex("minutes|mins", RegexOption.IGNORE_CASE)
    private val HOURS = Regex("hours", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("percent|progress|pct|ratio", RegexOption.IGNORE_CASE)
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}(T[\\d:.]+(Z|[+-]\\d{2}:?\\d{2})?)?$")

    val TITLE_KEYS = listOf("title", "name", "itemName", "nowPlaying", "item", "seriesName", "series", "label", "displayName", "path", "id")
    val USER_KEYS = listOf("viewer", "userName", "user", "username", "requestedBy")
    val DEVICE_KEYS = listOf("device", "deviceName", "client", "player")
    val PROGRESS_KEYS = listOf("progress", "percent", "percentage", "playedPercentage", "progressPercent")
    val IMAGE_KEYS = listOf("poster", "posterUrl", "image", "imageUrl", "thumb", "thumbUrl", "backdrop", "art", "cover")

    fun flatten(data: Any?): Flat {
        val leaves = mutableListOf<Leaf>()
        val lists = mutableListOf<ListField>()
        fun full() = leaves.size + lists.size >= MAX_FIELDS
        fun walk(v: Any?, path: String, depth: Int) {
            if (full()) return
            when (v) {
                is List<*> -> lists += ListField(path, path.substringAfterLast('.'), v)
                is Map<*, *> -> if (depth < 4) v.forEach { (k, value) -> if (k !in HIDDEN) walk(value, if (path.isEmpty()) "$k" else "$path.$k", depth + 1) }
                is Double -> if (v.isFinite()) leaves += Leaf(path, path.substringAfterLast('.'), v)
                is Boolean -> leaves += Leaf(path, path.substringAfterLast('.'), v)
                is String -> if (v.isNotEmpty() && v.length <= MAX_STR) leaves += Leaf(path, path.substringAfterLast('.'), v)
                else -> Unit
            }
        }
        walk(data, "", 0)
        return Flat(leaves, lists)
    }

    fun headline(flat: Flat, def: WidgetDef, pinnedPath: String?): Headline {
        pinnedPath?.let { pin ->
            flat.leaves.firstOrNull { it.path == pin }?.let { return leafHeadline(it, def) }
            flat.lists.firstOrNull { it.path == pin }?.let { return listHeadline(it, def) }
        }
        for (pattern in def.main) {
            val re = Regex(pattern, RegexOption.IGNORE_CASE)
            flat.leaves.firstOrNull { it.num != null && re.containsMatchIn(it.key) }?.let { return leafHeadline(it, def) }
            flat.leaves.firstOrNull { re.containsMatchIn(it.key) }?.let { return leafHeadline(it, def) }
        }
        // A widget that is mostly a list: the list length is the most useful number.
        flat.lists.maxByOrNull { it.items.size }?.takeIf { flat.leaves.none { l -> l.num != null } }?.let { return listHeadline(it, def) }
        flat.leaves.firstOrNull { it.num != null }?.let { return leafHeadline(it, def) }
        flat.lists.firstOrNull()?.let { return listHeadline(it, def) }
        flat.leaves.firstOrNull()?.let { return leafHeadline(it, def) }
        return Headline("—", "No data", Level.OK, null, null)
    }

    private fun leafHeadline(l: Leaf, def: WidgetDef) = Headline(format(l), humanize(l.key), levelOf(l, def), l.path, l.num ?: l.bool?.let { if (it) 1.0 else 0.0 })

    private fun listHeadline(l: ListField, def: WidgetDef): Headline {
        val n = l.items.size.toDouble()
        val level = if (def.warnPositive && n > 0) Level.WARN else Level.OK
        return Headline(fmtNum(n), humanize(l.key.ifBlank { def.noun }).ifBlank { def.noun }, level, l.path, n)
    }

    private fun isGoodFlag(key: String) =
        GOOD_BOOL.containsMatchIn(key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2"))

    fun levelOf(l: Leaf, def: WidgetDef): Level {
        l.bool?.let { if (!it && isGoodFlag(l.key)) return Level.ALERT }
        l.num?.let { if (def.warnPositive && it > 0) return Level.WARN }
        return Level.OK
    }

    /** Worst state across a whole response: any false health flag is an alert. */
    fun overallLevel(flat: Flat, def: WidgetDef, head: Headline): Level {
        if (flat.leaves.any { it.bool == false && isGoodFlag(it.key) }) return Level.ALERT
        return head.level
    }

    fun format(l: Leaf): String = when (val v = l.value) {
        is Boolean -> if (isGoodFlag(l.key)) (if (v) "OK" else "Down") else (if (v) "Yes" else "No")
        is Double -> formatNumber(v, l.key)
        else -> formatString(v.toString())
    }

    fun formatNumber(v: Double, key: String): String = when {
        BYTES.containsMatchIn(key) && abs(v) >= 1024 -> fmtBytes(v)
        MINUTES.containsMatchIn(key) && v >= 60 -> fmtDuration(v * 60)
        HOURS.containsMatchIn(key) -> fmtDuration(v * 3600)
        SECONDS.containsMatchIn(key) -> fmtDuration(v)
        PERCENT.containsMatchIn(key) -> (if (v <= 1.0 && v > 0 && key.contains("ratio", true)) fmtNum(v * 100) else fmtNum(v)) + "%"
        else -> fmtNum(v)
    }

    fun formatString(s: String): String {
        if (s.length > 10 && ISO_DATE.matches(s)) {
            val t = runCatching { OffsetDateTime.parse(s).toInstant() }
                .recoverCatching { LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() }
                .getOrNull()
            if (t != null) return relative(t)
        }
        return s
    }

    fun relative(t: Instant): String {
        val diff = (System.currentTimeMillis() - t.toEpochMilli()) / 1000
        val a = abs(diff)
        val txt = when {
            a < 60 -> return "just now"
            a < 3600 -> "${a / 60}m"
            a < 86400 -> "${a / 3600}h"
            a < 86400 * 14 -> "${a / 86400}d"
            else -> return DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(t)
        }
        return if (diff >= 0) "$txt ago" else "in $txt"
    }

    fun fmtNum(v: Double): String {
        if (v == v.roundToLong().toDouble() && abs(v) < 1e15) {
            val n = v.roundToLong()
            return when {
                abs(n) >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
                abs(n) >= 100_000 -> String.format(Locale.US, "%.0fk", n / 1000.0)
                else -> NumberFormat.getIntegerInstance().format(n)
            }
        }
        return String.format(Locale.getDefault(), if (abs(v) < 10) "%.2f" else "%.1f", v)
    }

    fun fmtBytes(v: Double): String {
        val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
        var x = v
        var i = 0
        while (abs(x) >= 1024 && i < units.size - 1) { x /= 1024; i++ }
        return String.format(Locale.getDefault(), if (x >= 100) "%.0f %s" else "%.1f %s", x, units[i])
    }

    fun fmtDuration(seconds: Double): String {
        val s = seconds.roundToLong()
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun humanize(key: String): String {
        if (key.isBlank()) return ""
        LABELS[key]?.let { return it }
        val spaced = key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').trim()
        return spaced.lowercase().replaceFirstChar { it.uppercase() }
    }

    // ---------------------------------------------------------------- list items

    @Suppress("UNCHECKED_CAST")
    fun getCI(obj: Any?, key: String): Any? {
        val m = obj as? Map<String, Any?> ?: return null
        m[key]?.let { return it }
        return m.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    fun pickStr(obj: Any?, keys: List<String>): String {
        for (k in keys) {
            when (val v = getCI(obj, k)) {
                is String -> if (v.isNotBlank()) return v
                is Double -> return fmtNum(v)
                is Map<*, *> -> pickStr(v, TITLE_KEYS).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    fun itemTitle(item: Any?): String = when (item) {
        is String -> item
        is Double -> fmtNum(item)
        is Map<*, *> -> pickStr(item, TITLE_KEYS)
        else -> ""
    }

    fun itemSubtitle(item: Any?): String {
        if (item !is Map<*, *>) return ""
        val parts = mutableListOf<String>()
        pickStr(item, USER_KEYS).takeIf { it.isNotBlank() }?.let { parts += it }
        pickStr(item, DEVICE_KEYS).takeIf { it.isNotBlank() }?.let { parts += it }
        if (parts.isEmpty()) {
            pickStr(item, listOf("status", "state", "type", "kind", "library", "date", "when", "airDate", "added", "createdAt", "time"))
                .takeIf { it.isNotBlank() }?.let { parts += formatString(it) }
        }
        return parts.joinToString(" • ")
    }

    /** 0..1 progress if the item carries one. */
    fun itemProgress(item: Any?): Float? {
        for (k in PROGRESS_KEYS) {
            val v = getCI(item, k) as? Double ?: continue
            return (if (v > 1.0) v / 100.0 else v).toFloat().coerceIn(0f, 1f)
        }
        val pos = (getCI(item, "position") ?: getCI(item, "positionTicks")) as? Double
        val len = (getCI(item, "runtime") ?: getCI(item, "runTimeTicks") ?: getCI(item, "duration")) as? Double
        if (pos != null && len != null && len > 0) return (pos / len).toFloat().coerceIn(0f, 1f)
        return null
    }

    fun itemImage(item: Any?, baseUrl: String): String? {
        for (k in IMAGE_KEYS) {
            val v = getCI(item, k) as? String ?: continue
            if (v.startsWith("http://") || v.startsWith("https://")) return v
            if (v.startsWith("/")) return baseUrl.trimEnd('/') + v
        }
        return null
    }

    fun sessionId(item: Any?): String {
        for (k in listOf("sessionId", "SessionId", "id", "Id")) {
            when (val v = getCI(item, k)) {
                is String -> if (v.isNotBlank()) return v
                is Double -> return v.roundToLong().toString()
            }
        }
        return ""
    }

    /** Scalar fields of a list item that are not already the title or subtitle. */
    fun itemDetails(item: Any?): List<Pair<String, String>> {
        if (item !is Map<*, *>) return emptyList()
        return flatten(item).leaves.take(12).map { humanize(it.key) to format(it) }
    }
}
