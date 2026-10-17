package io.github.impca201.launcherforsimkl.tv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import androidx.core.graphics.createBitmap
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.model.Images
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Tile
import io.github.impca201.launcherforsimkl.ui.MainActivity
import io.github.impca201.launcherforsimkl.ui.OpenActivity

/**
 * Publishes the launcher's "Up next" (paused and next episodes) as a channel on the system's own
 * home screen (Android TV home, and Google TV where it shows app channels), so it is there too when
 * the built-in home screen is used. Only local data: the channel is rewritten from the rows the
 * launcher already has, never with a call to SIMKL.
 */
class HomeChannel(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    private val supported: Boolean
        get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    /** Replaces the channel's programmes with [tiles]; removes the channel when [enabled] is false. */
    fun publish(
        tiles: List<Tile>,
        enabled: Boolean,
    ) {
        if (!supported) return
        runCatching {
            val existing = channelId()
            if (!enabled) {
                existing?.let { resolver.delete(TvContract.buildChannelUri(it), null, null) }
                return
            }
            val id = existing ?: createChannel() ?: return
            resolver.delete(TvContract.buildPreviewProgramsUriForChannel(id), null, null)
            tiles.take(MAX_PROGRAMS).forEachIndexed { index, tile -> resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, program(id, tile, index)) }
        }
    }

    /** This app's channel (the provider only returns this app's own channels). */
    private fun channelId(): Long? =
        resolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, TvContract.Channels.COLUMN_PACKAGE_NAME),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == CHANNEL_KEY && c.getString(2) == context.packageName) return c.getLong(0)
            }
            null
        }

    private fun createChannel(): Long? {
        val values =
            ContentValues().apply {
                put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, context.getString(R.string.home_channel_name))
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, CHANNEL_KEY)
                put(
                    TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
                    Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME),
                )
            }
        val uri = resolver.insert(TvContract.Channels.CONTENT_URI, values) ?: return null
        val id = runCatching { uri.lastPathSegment?.toLong() }.getOrNull() ?: return null
        runCatching { writeLogo(id) }
        // The first channel an app asks for is always shown on the home screen.
        runCatching { TvContract.requestChannelBrowsable(context, id) }
        return id
    }

    private fun writeLogo(channelId: Long) {
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = createBitmap(LOGO_SIZE, LOGO_SIZE)
        icon.setBounds(0, 0, LOGO_SIZE, LOGO_SIZE)
        icon.draw(Canvas(bitmap))
        resolver.openOutputStream(TvContract.buildChannelLogoUri(channelId))?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun program(
        channelId: Long,
        tile: Tile,
        weight: Int,
    ) = ContentValues().apply {
        put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
        put(
            TvContract.PreviewPrograms.COLUMN_TYPE,
            if (tile.type == MediaType.Movie) TvContract.PreviewPrograms.TYPE_MOVIE else TvContract.PreviewPrograms.TYPE_TV_EPISODE,
        )
        put(TvContract.PreviewPrograms.COLUMN_TITLE, tile.title)
        tile.episode?.let { ep ->
            ep.season?.let { put(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER, it.toString()) }
            put(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, ep.episode.toString())
        }
        tile.episodeTitle?.let { put(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE, it) }
        tile.overview?.let { put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, it) }
        tile.year?.let { put(TvContract.PreviewPrograms.COLUMN_RELEASE_DATE, it.toString()) }
        Images.poster(tile.poster, large = true)?.let {
            put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, it)
            put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_2_3)
        }
        put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, tile.key)
        put(TvContract.PreviewPrograms.COLUMN_WEIGHT, MAX_PROGRAMS - weight)
        put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, OpenActivity.intent(context, tile).toUri(Intent.URI_INTENT_SCHEME))
    }

    companion object {
        private const val CHANNEL_KEY = "up_next"
        private const val MAX_PROGRAMS = 20
        private const val LOGO_SIZE = 160
    }
}
