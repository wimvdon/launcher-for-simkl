package io.github.impca201.launcherforsimkl.ui

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.impca201.launcherforsimkl.BuildConfig
import io.github.impca201.launcherforsimkl.LauncherApp
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.ContentState
import io.github.impca201.launcherforsimkl.data.LauncherSettings
import io.github.impca201.launcherforsimkl.data.SignInFlow
import io.github.impca201.launcherforsimkl.data.model.AppChannel
import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.data.model.PinnedApps
import io.github.impca201.launcherforsimkl.data.model.Player
import io.github.impca201.launcherforsimkl.data.model.PlayerChoice
import io.github.impca201.launcherforsimkl.data.model.Rows
import io.github.impca201.launcherforsimkl.data.model.Tile
import io.github.impca201.launcherforsimkl.home.DefaultHome
import io.github.impca201.launcherforsimkl.home.HomeButtonService
import io.github.impca201.launcherforsimkl.ui.apps.AllAppsScreen
import io.github.impca201.launcherforsimkl.ui.components.DialogOption
import io.github.impca201.launcherforsimkl.ui.components.OptionsDialog
import io.github.impca201.launcherforsimkl.ui.home.HomeActions
import io.github.impca201.launcherforsimkl.ui.home.HomeScreen
import io.github.impca201.launcherforsimkl.ui.home.HomeState
import io.github.impca201.launcherforsimkl.ui.settings.AdbCommands
import io.github.impca201.launcherforsimkl.ui.settings.DeviceStatus
import io.github.impca201.launcherforsimkl.ui.settings.SettingsActions
import io.github.impca201.launcherforsimkl.ui.settings.SettingsScreen
import io.github.impca201.launcherforsimkl.ui.signin.SignInScreen
import kotlinx.coroutines.launch

enum class Route { Home, Apps, Settings, SignIn }

private sealed interface Popup {
    data class AppOptions(
        val app: AppEntry,
    ) : Popup

    data class OpenWith(
        val tile: Tile,
    ) : Popup

    data object NoPlayer : Popup

    data object ConfirmSignOut : Popup

    data object NoHomeChoice : Popup
}

/**
 * The launcher's screens. [resumes] counts how often the launcher came to the front (device state
 * such as installed apps and the Home button override is read again then); [homePresses] how often
 * Home was pressed while it was open.
 */
