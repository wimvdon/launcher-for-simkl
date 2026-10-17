package io.github.impca201.launcherforsimkl.tv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import io.github.impca201.launcherforsimkl.data.model.AppChannel
import io.github.impca201.launcherforsimkl.data.model.AppProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the channels other apps publish for the home screen (Android TV's channel rows). Android
 * only shares them with home screens that are part of the system (the permission
 * ACCESS_ALL_EPG_DATA is "signature|privileged"), so on a normal installation [available] is false
 * and the launcher shows only its own rows. Installed as a privileged app (rooted devices, custom
 * builds), it shows every app's channels like the built-in home screen.
 */
class SystemChannels(
    private val context: Context,
) {
    val available: Boolean
        get() =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun load(): List<AppChannel> {
        if (!available) return emptyList()
        return withContext(Dispatchers.IO) { runCatching { query() }.getOrDefault(emptyList()) }
    }

    private fun query(): List<AppChannel> {
        val resolver = context.contentResolver
        val channels = mutableListOf<Triple<Long, String, String>>()
        resolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_PACKAGE_NAME),
            "${TvContract.Channels.COLUMN_TYPE}=? AND ${TvContract.Channels.COLUMN_BROWSABLE}=1",
            arrayOf(TvContract.Channels.TYPE_PREVIEW),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val pkg = c.getString(2) ?: continue
                if (pkg != context.packageName) channels += Triple(c.getLong(0), c.getString(1).orEmpty(), pkg)
            }
        }
        return channels.mapNotNull { (id, name, pkg) ->
            val programs = mutableListOf<AppProgram>()
            resolver.query(
                TvContract.buildPreviewProgramsUriForChannel(id),
                arrayOf(
                    TvContract.PreviewPrograms._ID,
                    TvContract.PreviewPrograms.COLUMN_TITLE,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                    TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                    TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                    TvContract.PreviewPrograms.COLUMN_WEIGHT,
                ),
                null,
                null,
                "${TvContract.PreviewPrograms.COLUMN_WEIGHT} DESC",
            )?.use { c ->
                while (c.moveToNext() && programs.size < MAX_PROGRAMS) {
                    programs +=
                        AppProgram(
                            key = "$pkg:${c.getLong(0)}",
                            title = c.getString(1).orEmpty(),
                            image = c.getString(2),
                            portrait = c.getInt(3) == TvContract.PreviewPrograms.ASPECT_RATIO_2_3,
                            intentUri = c.getString(4),
                        )
                }
            }
            programs.takeIf { it.isNotEmpty() }?.let { AppChannel("$pkg:$id", name, pkg, it) }
        }
    }

    /**
     * The intent a programme opens, or null when its link is broken. Like the built-in home screen,
     * it only starts activities: selectors and URI permission grants are dropped.
     */
    fun intentFor(program: AppProgram): Intent? {
        val intent = program.intentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() } ?: return null
        intent.selector = null
        intent.flags = intent.flags and PERMISSION_FLAGS.inv()
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val PERMISSION = "com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA"
        private const val MAX_PROGRAMS = 30
        private const val PERMISSION_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
