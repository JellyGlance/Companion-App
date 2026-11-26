package dev.nerdytech.jellyglance.data

import dev.nerdytech.jellyglance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** A failure the UI can explain in one line. */
class ApiException(val kind: Kind, val detail: String = "") : Exception(detail.ifBlank { kind.message }) {
    enum class Kind(val short: String, val message: String) {
        NOT_CONFIGURED("Setup", "Not configured yet: enter your JellyGlance URL."),
        BAD_URL("URL", "Invalid server URL. Include http:// or https://."),
        AUTH("Auth", "Authentication failed: check the API key."),
        FORBIDDEN("Forbidden", "This API key is not allowed to do that (needs widgets-write or full access)."),
        MISSING("Missing", "Route not found: check the URL and your JellyGlance version."),
        UNAVAILABLE("Unavailable", "JellyGlance could not load that widget (503)."),
        HTTP("HTTP", "JellyGlance returned an error."),
        NOT_JSON("Not JSON", "The response was not JSON: check the URL points at JellyGlance."),
        TLS("TLS", "TLS certificate error. Turn on \"Allow self-signed certificates\" if this is expected."),
        TIMEOUT("Timeout", "Timed out reaching JellyGlance."),
        OFFLINE("Offline", "Can't reach JellyGlance."),
    }
}

