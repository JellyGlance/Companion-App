package dev.nerdytech.jellyglance.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.nerdytech.jellyglance.MainActivity
import dev.nerdytech.jellyglance.R

/** Quick Settings tile: "2 streaming · 1 request" at a glance; tap opens the app. */
class StatusTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val streams = p.getInt("streams", -1)
        val pending = p.getInt("pending", -1)
        qsTile?.apply {
            label = "JellyGlance"
            icon = Icon.createWithResource(this@StatusTile, R.drawable.ic_stat_jelly)
            state = if (streams > 0 || pending > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = when {
                streams < 0 -> "Open to refresh"
                else -> listOfNotNull("$streams streaming", pending.takeIf { it > 0 }?.let { "$it request${if (it == 1) "" else "s"}" }).joinToString(" · ")
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tab = if (p.getInt("pending", 0) > 0) "requests" else "streams"
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_TAB, tab)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val PREFS = "tile"

        /** Saves the latest counts and asks Android to redraw the tile. [pending] is null for non-admins. */
        fun refresh(context: Context, streams: Int, pending: Int?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("streams", streams).putInt("pending", pending ?: -1).apply()
            runCatching { requestListeningState(context, ComponentName(context, StatusTile::class.java)) }
        }
    }
}