@Composable
fun AppRoot(
    app: LauncherApp,
    resumes: Int,
    homePresses: Int,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf(Route.Home) }
    var popup by remember { mutableStateOf<Popup?>(null) }

    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val content by app.content.state.collectAsStateWithLifecycle()
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val appChannels by produceState(emptyList<AppChannel>(), resumes) { value = app.systemChannels.load() }
    val installedPlayers = remember(resumes, apps) { app.apps.installedPlayers() }

    // Home pressed: back to the home screen's top.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            popup = null
        }
    }
    // The first start pins the streaming apps (and the store), so the apps row isn't empty.
    LaunchedEffect(settings?.pinned == null, apps) {
        val installed = apps ?: return@LaunchedEffect
        if (settings != null && settings?.pinned == null) app.settings.setPinned(PinnedApps.defaults(installed))
    }

    val s = settings ?: return

    fun openTile(tile: Tile) {
        when (val choice = PlayerChoice.decide(s.player, installedPlayers)) {
            is PlayerChoice.Open -> scope.launch { app.players.open(choice.player, tile) }
            PlayerChoice.Ask -> popup = Popup.OpenWith(tile)
            PlayerChoice.NoneInstalled -> popup = Popup.NoPlayer
        }
    }

    BackHandler(enabled = stack.size > 1 || popup != null) {
        if (popup != null) popup = null else stack.removeAt(stack.lastIndex)
    }
    // The home screen is where Back ends: a launcher has nothing behind it.
    BackHandler(enabled = stack.size == 1 && popup == null) { }

    // A short fade-in for each screen. Not AnimatedContent: focus requests fail while it animates,
    // and on a TV the focus must land on the new screen at once.
    FadeIn(stack.last()) { route ->
        when (route) {
            Route.Home ->
                HomeScreen(
                    state = homeState(s, content, apps, appChannels),
                    actions =
                        HomeActions(
                            openApp = { app.apps.open(it.packageName) },
                            appOptions = { popup = Popup.AppOptions(it) },
                            openTile = ::openTile,
                            tileOptions = { popup = Popup.OpenWith(it) },
                            openProgram = { p -> app.systemChannels.intentFor(p)?.let { runCatching { context.startActivity(it) } } },
                            allApps = { stack.add(Route.Apps) },
                            settings = { stack.add(Route.Settings) },
                            signIn = { stack.add(Route.SignIn) },
                            loadPicture = app.apps::picture,
                        ),
                    homePresses = homePresses,
                )
            Route.Apps ->
                AllAppsScreen(
                    apps = apps,
                    pinned = s.pinned.orEmpty().toSet(),
                    loadPicture = app.apps::picture,
                    onOpen = { app.apps.open(it.packageName) },
                    onOptions = { popup = Popup.AppOptions(it) },
                )
            Route.Settings ->
                SettingsScreen(
                    settings = s,
                    status = deviceStatus(app, installedPlayers, resumes),
                    actions =
                        SettingsActions(
                            signIn = { stack.add(Route.SignIn) },
                            signOut = { popup = Popup.ConfirmSignOut },
                            setPlayer = { scope.launch { app.settings.setPlayer(it) } },
                            toggleChannel = { c -> scope.launch { app.settings.setChannels(s.channels.toggled(c)) } },
                            moveChannel = { c, up -> scope.launch { app.settings.setChannels(s.channels.moved(c, up)) } },
                            setHomeChannel = { scope.launch { app.settings.setHomeChannel(it) } },
                            makeDefault = { if (!DefaultHome.request(context as Activity)) popup = Popup.NoHomeChoice },
                            openAccessibility = { DefaultHome.openAccessibilitySettings(context as Activity) },
                            openDeviceSettings = { DefaultHome.openDeviceSettings(context as Activity) },
                        ),
                )
            Route.SignIn ->
                SignInScreen(
                    flow = remember { SignInFlow(app.api, app.settings, app.content) },
                    onDone = { stack.remove(Route.SignIn) },
                    onCancel = { stack.remove(Route.SignIn) },
                )
        }
    }

    when (val p = popup) {
        null -> Unit
        is Popup.AppOptions -> AppOptionsDialog(p.app, s, app) { popup = null }
        is Popup.OpenWith ->
            OptionsDialog(
                title = p.tile.title,
                message = stringResource(R.string.open_in_title),
                options =
                    Player.entries.map { player ->
                        val installed = player in installedPlayers
                        DialogOption(
                            label = stringResource(if (player == Player.Stremio) R.string.player_stremio else R.string.player_nuvio),
                            icon = Icons.Rounded.PlayArrow,
                            supporting = if (installed) null else stringResource(R.string.player_not_installed),
                            enabled = installed,
                        ) {
                            popup = null
                            scope.launch { app.players.open(player, p.tile) }
                        }
                    },
                onDismiss = { popup = null },
            )
        Popup.NoPlayer ->
            OptionsDialog(
                title = stringResource(R.string.no_player_title),
                message = stringResource(R.string.no_player_text),
                options = listOf(DialogOption(stringResource(R.string.ok)) { popup = null }),
                onDismiss = { popup = null },
            )
        Popup.ConfirmSignOut ->
            OptionsDialog(
                title = stringResource(R.string.sign_out_title),
                message = stringResource(R.string.sign_out_text),
                options =
                    listOf(
                        DialogOption(stringResource(R.string.cancel)) { popup = null },
                        DialogOption(stringResource(R.string.sign_out), icon = Icons.AutoMirrored.Rounded.Logout) {
                            popup = null
                            scope.launch { app.content.signOut() }
                        },
                    ),
                onDismiss = { popup = null },
            )
        Popup.NoHomeChoice ->
            OptionsDialog(
                title = stringResource(R.string.no_home_choice_title),
                message = stringResource(R.string.no_home_choice_text),
                options =
                    listOf(
                        DialogOption(stringResource(R.string.settings_home_button), icon = Icons.AutoMirrored.Rounded.ArrowForward) {
                            popup = null
                            DefaultHome.openAccessibilitySettings(context as Activity)
                        },
                        DialogOption(stringResource(R.string.cancel)) { popup = null },
                    ),
                onDismiss = { popup = null },
            )
    }
}

