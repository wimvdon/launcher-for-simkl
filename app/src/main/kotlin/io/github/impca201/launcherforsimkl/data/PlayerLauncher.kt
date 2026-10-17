package io.github.impca201.launcherforsimkl.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.impca201.launcherforsimkl.data.model.DeepLinks
import io.github.impca201.launcherforsimkl.data.model.Player
import io.github.impca201.launcherforsimkl.data.model.Tile

/** Opens a title in Stremio or Nuvio, looking up its IMDb id first when the lists left it out. */
class PlayerLauncher(
    private val context: Context,
    private val apps: AppsRepository,
    private val content: ContentRepository,
) {
    /** True when the app opened (on the title, or at least its home screen). */
    suspend fun open(
        player: Player,
        tile: Tile,
    ): Boolean {
        val pkg = apps.installedPackage(player) ?: return false
        val ids = if (DeepLinks.needsLookup(player, tile.ids)) content.fullIds(tile.type, tile.ids) else tile.ids
        val link = DeepLinks.forTile(player, tile.copy(ids = ids))
        if (link != null) {
            val intent =
                Intent(Intent.ACTION_VIEW, link.toUri())
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // An old version without deep links: open the app itself below.
            }
        }
        return apps.open(pkg)
    }
}
