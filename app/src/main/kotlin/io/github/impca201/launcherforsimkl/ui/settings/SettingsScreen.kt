package io.github.impca201.launcherforsimkl.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.LauncherSettings
import io.github.impca201.launcherforsimkl.data.model.Channel
import io.github.impca201.launcherforsimkl.data.model.Player
import io.github.impca201.launcherforsimkl.ui.components.requestAfterLayout
import io.github.impca201.launcherforsimkl.ui.home.channelTitle
import io.github.impca201.launcherforsimkl.ui.theme.SurfaceRoles
import io.github.impca201.launcherforsimkl.ui.theme.TvSpacing

/** What the settings screen shows besides the stored settings. */
data class DeviceStatus(
    val installedPlayers: Set<Player>,
    val isDefaultHome: Boolean,
    val overrideOn: Boolean,
    /** A TV (leanback) device: system home screen channels exist. */
    val isTv: Boolean,
    val systemChannels: Boolean,
    val hasClientId: Boolean,
    val version: String,
    /** ADB commands with this build's package and service names. */
    val adb: AdbCommands,
)

data class AdbCommands(
    val restricted: String,
    val enableService: String,
    val enableAccessibility: String,
    val disableStockHome: String,
    val enableStockHome: String,
)

class SettingsActions(
    val signIn: () -> Unit,
    val signOut: () -> Unit,
    val setPlayer: (Player?) -> Unit,
    val toggleChannel: (Channel) -> Unit,
    val moveChannel: (Channel, Boolean) -> Unit,
    val setHomeChannel: (Boolean) -> Unit,
    val makeDefault: () -> Unit,
    val openAccessibility: () -> Unit,
    val openDeviceSettings: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    status: DeviceStatus,
    actions: SettingsActions,
) {
    val first = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = TvSpacing.SafeHorizontal, vertical = TvSpacing.SafeVertical),
    ) {
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        section(R.string.settings_account)
        item {
            if (settings.signedIn) {
                Setting(
                    headline = settings.userName ?: stringResource(R.string.settings_signed_in),
                    supporting = stringResource(R.string.settings_sign_out_text),
                    trailing = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = stringResource(R.string.sign_out)) },
                    onClick = actions.signOut,
                    modifier = Modifier.focusRequester(first),
                )
            } else {
                Setting(
                    headline = stringResource(R.string.sign_in),
                    supporting = stringResource(if (status.hasClientId) R.string.sign_in_row_text else R.string.no_client_id_text),
                    trailing = { Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null) },
                    onClick = actions.signIn,
                    modifier = Modifier.focusRequester(first),
                )
            }
        }

        section(R.string.settings_open_in)
        val choices = listOf(null) + Player.entries
        choices.forEach { player ->
            item(key = "player-${player?.name}") {
                val installed = player == null || player in status.installedPlayers
                Setting(
                    headline =
                        when (player) {
                            null -> stringResource(R.string.player_ask)
                            Player.Stremio -> stringResource(R.string.player_stremio)
                            Player.Nuvio -> stringResource(R.string.player_nuvio)
                        },
                    supporting = if (installed) null else stringResource(R.string.player_not_installed),
                    leading = {
                        val selected = settings.player == player
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = stringResource(if (selected) R.string.choice_selected else R.string.choice_not_selected),
                        )
                    },
                    onClick = { actions.setPlayer(player) },
                )
            }
        }

        section(R.string.settings_channels)
        item { Hint(stringResource(R.string.settings_channels_hint)) }
        val order = settings.channels.order
        order.forEachIndexed { index, channel ->
            item(key = "channel-${channel.name}") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Setting(
                        headline = channelTitle(channel),
                        supporting = if (channel.personal && !settings.signedIn) stringResource(R.string.settings_channel_needs_sign_in) else null,
                        trailing = {
                            Switch(checked = channel !in settings.channels.hidden, onCheckedChange = null)
                        },
                        onClick = { actions.toggleChannel(channel) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { actions.moveChannel(channel, true) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up))
                    }
                    IconButton(onClick = { actions.moveChannel(channel, false) }, enabled = index < order.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down))
                    }
                }
            }
        }
        if (status.isTv) {
            item {
                Setting(
                    headline = stringResource(R.string.settings_home_channel),
                    supporting = stringResource(R.string.settings_home_channel_text),
                    trailing = { Switch(checked = settings.homeChannel, onCheckedChange = null) },
                    onClick = { actions.setHomeChannel(!settings.homeChannel) },
                )
            }
            item {
                Setting(
                    headline = stringResource(R.string.settings_app_channels),
                    supporting =
                        stringResource(
                            if (status.systemChannels) R.string.settings_app_channels_on else R.string.settings_app_channels_unavailable,
                        ),
                    onClick = null,
                )
            }
        }

        section(R.string.settings_home)
        item {
            Setting(
                headline = stringResource(R.string.settings_default_home),
                supporting = stringResource(if (status.isDefaultHome) R.string.settings_default_home_on else R.string.settings_default_home_off),
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = actions.makeDefault,
            )
        }
        item {
            Setting(
                headline = stringResource(R.string.settings_home_button),
                supporting = stringResource(if (status.overrideOn) R.string.settings_home_button_on else R.string.settings_home_button_off),
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = actions.openAccessibility,
            )
        }
        item {
            Setting(
                headline = stringResource(R.string.settings_device),
                supporting = stringResource(R.string.settings_device_text),
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = actions.openDeviceSettings,
            )
        }
        adbHelp(status.adb)

        item {
            // Information only, but focusable so the remote reaches the end of the list.
            Setting(
                headline = stringResource(R.string.settings_footer, stringResource(R.string.app_name), status.version),
                supporting = stringResource(R.string.settings_data_credit),
                onClick = null,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
    LaunchedEffect(Unit) { first.requestAfterLayout() }
}

private fun LazyListScope.section(title: Int) {
    item(key = "section-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 640.dp).padding(bottom = 4.dp),
    )
}

/** A settings row: Material's TV list item, at most 720dp wide. A null [onClick] is information only. */
@Composable
private fun Setting(
    headline: String,
    supporting: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick ?: {},
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading?.let { { it() } },
        trailingContent = trailing,
        modifier = modifier.widthIn(max = 720.dp),
    )
}

/** Setup with a computer, for devices where the switches above aren't enough. */
private fun LazyListScope.adbHelp(adb: AdbCommands) {
    item(key = "adb") {
        var open by remember { mutableStateOf(false) }
        Column {
            Setting(
                headline = stringResource(R.string.settings_adb),
                supporting = stringResource(R.string.settings_adb_text),
                leading = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                trailing = {
                    Icon(
                        if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(if (open) R.string.section_expanded else R.string.section_collapsed),
                    )
                },
                onClick = { open = !open },
            )
            if (open) {
                Column(
                    Modifier.widthIn(max = 720.dp).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Hint(stringResource(R.string.adb_step_restricted))
                    Command(adb.restricted)
                    Hint(stringResource(R.string.adb_step_override))
                    Command(adb.enableService)
                    Command(adb.enableAccessibility)
                    Hint(stringResource(R.string.adb_step_disable))
                    Command(adb.disableStockHome)
                    Hint(stringResource(R.string.adb_step_undo))
                    Command(adb.enableStockHome)
                }
            }
        }
    }
}

/** One command. Focusable (it does nothing when selected), so the remote can scroll to it. */
@Composable
private fun Command(text: String) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = SurfaceRoles.containerHigh),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
