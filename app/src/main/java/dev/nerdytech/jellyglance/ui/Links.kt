package dev.nerdytech.jellyglance.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Opens links in the matching app when it is installed, otherwise in the browser.
 * The packages are also declared under <queries> in the manifest so Android 11+ lets us see them.
 */
object Links {
    /** Jellyfin clients in order of preference: official, Findroid, Streamyfin, Wholphin. */
    val JELLYFIN_APPS = listOf("org.jellyfin.mobile", "dev.jdtech.jellyfin", "com.fredrikburmester.streamyfin", "com.github.damontecres.wholphin")
    private const val IMDB_APP = "com.imdb.mobile"
    private const val YOUTUBE_APP = "com.google.android.youtube"

    /** Name of the installed Jellyfin client, if any. */
    fun jellyfinApp(ctx: Context): String? = JELLYFIN_APPS.firstOrNull { installed(ctx, it) }

    private fun installed(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun tryStart(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    fun browser(ctx: Context, url: String) {
        if (!tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
    }

    /**
     * A Jellyfin item: the first installed Jellyfin app (handed the item link, or just opened when it
     * can't take links), otherwise Jellyfin's web UI at [webUrl].
     */
    fun jellyfin(ctx: Context, webUrl: String) {
        val app = JELLYFIN_APPS.firstOrNull { installed(ctx, it) }
        if (app != null) {
            if (webUrl.isNotBlank() && tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).setPackage(app))) return
            ctx.packageManager.getLaunchIntentForPackage(app)?.let { launch ->
                if (tryStart(ctx, launch)) {
                    Toast.makeText(ctx, "Opened the Jellyfin app", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        if (webUrl.isNotBlank()) browser(ctx, webUrl)
    }

    /** IMDb title (tt...): the IMDb app if installed, otherwise imdb.com. */
    fun imdb(ctx: Context, imdbId: String) {
        val web = "https://www.imdb.com/title/$imdbId/"
        if (installed(ctx, IMDB_APP)) {
            if (tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(web)).setPackage(IMDB_APP))) return
            if (tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("imdb:///title/$imdbId/")))) return
        }
        browser(ctx, web)
    }

    fun youtube(ctx: Context, key: String) {
        val web = "https://www.youtube.com/watch?v=$key"
        if (installed(ctx, YOUTUBE_APP) && tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(web)).setPackage(YOUTUBE_APP))) return
        browser(ctx, web)
    }

    /** Any other link: whatever app claims it (TMDB, Rotten Tomatoes, Sonarr...), else the browser. */
    fun open(ctx: Context, url: String) = when {
        url.contains("imdb.com/title/") -> imdb(ctx, url.substringAfter("/title/").substringBefore('/'))
        url.contains("youtube.com/watch") -> youtube(ctx, Uri.parse(url).getQueryParameter("v").orEmpty())
        url.contains("/web/index.html") || url.contains("/web/#") -> jellyfin(ctx, url)
        else -> browser(ctx, url)
    }
}