@Composable
private fun <T> FadeIn(
    target: T,
    content: @Composable (T) -> Unit,
) {
    key(target) {
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMillis = 150)) }
        Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content(target) }
    }
}

private fun homeState(
    s: LauncherSettings,
    content: ContentState,
    apps: List<AppEntry>?,
    appChannels: List<AppChannel>,
) = HomeState(
    pinned = PinnedApps.resolve(s.pinned.orEmpty(), apps.orEmpty()),
    appsLoaded = apps != null && s.pinned != null,
    rows = Rows.build(if (s.signedIn) s.channels else s.channels.copy(hidden = s.channels.hidden + personal), content.library, content.trending),
    appChannels = appChannels,
    signedIn = s.signedIn,
    syncError = content.error,
)

private val personal = io.github.impca201.launcherforsimkl.data.model.Channel.entries.filter { it.personal }.toSet()

@Composable
private fun deviceStatus(
    app: LauncherApp,
    installedPlayers: Set<Player>,
    resumes: Int,
): DeviceStatus {
    val pkg = app.packageName
    val service = HomeButtonService.flattened(app)
    return remember(resumes, installedPlayers) {
        DeviceStatus(
            installedPlayers = installedPlayers,
            isDefaultHome = DefaultHome.isDefault(app),
            overrideOn = HomeButtonService.isEnabled(app),
            isTv = app.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            systemChannels = app.systemChannels.available,
            hasClientId = app.api.hasClientId,
            version = BuildConfig.VERSION_NAME,
            adb =
                AdbCommands(
                    restricted = "adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",
                    enableService = "adb shell settings put secure enabled_accessibility_services $service",
                    enableAccessibility = "adb shell settings put secure accessibility_enabled 1",
                    disableStockHome = "adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx",
                    enableStockHome = "adb shell pm enable com.google.android.apps.tv.launcherx",
                ),
        )
    }
}

@Composable
private fun AppOptionsDialog(
    entry: AppEntry,
    s: LauncherSettings,
    app: LauncherApp,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pinned = s.pinned.orEmpty()
    val isPinned = entry.packageName in pinned
    val index = pinned.indexOf(entry.packageName)
    fun save(list: List<String>) {
        onDismiss()
        scope.launch { app.settings.setPinned(list) }
    }
    val options =
        buildList {
            add(
                DialogOption(stringResource(R.string.app_open), icon = Icons.Rounded.PlayArrow) {
                    onDismiss()
                    app.apps.open(entry.packageName)
                },
            )
            if (isPinned) {
                add(DialogOption(stringResource(R.string.app_unpin), icon = Icons.Rounded.PushPin) { save(PinnedApps.unpin(pinned, entry.packageName)) })
                if (index > 0) {
                    add(
                        DialogOption(stringResource(R.string.app_move_left), icon = Icons.AutoMirrored.Rounded.ArrowBack) {
                            save(PinnedApps.move(pinned, entry.packageName, left = true))
                        },
                    )
                }
                if (index < pinned.lastIndex) {
                    add(
                        DialogOption(stringResource(R.string.app_move_right), icon = Icons.AutoMirrored.Rounded.ArrowForward) {
                            save(PinnedApps.move(pinned, entry.packageName, left = false))
                        },
                    )
                }
            } else {
                add(DialogOption(stringResource(R.string.app_pin), icon = Icons.Rounded.PushPin) { save(PinnedApps.pin(pinned, entry.packageName)) })
            }
            add(
                DialogOption(stringResource(R.string.app_info), icon = Icons.Rounded.Info) {
                    onDismiss()
                    app.apps.openInfo(entry.packageName)
                },
            )
        }
    OptionsDialog(title = entry.label, options = options, onDismiss = onDismiss)
}
