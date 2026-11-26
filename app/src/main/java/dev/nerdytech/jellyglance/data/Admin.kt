package dev.nerdytech.jellyglance.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Server administration through a signed-in admin session: JellyGlance tasks, the download
 * queue, webhooks, integration health, backups, Wizarr invites and Jellyfin devices/plugins.
 */

data class ServerTask(val key: String, val name: String, val about: String)
data class TaskRun(val name: String, val result: String, val duration: String, val ranAt: String?)

data class DownloadClient(val name: String, val instanceId: String, val connected: Boolean, val message: String)
data class DownloadItem(
    val id: String, val name: String, val client: String, val state: String, val progress: Int, val size: String,
    val down: String, val up: String, val eta: Long?, val seeds: Int, val peers: Int, val error: String, val source: String,
) {
    val paused: Boolean get() = state.contains("pause", true) || state.contains("stopped", true)
    val done: Boolean get() = progress >= 100
}

data class Webhook(val id: String, val name: String, val type: String, val trigger: String, val event: String, val enabled: Boolean, val url: String, val raw: Map<*, *>)
data class Service(val name: String, val ok: Boolean, val issues: List<String>)
data class BackupFile(val name: String, val size: Double, val created: String?)
data class Invite(val id: String, val code: String, val url: String, val status: String, val expires: String?, val usedBy: String, val servers: List<String>)
data class WizarrServer(val id: Int, val name: String)
data class Device(val name: String, val app: String, val user: String, val lastActive: String?)
data class Plugin(val name: String, val version: String, val status: String)

class Admin(private val api: JellyGlanceApi) {
    private fun Map<*, *>.s(vararg keys: String): String {
        for (k in keys) when (val v = Fields.getCI(this, k)) {
            is String -> if (v.isNotBlank()) return v
            is Double -> return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
            is Boolean -> return v.toString()
        }
        return ""
    }
    private fun Map<*, *>.n(k: String) = (Fields.getCI(this, k) as? Double) ?: 0.0
    private fun maps(v: Any?) = (v as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ---- tasks (classes/task-list.js)
    val tasks = listOf(
        ServerTask("PartialJellyfinSync", "Recently added sync", "Pull new items from Jellyfin"),
        ServerTask("JellyfinSync", "Full Jellyfin sync", "Rebuild libraries, items and users from Jellyfin"),
        ServerTask("JellyfinPlaybackReportingPluginSync", "Playback Reporting import", "Import history from the Jellyfin plugin"),
        ServerTask("RefreshDashboardStats", "Refresh dashboard stats", "Rebuild the stats views behind libraries and charts"),
        ServerTask("IntegrationSync", "Integration sync", "Refresh data from the Arrs, Seerr and clients"),
        ServerTask("ArrCalendarSync", "Arr calendar sync", "Refresh upcoming releases"),
        ServerTask("DownloadQueueSync", "Download queue sync", "Refresh the download queue"),
        ServerTask("IntegrationHealthCheck", "Integration health check", "Test every connected integration"),
        ServerTask("WebhookHealthCheck", "Webhook health check", "Check webhook destinations"),
        ServerTask("InviteSync", "Invite sync", "Refresh Wizarr invites"),
        ServerTask("NewsletterCampaigns", "Newsletter campaigns", "Send due newsletter campaigns"),
        ServerTask("WrappedRecaps", "Wrapped recaps", "Build yearly recaps"),
        ServerTask("Backup", "Backup", "Back up the JellyGlance database"),
        ServerTask("ClearStaleTasks", "Clear stale task logs", "Tidy old task runs"),
    )

    suspend fun startTask(key: String) { api.get("/api/startTask?task=" + enc(key), lenient = true) }
    suspend fun stopTask(key: String) { api.get("/api/stopTask?task=" + enc(key), lenient = true) }

    suspend fun taskRuns(): List<TaskRun> = maps((api.get("/api/widgets/jobs") as? Map<*, *>)?.get("items")).map {
        TaskRun(it.s("name"), it.s("result"), it.s("duration"), it.s("ranAt").ifBlank { null })
    }

    // ---- downloads
    suspend fun downloads(): Pair<List<DownloadClient>, List<DownloadItem>> {
        val m = api.get("/api/integrations/downloads") as? Map<*, *> ?: return emptyList<DownloadClient>() to emptyList()
        val clients = maps(m["clients"]).map { DownloadClient(it.s("name"), it.s("instanceId"), Fields.getCI(it, "connected") == true, it.s("message")) }
        val items = maps(m["items"]).map {
            DownloadItem(
                it.s("id"), it.s("name"), it.s("client"), it.s("state"), it.n("progress").toInt(), it.s("size"), it.s("down"), it.s("up"),
                (Fields.getCI(it, "eta") as? Double)?.toLong(), it.n("seeds").toInt(), it.n("peers").toInt(), it.s("error"), it.s("source"),
            )
        }
        return clients to items
    }

    suspend fun pause(id: String, paused: Boolean) { api.post("/api/downloads/pause", JSONObject().put("id", id).put("paused", paused)) }
    suspend fun remove(id: String, deleteFiles: Boolean) { api.post("/api/downloads/remove", JSONObject().put("id", id).put("deleteFiles", deleteFiles)) }
    suspend fun add(client: DownloadClient, value: String) {
        api.post("/api/downloads/add", JSONObject().put("client", client.name).put("instanceId", client.instanceId).put("value", value.trim()))
    }

    // ---- webhooks
    suspend fun webhooks(): List<Webhook> = maps(api.get("/webhooks")).map {
        Webhook(it.s("id"), it.s("name"), it.s("webhook_type"), it.s("trigger_type"), it.s("event_type"), Fields.getCI(it, "enabled") == true, it.s("url"), it)
    }

    suspend fun testWebhook(id: String): Boolean = (api.post("/webhooks/$id/test", JSONObject()) as? Map<*, *>)?.let { it["success"] == true || it["ok"] == true } ?: true

    /** The update route replaces the whole row, so send every column back with only `enabled` changed. */
    suspend fun setWebhookEnabled(w: Webhook, enabled: Boolean) {
        val body = JSONObject()
        listOf("name", "url", "headers", "payload", "method", "webhook_type", "trigger_type", "schedule", "event_type", "retry_on_failure", "max_retries")
            .forEach { k -> w.raw[k]?.let { v -> body.put(k, toJson(v)) } }
        body.put("enabled", enabled)
        api.request("PUT", "/webhooks/${w.id}", body)
    }

    suspend fun deleteWebhook(id: String) { api.request("DELETE", "/webhooks/$id", null) }

    private fun toJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { o -> v.forEach { (k, x) -> o.put(k.toString(), toJson(x)) } }
        is List<*> -> JSONArray().also { a -> v.forEach { a.put(toJson(it)) } }
        is Double -> if (v % 1.0 == 0.0) v.toLong() else v
        else -> v
    }