/** Minimal JellyGlance client: GET widget routes and POST control actions with x-api-token. */
class JellyGlanceApi(
    private val settings: () -> AppSettings,
    /** Called when the server rejects the session token, so the app can fall back to the API key. */
    private val onSessionRejected: () -> Unit = {},
) {

    /** [lenient] accepts a plain-text reply (some JellyGlance routes answer "OK" instead of JSON). */
    suspend fun get(route: String, override: AppSettings? = null, useSession: Boolean = true, lenient: Boolean = false): Any? =
        withSessionFallback(useSession, override) { session -> request(if (lenient) "GET-TEXT" else "GET", route, null, override, session) }

    suspend fun post(route: String, body: JSONObject): Any? =
        withSessionFallback(true, null) { session -> request("POST", route, body.toString(), null, session) }

    /** PUT and DELETE for admin routes; a non-JSON reply is fine. */
    suspend fun request(method: String, route: String, body: JSONObject?): Any? =
        withSessionFallback(true, null) { session -> request(method, route, body?.toString(), null, session) }

    /** Unauthenticated calls to the /auth routes, for signing in. */
    suspend fun auth(route: String, body: JSONObject?, baseUrl: String? = null): Any? {
        val s = settings().let { if (baseUrl != null) it.copy(baseUrl = baseUrl) else it }.copy(apiKey = "", sessionToken = "")
        return request(if (body == null) "GET" else "POST", route, body?.toString(), s, false)
    }

    private suspend fun withSessionFallback(useSession: Boolean, override: AppSettings?, call: suspend (Boolean) -> Any?): Any? {
        val s = override ?: settings()
        val session = useSession && s.signedIn
        return try {
            call(session)
        } catch (e: ApiException) {
            if (!session || e.kind != ApiException.Kind.AUTH) throw e
            onSessionRejected()
            if (s.apiKey.isBlank()) throw ApiException(ApiException.Kind.AUTH, "Your JellyGlance session expired. Sign in again.")
            call(false)
        }
    }

    suspend fun stopSession(sessionId: String) {
        post(Catalogue.STOP_STREAM, JSONObject().put("sessionId", sessionId))
    }

    /** Pops a message up over playback on a Jellyfin client (header shows as "JellyGlance"). */
    suspend fun sendSessionMessage(sessionId: String, text: String) {
        post("/api/sessions/message", JSONObject().put("sessionId", sessionId).put("header", "JellyGlance").put("text", text))
    }

    /** Shows [text] on screen, then stops the stream after [delayMs]. */
    suspend fun stopWithMessage(sessionId: String, text: String, delayMs: Long = 5000) {
        post("/api/sessions/stop-with-message", JSONObject().put("sessionId", sessionId).put("header", "JellyGlance").put("text", text).put("delayMs", delayMs))
    }

    suspend fun pauseDownload(id: String, paused: Boolean) {
        post(Catalogue.PAUSE_DOWNLOAD, JSONObject().put("id", id).put("paused", paused))
    }

    /** action is approve, decline or retry. */
    suspend fun requestAction(requestId: String, sourceId: String, action: String) {
        val body = JSONObject().put("requestId", requestId).put("action", action)
        if (sourceId.isNotBlank()) body.put("sourceId", sourceId)
        post(Catalogue.REQUEST_ACTION, body)
    }

    /** Re-sends a test message through a webhook. Returns whether it was delivered. */
    suspend fun retryWebhook(webhookId: String): Boolean =
        (post("/webhooks/retry/" + java.net.URLEncoder.encode(webhookId, "UTF-8"), JSONObject()) as? Map<*, *>)?.get("ok") == true

    suspend fun refreshItem(itemId: String) {
        post("/api/widgets/repair/refresh", JSONObject().put("itemId", itemId).put("recursive", true))
    }

    private suspend fun request(method: String, route: String, body: String?, override: AppSettings?, session: Boolean = false): Any? = withContext(Dispatchers.IO) {
        val s = override ?: settings()
        val base = s.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw ApiException(ApiException.Kind.NOT_CONFIGURED)
        val url = try {
            URL(base + (if (route.startsWith('/')) route else "/$route")).also {
                if (it.protocol != "http" && it.protocol != "https") throw IOException()
            }
        } catch (e: Exception) {
            throw ApiException(ApiException.Kind.BAD_URL)
        }

        val conn = url.openConnection() as HttpURLConnection
        try {
            if (conn is HttpsURLConnection && s.insecure) {
                conn.sslSocketFactory = trustAllFactory
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            conn.requestMethod = if (method == "GET-TEXT") "GET" else method
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "jellyglance-android/${BuildConfig.VERSION_NAME}")
            if (session) conn.setRequestProperty("Authorization", "Bearer " + s.sessionToken)
            else if (s.apiKey.isNotBlank()) conn.setRequestProperty("x-api-token", s.apiKey.trim())
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            when {
                // JellyGlance passes some upstream errors through with their status (a Wizarr or Seerr 401),
                // so only its own token/key rejection counts as "signed out".
                code == 401 && isOwnAuthFailure(text) -> throw ApiException(ApiException.Kind.AUTH, serverMessage(text))
                code == 401 -> throw ApiException(ApiException.Kind.HTTP, serverMessage(text).ifBlank { "The service behind JellyGlance refused the request (401)" })
                code == 403 -> throw ApiException(if (method == "GET") ApiException.Kind.AUTH else ApiException.Kind.FORBIDDEN)
                code == 404 -> throw ApiException(ApiException.Kind.MISSING)
                code == 503 -> throw ApiException(ApiException.Kind.UNAVAILABLE)
                code !in 200..299 -> throw ApiException(ApiException.Kind.HTTP, serverMessage(text).ifBlank { "HTTP $code" })
            }
            if (text.isBlank()) return@withContext null
            try {
                toKotlin(JSONTokener(text).nextValue())
            } catch (e: Exception) {
                if (method == "GET") throw ApiException(ApiException.Kind.NOT_JSON) else null
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: SSLException) {
            throw ApiException(ApiException.Kind.TLS, e.message ?: "")
        } catch (e: SocketTimeoutException) {
            throw ApiException(ApiException.Kind.TIMEOUT)
        } catch (e: UnknownHostException) {
            throw ApiException(ApiException.Kind.OFFLINE, "Unknown host ${url.host}")
        } catch (e: ConnectException) {
            throw ApiException(ApiException.Kind.OFFLINE, "Connection refused by ${url.host}")
        } catch (e: IOException) {
            throw ApiException(ApiException.Kind.OFFLINE, e.message ?: "")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** The 401 bodies JellyGlance's own authenticate() sends (server.js), or an empty 401. */
        fun isOwnAuthFailure(text: String): Boolean {
            val msg = serverMessage(text)
            return text.isBlank() || msg.isBlank() || Regex("invalid token|authentication failed", RegexOption.IGNORE_CASE).containsMatchIn(msg)
        }

        /** JellyGlance errors come back as {error}, {errorMessage} or {message}. */
        fun serverMessage(text: String): String = runCatching {
            val o = JSONObject(text)
            o.optString("errorMessage").ifBlank { o.optString("error") }.ifBlank { o.optString("message") }
        }.getOrDefault("")

        /** org.json -> plain Kotlin maps, lists and primitives. */
        fun toKotlin(v: Any?): Any? = when (v) {
            null, JSONObject.NULL -> null
            is JSONObject -> LinkedHashMap<String, Any?>().also { m -> v.keys().forEach { k -> m[k] = toKotlin(v.opt(k)) } }
            is JSONArray -> List(v.length()) { toKotlin(v.opt(it)) }
            is Int -> v.toDouble()
            is Long -> v.toDouble()
            is Float -> v.toDouble()
            is Number -> v.toDouble()
            else -> v
        }

        private val trustAllFactory: SSLSocketFactory by lazy {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }.socketFactory
        }
    }
}
