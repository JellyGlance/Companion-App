package dev.nerdytech.jellyglance.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.nerdytech.jellyglance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Self-updates the app from GitHub Releases ([BuildConfig.UPDATE_REPO]): finds the latest release,
 * downloads its APK (checking the published SHA-256 when the release has one) and hands it to
 * Android's package installer. Android itself refuses an APK signed with a different key.
 */
class Updater(private val context: Context) {

    data class Release(
        val version: String, val name: String, val notes: String, val url: String,
        val apkUrl: String, val apkName: String, val apkSize: Long, val sha256Url: String?, val publishedAt: String?,
    ) {
        val newer: Boolean get() = isNewer(version, BuildConfig.VERSION_NAME)
    }

    sealed interface Check {
        data class Available(val release: Release) : Check
        data class UpToDate(val latest: String) : Check
        data class Failed(val message: String) : Check
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000; readTimeout = 20_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "JellyGlance-Companion/${BuildConfig.VERSION_NAME}")
    }

    suspend fun check(): Check = withContext(Dispatchers.IO) {
        try {
            val conn = open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            val code = conn.responseCode
            if (code == 404) return@withContext Check.Failed("No releases published yet")
            if (code == 403) return@withContext Check.Failed("GitHub rate limit reached, try again later")
            if (code !in 200..299) return@withContext Check.Failed("GitHub answered $code")
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = j.optJSONArray("assets")
            var apk: JSONObject? = null
            var sha: String? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                val n = a.optString("name")
                // Prefer a release APK when a release carries several.
                if (n.endsWith(".apk", true) && (apk == null || (apk.optString("name").contains("debug", true) && !n.contains("debug", true)))) apk = a
                if (n.endsWith(".sha256", true)) sha = a.optString("browser_download_url")
            }
            val tag = j.optString("tag_name").removePrefix("v").removePrefix("V")
            if (apk == null) return@withContext Check.Failed("The latest release (v$tag) has no APK attached")
            val r = Release(
                tag, j.optString("name").ifBlank { "v$tag" }, j.optString("body"), j.optString("html_url"),
                apk.optString("browser_download_url"), apk.optString("name"), apk.optLong("size"), sha,
                j.optString("published_at").ifBlank { null },
            )
            if (r.newer) Check.Available(r) else Check.UpToDate(tag)
        } catch (e: IOException) {
            Check.Failed("Couldn't reach GitHub")
        } catch (e: Exception) {
            Check.Failed(e.message ?: "Update check failed")
        }
    }

    private fun dir() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Downloads the release APK, reporting progress 0..1, and verifies its checksum when published. */
    suspend fun download(r: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        dir().listFiles()?.forEach { it.delete() }
        val out = File(dir(), "JellyGlance-${r.version}.apk")
        val conn = open(r.apkUrl).apply { setRequestProperty("Accept", "application/octet-stream"); instanceFollowRedirects = true }
        if (conn.responseCode !in 200..299) throw IOException("Download failed (${conn.responseCode})")
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.apkSize
        val digest = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { input ->
            out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n); digest.update(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        r.sha256Url?.let { url ->
            val expected = open(url).inputStream.bufferedReader().use { it.readText() }.trim().split(Regex("\\s+")).first().lowercase()
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected.length == 64 && expected != actual) {
                out.delete()
                throw IOException("The download didn't match the release checksum, so it wasn't installed")
            }
        }
        out
    }

    /** Whether Android will let this app open the installer (the "install unknown apps" permission). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        /** True when [candidate] (e.g. 1.16.0) is a higher version than [current] (e.g. 1.15.1-debug). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Debug builds have a different package and signing key, so they can't take release updates. */
        val canSelfUpdate: Boolean get() = (!BuildConfig.DEBUG || BuildConfig.UPDATE_TEST) && Build.VERSION.SDK_INT >= 26
    }
}