    // ---- integrations
    suspend fun health(): List<Service> = maps((api.get("/api/automation-health") as? Map<*, *>)?.get("services")).map {
        Service(it.s("name"), Fields.getCI(it, "ok") == true, maps(it["issues"]).map { i -> readable(i.s("message")) }.filter { m -> m.isNotBlank() })
    }

    /** Bazarr and friends report issues as JSON text like {"object": "...", "issue": "..."}. */
    private fun readable(message: String): String {
        val objects = Regex("""\{[^{}]*\}""").findAll(message).mapNotNull { m ->
            runCatching { JSONObject(m.value) }.getOrNull()?.let { o ->
                listOf(o.optString("object"), o.optString("issue")).filter { it.isNotBlank() }.joinToString(": ")
            }
        }.toList()
        return if (objects.isNotEmpty()) objects.joinToString("\n") else message
    }

    suspend fun testAllIntegrations() { api.post("/api/integrations/test-all", JSONObject()) }

    // ---- backups
    suspend fun backups(): List<BackupFile> = maps(api.get("/backup/files")).map {
        BackupFile(it.s("name"), it.n("size"), it.s("datecreated").ifBlank { null })
    }.sortedByDescending { it.created ?: "" }

    suspend fun backupNow() { api.get("/backup/beginBackup", lenient = true) }
    suspend fun deleteBackup(name: String) { api.request("DELETE", "/backup/files/" + enc(name), null) }

    // ---- Wizarr
    suspend fun wizarr(): Pair<List<Invite>, List<WizarrServer>> {
        val m = api.get("/api/wizarr/summary") as? Map<*, *> ?: return emptyList<Invite>() to emptyList()
        val invites = maps(m["invites"]).map {
            Invite(
                it.s("id"), it.s("code"), it.s("url"), it.s("status"), it.s("expires").ifBlank { null },
                it.s("usedBy"), (it["serverNames"] as? List<*>)?.map { n -> n.toString() } ?: emptyList(),
            )
        }
        val servers = maps(m["servers"]).mapNotNull { s -> (Fields.getCI(s, "id") as? Double)?.toInt()?.let { WizarrServer(it, s.s("name")) } }
        return invites to servers
    }

    suspend fun createInvite(serverIds: List<Int>, expiresInDays: Int?) {
        val body = JSONObject().put("serverIds", JSONArray(serverIds)).put("duration", "unlimited")
        if (expiresInDays != null) body.put("expiresInDays", expiresInDays)
        api.post("/api/wizarr/invitations", body)
    }

    suspend fun deleteInvite(id: String) { api.request("DELETE", "/api/wizarr/invitations/" + enc(id), null) }

    // ---- Jellyfin
    suspend fun devices(): List<Device> {
        val r = api.get("/api/jellyfin/devices")
        val rows = maps(if (r is List<*>) r else (r as? Map<*, *>)?.let { it["Items"] ?: it["items"] ?: it["devices"] })
        return rows.map { Device(dev.nerdytech.jellyglance.ui.components.cleanDeviceName(it.s("Name", "name")), listOf(it.s("AppName", "appName"), it.s("AppVersion", "appVersion")).filter { s -> s.isNotBlank() }.joinToString(" "), it.s("LastUserName", "lastUserName"), it.s("DateLastActivity", "lastActivity").ifBlank { null }) }
            .sortedByDescending { it.lastActive ?: "" }
    }

    suspend fun plugins(): List<Plugin> {
        val r = api.get("/api/jellyfin/plugins")
        val rows = maps(if (r is List<*>) r else (r as? Map<*, *>)?.let { it["plugins"] ?: it["items"] })
        return rows.map { Plugin(it.s("Name", "name"), it.s("Version", "version"), it.s("Status", "status")) }.sortedBy { it.name.lowercase() }
    }

    suspend fun refreshLibrary() { api.post("/api/jellyfin/refresh-library", JSONObject()) }
}
