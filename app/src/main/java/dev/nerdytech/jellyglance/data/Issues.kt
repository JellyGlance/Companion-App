package dev.nerdytech.jellyglance.data

import java.time.Instant

/** One thing that needs attention, merged from the ops digest and, when readable, the webhook delivery log. */
data class Issue(
    val key: String,
    val kind: Kind,
    val title: String,
    val count: Int,
    /** Newest occurrence in epoch ms, or 0 when the source has no time. */
    val latestMs: Long,
    val summary: String = "",
    val details: List<Pair<String, String>> = emptyList(),
    val occurrences: List<Occurrence> = emptyList(),
    val webhookId: String? = null,
    val downloadId: String? = null,
    val downloadPaused: Boolean = false,
) {
    enum class Kind(val label: String) { WEBHOOK("Webhook"), DOWNLOAD("Download"), AUDIT("Failed action"), OTHER("Other") }
    data class Occurrence(val at: String?, val text: String)
}

data class Delivery(
    val webhookId: String, val name: String, val type: String, val event: String, val ok: Boolean,
    val status: Int, val error: String, val destination: String, val timestamp: String?,
) {
    val ms: Long get() = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
}

object Issues {
    @Suppress("UNCHECKED_CAST")
    fun deliveries(data: Any?): List<Delivery>? = (data as? List<*>)?.filterIsInstance<Map<String, Any?>>()?.map { m ->
        fun s(k: String) = when (val v = m[k]) { is String -> v; is Double -> v.toLong().toString(); else -> "" }
        Delivery(
            s("webhookId"), s("name").ifBlank { "Webhook" }, s("webhookType"), s("eventType"), m["ok"] == true,
            (m["status"] as? Double)?.toInt() ?: 0, s("error"), s("destination"), s("timestamp").ifBlank { null },
        )
    }

    fun build(health: Models.Health?, deliveries: List<Delivery>?, downloads: Models.Downloads?): List<Issue> {
        val out = mutableListOf<Issue>()
        if (deliveries != null) {
            deliveries.groupBy { it.webhookId.ifBlank { it.name } }.forEach { (id, rows) ->
                val fails = rows.filter { !it.ok }.sortedByDescending { it.ms }
                val lastFail = fails.firstOrNull() ?: return@forEach
                val lastOk = rows.filter { it.ok }.maxByOrNull { it.ms }
                // A webhook that has delivered since its last failure has recovered.
                if (lastOk != null && lastOk.ms > lastFail.ms) return@forEach
                val reason = when {
                    lastFail.error.isNotBlank() && lastFail.status > 0 -> "HTTP ${lastFail.status}: ${lastFail.error}"
                    lastFail.error.isNotBlank() -> lastFail.error
                    lastFail.status > 0 -> "HTTP ${lastFail.status}"
                    else -> "No error message recorded"
                }
                out += Issue(
                    key = "webhook:$id", kind = Issue.Kind.WEBHOOK,
                    title = "${lastFail.name} delivery failed", count = fails.size, latestMs = lastFail.ms,
                    summary = reason,
                    details = listOfNotNull(
                        lastFail.type.takeIf { it.isNotBlank() }?.let { "Type" to it.replaceFirstChar { c -> c.uppercase() } },
                        fails.map { it.event }.filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.let { "Events" to it.joinToString(", ") { e -> Fields.humanize(e) } },
                        lastFail.destination.takeIf { it.isNotBlank() }?.let { "Destination" to it },
                        "Last success" to (lastOk?.timestamp?.let { Fields.formatString(it) } ?: "Never in the recent log"),
                    ),
                    occurrences = fails.take(20).map { f ->
                        Issue.Occurrence(f.timestamp, listOfNotNull(
                            f.event.takeIf { it.isNotBlank() }?.let { Fields.humanize(it) },
                            f.status.takeIf { it > 0 }?.let { "HTTP $it" },
                            f.error.takeIf { it.isNotBlank() },
                        ).joinToString(" · "))
                    },
                    webhookId = lastFail.webhookId.ifBlank { null },
                )
            }
        }
        val digest = health?.items ?: emptyList()
        digest.filter { it.type != "webhook" || deliveries == null }.groupBy { it.type to it.label }.forEach { (k, rows) ->
            val (type, label) = k
            val kind = when (type) { "webhook" -> Issue.Kind.WEBHOOK; "download" -> Issue.Kind.DOWNLOAD; "audit" -> Issue.Kind.AUDIT; else -> Issue.Kind.OTHER }
            val dl = if (kind == Issue.Kind.DOWNLOAD) downloads?.items?.firstOrNull { label.startsWith(it.name) } else null
            out += Issue(
                key = "${type}:$label", kind = kind, title = label, count = rows.size, latestMs = 0L,
                summary = when (kind) {
                    Issue.Kind.WEBHOOK -> "A full-access API key shows the error and lets you send a test delivery."
                    Issue.Kind.DOWNLOAD -> dl?.let { "${(it.progress * 100).toInt()}% · ${it.state}" } ?: "Stalled, failed or paused part way"
                    Issue.Kind.AUDIT -> "Recorded in JellyGlance's audit log"
                    Issue.Kind.OTHER -> ""
                },
                downloadId = dl?.id?.ifBlank { null }, downloadPaused = dl?.paused == true,
            )
        }
        return out.sortedByDescending { it.latestMs }
    }

    fun visible(all: List<Issue>, dismissed: Map<String, Long>) = all.filter { i -> dismissed[i.key]?.let { it < i.latestMs } ?: true }
}
