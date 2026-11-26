package dev.nerdytech.jellyglance.data

import android.util.Base64
import org.json.JSONObject

/** JellyGlance account sign-in (routes/auth.js): local accounts, Jellyfin password and Jellyfin Quick Connect. */
class Auth(private val api: JellyGlanceApi) {

    /** How this server lets people sign in, from the public /auth/isConfigured. */
    data class Config(val mode: String, val label: String, val version: String, val serverType: String) {
        val jellyfin: Boolean get() = mode == "quick-connect"
        val oidc: Boolean get() = mode == "oidc"
    }

    data class Session(val token: String, val user: String, val role: String, val exp: Long, val userId: String = "")

    data class QuickConnect(val code: String, val secret: String, val url: String)

    suspend fun config(baseUrl: String): Config {
        val m = api.auth("/auth/isConfigured", null, baseUrl) as? Map<*, *> ?: throw ApiException(ApiException.Kind.NOT_JSON)
        val auth = m["auth"] as? Map<*, *>
        return Config(
            mode = (auth?.get("mode") as? String) ?: "local",
            label = (auth?.get("label") as? String).orEmpty(),
            version = (m["version"] as? String).orEmpty(),
            serverType = (m["serverType"] as? String) ?: "jellyfin",
        )
    }

    /** JellyGlance's own accounts (the owner set up at first run, or local users). */
    suspend fun local(baseUrl: String, username: String, password: String): Session =
        session(api.auth("/auth/login", JSONObject().put("username", username).put("password", password), baseUrl))

    /** Jellyfin username and password; JellyGlance passes them to Jellyfin and never stores the password. */
    suspend fun jellyfin(baseUrl: String, username: String, password: String): Session =
        session(api.auth("/auth/media-server-login", JSONObject().put("username", username).put("password", password), baseUrl))

    suspend fun quickConnectStart(baseUrl: String): QuickConnect {
        val m = api.auth("/auth/jellyfin-quick-connect/initiate", JSONObject(), baseUrl) as? Map<*, *> ?: throw ApiException(ApiException.Kind.NOT_JSON)
        return QuickConnect(m["code"].toString(), m["secret"].toString(), (m["quickConnectUrl"] as? String).orEmpty())
    }

    suspend fun quickConnectApproved(baseUrl: String, secret: String): Boolean =
        (api.auth("/auth/jellyfin-quick-connect/status", JSONObject().put("secret", secret), baseUrl) as? Map<*, *>)?.get("authenticated") == true

    suspend fun quickConnectFinish(baseUrl: String, secret: String): Session =
        session(api.auth("/auth/jellyfin-quick-connect/complete", JSONObject().put("secret", secret), baseUrl))

    private fun session(response: Any?): Session {
        val m = response as? Map<*, *> ?: throw ApiException(ApiException.Kind.NOT_JSON)
        val token = m["token"] as? String ?: throw ApiException(ApiException.Kind.AUTH, "JellyGlance did not return a session")
        val claims = decode(token)
        val user = (m["user"] as? Map<*, *>)
        val claimUser = claims?.optJSONObject("user")
        return Session(
            token = token,
            user = (user?.get("name") as? String) ?: claimUser?.optString("username").orEmpty(),
            role = (user?.get("role") as? String) ?: claimUser?.optString("role").orEmpty(),
            exp = claims?.optLong("exp", 0L) ?: 0L,
            userId = (user?.get("id") as? String)?.takeIf { it.length > 8 } ?: claimUser?.optString("id")?.takeIf { it.length > 8 }.orEmpty(),
        )
    }

    /** Reads the JWT payload (no verification; the server does that). */
    private fun decode(token: String): JSONObject? = runCatching {
        val part = token.split('.')[1]
        JSONObject(String(Base64.decode(part, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
    }.getOrNull()
}
