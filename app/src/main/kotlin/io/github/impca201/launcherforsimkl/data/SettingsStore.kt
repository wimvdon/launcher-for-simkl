package io.github.impca201.launcherforsimkl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.impca201.launcherforsimkl.data.model.ChannelConfig
import io.github.impca201.launcherforsimkl.data.model.PinnedApps
import io.github.impca201.launcherforsimkl.data.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt file is reset instead of crashing every start.
private val Context.deviceStore: DataStore<Preferences> by preferencesDataStore(
    name = DataFiles.DEVICE,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val Context.backedUpStore: DataStore<Preferences> by preferencesDataStore(
    name = DataFiles.BACKED_UP_SETTINGS,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Names of the DataStore files. Only [BACKED_UP_SETTINGS] goes into Android's backup and
 * device-to-device transfer (`res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`,
 * checked by `BackupRulesTest`).
 */
object DataFiles {
    /** Pinned apps, channels and the streaming app. Backed up. */
    const val BACKED_UP_SETTINGS = "backed_up_settings"

    /** The SIMKL sign-in: never leaves this device. */
    const val DEVICE = "device"

    /** Where DataStore keeps a file, relative to the app's files folder (backup rules use this). */
    fun path(name: String) = "datastore/$name.preferences_pb"
}

data class LauncherSettings(
    val signedIn: Boolean = false,
    val userName: String? = null,
    val userAvatar: String? = null,
    /** Null until the first start has pinned the default apps. */
    val pinned: List<String>? = null,
    val channels: ChannelConfig = ChannelConfig(),
    /** Null: ask every time (when both apps are installed). */
    val player: Player? = null,
    /** Publish "Up next" to the system's home screen as a channel (Android TV home, Google TV). */
    val homeChannel: Boolean = true,
)

class SettingsStore(
    private val context: Context,
) {
    private object Keys {
        val token = stringPreferencesKey("access_token")
        val userName = stringPreferencesKey("user_name")
        val userAvatar = stringPreferencesKey("user_avatar")

        val pinned = stringPreferencesKey("pinned_apps")
        val channelOrder = stringPreferencesKey("channel_order")
        val channelHidden = stringSetPreferencesKey("channel_hidden")
        val player = stringPreferencesKey("player")
        val homeChannel = booleanPreferencesKey("home_channel")
    }

    private fun DataStore<Preferences>.safeData() = data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<LauncherSettings> =
        combine(context.deviceStore.safeData(), context.backedUpStore.safeData()) { device, backed ->
            LauncherSettings(
                signedIn = device[Keys.token] != null,
                userName = device[Keys.userName],
                userAvatar = device[Keys.userAvatar],
                pinned = backed[Keys.pinned]?.let(PinnedApps::parse),
                channels = ChannelConfig.parse(backed[Keys.channelOrder], backed[Keys.channelHidden]),
                player = Player.fromName(backed[Keys.player]),
                homeChannel = backed[Keys.homeChannel] ?: true,
            )
        }

    suspend fun current(): LauncherSettings = settings.first()

    suspend fun token(): String? = context.deviceStore.safeData().map { it[Keys.token] }.first()

    suspend fun signIn(token: String) {
        context.deviceStore.edit { it[Keys.token] = token }
    }

    suspend fun setUser(
        name: String?,
        avatar: String?,
    ) {
        context.deviceStore.edit { p ->
            if (name != null) p[Keys.userName] = name else p.remove(Keys.userName)
            if (avatar != null) p[Keys.userAvatar] = avatar else p.remove(Keys.userAvatar)
        }
    }

    suspend fun signOut() {
        context.deviceStore.edit { it.clear() }
    }

    suspend fun setPinned(pinned: List<String>) {
        context.backedUpStore.edit { it[Keys.pinned] = PinnedApps.format(pinned) }
    }

    suspend fun setChannels(config: ChannelConfig) {
        context.backedUpStore.edit { p ->
            p[Keys.channelOrder] = config.orderString()
            p[Keys.channelHidden] = config.hidden.map { it.name }.toSet()
        }
    }

    suspend fun setPlayer(player: Player?) {
        context.backedUpStore.edit { p -> if (player != null) p[Keys.player] = player.name else p.remove(Keys.player) }
    }

    suspend fun setHomeChannel(on: Boolean) {
        context.backedUpStore.edit { it[Keys.homeChannel] = on }
    }
}
