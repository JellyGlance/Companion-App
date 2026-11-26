package dev.nerdytech.jellyglance.data

import android.os.Build
import dev.nerdytech.jellyglance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to Jellyfin itself (not JellyGlance) so the app can make the Jellyfin app on this phone jump
 * to a title. The Jellyfin Android app can't take links, but its session accepts the server's
 * "Viewing" (DisplayContent) remote command, which is how casting a page to a device works.
 */
class JellyfinRemote(private val settings: () -> AppSettings, private val userDeviceName: () -> String? = { null }) {

    data class QuickConnect(val code: String, val secret: String)
    data class Linked(val token: String, val userId: String, val userName: String)
    data class Session(
        val id: String, val client: String, val deviceName: String, val lastActivity: String,
        val controllable: Boolean = false, val commands: List<String> = emptyList(),
    )

    private val deviceName = Build.MODEL ?: "Android"

    private fun header(token: String? = null): String {
        val s = settings()
        val id = s.jellyfinDeviceId.ifBlank { "jellyglance-companion" }
        return "MediaBrowser Client=\"JellyGlance Companion\", Device=\"$deviceName\", DeviceId=\"$id\", Version=\"${BuildConfig.VERSION_NAME}\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")
    }

    private suspend fun call(base: String, method: String, path: String, body: JSONObject? = null, token: String? = null): Any? = withContext(Dispatchers.IO) {
        val conn = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", header(token))
            conn.setRequestProperty("Accept", "application/json")
            if (body != null || method == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write((body?.toString() ?: "").toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                code == 401 || code == 403 -> throw ApiException(ApiException.Kind.AUTH, "Jellyfin refused the request (sign in to Jellyfin again)")
                code !in 200..299 -> throw ApiException(ApiException.Kind.HTTP, "Jellyfin returned HTTP $code")
            }
            if (text.isBlank()) null else JellyGlanceApi.toKotlin(org.json.JSONTokener(text).nextValue())
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(ApiException.Kind.OFFLINE, e.message ?: "Can't reach Jellyfin")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun quickConnectStart(base: String): QuickConnect {
        val m = call(base, "POST", "/QuickConnect/Initiate") as? Map<*, *> ?: throw ApiException(ApiException.Kind.NOT_JSON)
        return QuickConnect(m["Code"].toString(), m["Secret"].toString())
    }

    suspend fun quickConnectApproved(base: String, secret: String): Boolean =
        (call(base, "GET", "/QuickConnect/Connect?secret=" + URLEncoder.encode(secret, "UTF-8")) as? Map<*, *>)?.get("Authenticated") == true

    suspend fun quickConnectFinish(base: String, secret: String): Linked {
        val m = call(base, "POST", "/Users/AuthenticateWithQuickConnect", JSONObject().put("Secret", secret)) as? Map<*, *>
            ?: throw ApiException(ApiException.Kind.NOT_JSON)
        val user = m["User"] as? Map<*, *>
        return Linked(m["AccessToken"].toString(), user?.get("Id").toString(), user?.get("Name")?.toString().orEmpty())
    }

    /** Pause, Unpause or PlayPause a Jellyfin session (needs the Jellyfin link; admins can control anyone). */
    suspend fun playstate(sessionId: String, command: String) {
        val s = settings()
        call(s.jellyfinUrl, "POST", "/Sessions/${URLEncoder.encode(sessionId, "UTF-8")}/Playing/$command", token = s.jellyfinToken)
    }

    /** Jellyfin client sessions this user can control, newest first. */
    suspend fun sessions(): List<Session> {
        val s = settings()
        val list = call(s.jellyfinUrl, "GET", "/Sessions", token = s.jellyfinToken) as? List<*>
            ?: return emptyList()
        return list.filterIsInstance<Map<*, *>>().map {
            val commands = ((it["Capabilities"] as? Map<*, *>)?.get("SupportedCommands") as? List<*>)
                ?.map { c -> c.toString() }.orEmpty()
            Session(
                it["Id"].toString(), it["Client"]?.toString().orEmpty(),
                // Some clients send the model URL-encoded ("sdk+gphone64").
                it["DeviceName"]?.toString().orEmpty().replace('+', ' '),
                it["LastActivityDate"]?.toString().orEmpty(),
                it["SupportsRemoteControl"] == true, commands,
            )
        }.filter { !it.client.contains("JellyGlance", true) }.sortedByDescending { it.lastActivity }
    }

    /**
     * Waits for the Jellyfin app on this phone to show up as a controllable session (it was just
     * launched and needs a moment to open its websocket), then asks it to open [itemId]. Only
     * sessions reporting this phone's model are considered, so another device never gets the jump.
     * Returns the device it was sent to.
     */
    suspend fun showOnThisPhone(itemId: String, itemType: String, itemName: String, waitMs: Long = 25_000): String {
        val s = settings()
        val start = System.currentTimeMillis()
        // Jellyfin clients name the device by its model or by the name set in Android's settings.
        val names = listOfNotNull(deviceName, userDeviceName()).map { it.replace('+', ' ') }
        var target: Session? = null
        while (System.currentTimeMillis() - start < waitMs) {
            val all = runCatching { sessions() }.getOrElse { if (it is ApiException && it.kind == ApiException.Kind.AUTH) throw it else emptyList() }
            target = all.firstOrNull {
                names.any { n -> it.deviceName.equals(n, true) } && it.controllable && "DisplayContent" in it.commands
            }
            if (target != null) break
            delay(1500)
        }
        val t = target ?: throw ApiException(ApiException.Kind.HTTP, "The Jellyfin app on this phone didn't connect to Jellyfin in time")
        val q = "itemType=" + URLEncoder.encode(itemType, "UTF-8") + "&itemId=" + URLEncoder.encode(itemId, "UTF-8") +
            "&itemName=" + URLEncoder.encode(itemName, "UTF-8")
        call(s.jellyfinUrl, "POST", "/Sessions/${t.id}/Viewing?$q", token = s.jellyfinToken)
        return t.deviceName.ifBlank { t.client }
    }

    companion object {
        /** "https://media.example.com/web/index.html#!/details?id=..." -> "https://media.example.com" */
        fun baseFrom(itemUrl: String): String = itemUrl.substringBefore("/web/").substringBefore("/web#").trimEnd('/')
    }
}
