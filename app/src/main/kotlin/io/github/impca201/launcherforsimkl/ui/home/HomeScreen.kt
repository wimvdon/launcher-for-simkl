package io.github.impca201.launcherforsimkl.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.SyncError
import io.github.impca201.launcherforsimkl.data.model.AppChannel
import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.data.model.AppProgram
import io.github.impca201.launcherforsimkl.data.model.Channel
import io.github.impca201.launcherforsimkl.data.model.ContentRow
import io.github.impca201.launcherforsimkl.data.model.Images
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Tile
import io.github.impca201.launcherforsimkl.ui.components.ActionTile
import io.github.impca201.launcherforsimkl.ui.components.AppTile
import io.github.impca201.launcherforsimkl.ui.components.PosterTile
import io.github.impca201.launcherforsimkl.ui.components.ProgramTile
import io.github.impca201.launcherforsimkl.ui.components.TileSize
import io.github.impca201.launcherforsimkl.ui.components.requestAfterLayout
import io.github.impca201.launcherforsimkl.ui.theme.TvSpacing

/** Everything the home screen shows. */
data class HomeState(
    val pinned: List<AppEntry> = emptyList(),
    /** False until the installed apps were read, so first focus waits for the pinned row. */
    val appsLoaded: Boolean = false,
    val rows: List<ContentRow> = emptyList(),
    val appChannels: List<AppChannel> = emptyList(),
    val signedIn: Boolean = false,
    val syncError: SyncError? = null,
)

class HomeActions(
    val openApp: (AppEntry) -> Unit,
    val appOptions: (AppEntry) -> Unit,
    val openTile: (Tile) -> Unit,
    val tileOptions: (Tile) -> Unit,
    val openProgram: (AppProgram) -> Unit,
    val allApps: () -> Unit,
    val settings: () -> Unit,
    val signIn: () -> Unit,
    val loadPicture: suspend (String, Boolean) -> android.graphics.drawable.Drawable?,
)

@Composable
fun HomeScreen(
    state: HomeState,
    actions: HomeActions,
    /** Increases each time Home is pressed while the launcher is open: back to the top. */
    homePresses: Int,
) {
    var hero by remember { mutableStateOf<Tile?>(null) }
    val listState = rememberLazyListState()
    val firstApp = remember { FocusRequester() }

    LaunchedEffect(homePresses, state.appsLoaded) {
        listState.scrollToItem(0)
        hero = null
        firstApp.requestAfterLayout()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Backdrop(hero)
        Column(Modifier.fillMaxSize()) {
            TopArea(hero, state, actions)
            val scope = rememberCoroutineScope()

            // The focused row moves to the top of the list, as on Android TV's home screens, so the
            // row above isn't left cut in half. The list stops at its ends.
            fun Modifier.scrollHereOnFocus(index: Int) = onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(index) } }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // Room below the last row, so it too can scroll up to the top.
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.RowGap),
            ) {
                val showSignIn = !state.signedIn || state.syncError == SyncError.SignedOut
                val firstRow = if (showSignIn) 2 else 1
                item(key = "apps") {
                    AppsRow(state.pinned, actions, firstApp, Modifier.scrollHereOnFocus(0)) { hero = null }
                }
                if (showSignIn) {
                    item(key = "sign-in") { SignInRow(actions.signIn, Modifier.scrollHereOnFocus(1)) { hero = null } }
                }
                itemsIndexed(state.rows, key = { _, row -> row.channel.name }) { index, row ->
                    ContentRowView(row, actions, Modifier.scrollHereOnFocus(firstRow + index)) { hero = it }
                }
                itemsIndexed(state.appChannels, key = { _, channel -> channel.key }) { index, channel ->
                    AppChannelRow(channel, actions.openProgram, Modifier.scrollHereOnFocus(firstRow + state.rows.size + index)) { hero = null }
                }
            }
        }
    }
}

/** The focused title's backdrop, fading into the background towards the rows and the text. */
@Composable
private fun Backdrop(tile: Tile?) {
    Crossfade(targetState = tile?.let { Images.fanart(it.fanart) ?: Images.poster(it.poster, large = true) }, label = "backdrop") { url ->
        if (url != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd,
                    modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.7f).fillMaxHeight(0.75f),
                )
                val background = MaterialTheme.colorScheme.background
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0.3f to background, 0.65f to background.copy(alpha = 0.55f), 1f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.45f to background.copy(alpha = 0.4f), 0.75f to background),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TopArea(
    hero: Tile?,
    state: HomeState,
    actions: HomeActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .padding(start = TvSpacing.SafeHorizontal, end = TvSpacing.SafeHorizontal, top = TvSpacing.SafeVertical),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (hero != null) HeroText(hero) else Greeting()
        }
        Column(horizontalAlignment = Alignment.End) {
            Clock()
            if (state.syncError == SyncError.Offline || state.syncError == SyncError.NoClientId) {
                Text(
                    stringResource(if (state.syncError == SyncError.Offline) R.string.home_offline else R.string.home_no_client_id),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = actions.allApps) {
                    Icon(Icons.Rounded.Apps, contentDescription = stringResource(R.string.all_apps))
                }
                IconButton(onClick = actions.settings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
        }
    }
}

@Composable
private fun HeroText(tile: Tile) {
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tile.title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta =
            listOfNotNull(
                stringResource(
                    when (tile.type) {
                        MediaType.Tv -> R.string.type_tv
                        MediaType.Anime -> R.string.type_anime
                        MediaType.Movie -> R.string.type_movie
                    },
                ),
                tile.year?.toString(),
                tile.episode?.let { ep -> listOfNotNull(ep.label, tile.episodeTitle).joinToString(" · ") },
            ).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        tile.overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = TvSpacing.SafeHorizontal, bottom = 10.dp),
    )
}

@Composable
private fun AppsRow(
    pinned: List<AppEntry>,
    actions: HomeActions,
    firstFocus: FocusRequester,
    modifier: Modifier,
    onFocused: () -> Unit,
) {
    Column(modifier) {
        RowTitle(stringResource(R.string.row_apps))
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSpacing.SafeHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.TileGap),
        ) {
            itemsIndexed(pinned, key = { _, app -> app.packageName }) { index, app ->
                AppTile(
                    app = app,
                    loadPicture = actions.loadPicture,
                    onClick = { actions.openApp(app) },
                    onLongClick = { actions.appOptions(app) },
                    onFocused = onFocused,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            item(key = "all-apps") {
                ActionTile(
                    icon = Icons.Rounded.Apps,
                    label = stringResource(R.string.all_apps),
                    supporting = if (pinned.isEmpty()) stringResource(R.string.all_apps_pin_hint) else null,
                    onClick = actions.allApps,
                    onFocused = onFocused,
                    modifier = if (pinned.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SignInRow(
    onSignIn: () -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
) {
    Column(modifier) {
        RowTitle(stringResource(R.string.row_simkl))
        Row(Modifier.padding(horizontal = TvSpacing.SafeHorizontal, vertical = 8.dp)) {
            ActionTile(
                icon = Icons.AutoMirrored.Rounded.Login,
                label = stringResource(R.string.sign_in),
                supporting = stringResource(R.string.sign_in_row_text),
                onClick = onSignIn,
                onFocused = onFocused,
                width = 320.dp,
                height = TileSize.AppHeight + 24.dp,
            )
        }
    }
}

@Composable
fun channelTitle(channel: Channel): String =
    stringResource(
        when (channel) {
            Channel.ContinueWatching -> R.string.channel_continue_watching
            Channel.UpNext -> R.string.channel_up_next
            Channel.Watchlist -> R.string.channel_watchlist
            Channel.TrendingShows -> R.string.channel_trending_shows
            Channel.TrendingMovies -> R.string.channel_trending_movies
            Channel.TrendingAnime -> R.string.channel_trending_anime
        },
    )

@Composable
private fun ContentRowView(
    row: ContentRow,
    actions: HomeActions,
    modifier: Modifier,
    onFocused: (Tile) -> Unit,
) {
    Column(modifier) {
        RowTitle(channelTitle(row.channel))
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSpacing.SafeHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.TileGap),
        ) {
            items(row.tiles, key = { it.key }) { tile ->
                val description = listOfNotNull(tile.title, tile.episode?.label).joinToString(", ")
                PosterTile(
                    tile = tile,
                    description = description,
                    onClick = { actions.openTile(tile) },
                    onLongClick = { actions.tileOptions(tile) },
                    onFocused = { onFocused(tile) },
                )
            }
        }
        if (row.channel.personal.not()) {
            Text(
                stringResource(R.string.trending_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = TvSpacing.SafeHorizontal, top = 2.dp),
            )
        }
    }
}

@Composable
private fun AppChannelRow(
    channel: AppChannel,
    onOpen: (AppProgram) -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
) {
    Column(modifier) {
        RowTitle(channel.name)
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSpacing.SafeHorizontal, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.TileGap),
            modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
        ) {
            items(channel.programs, key = { it.key }) { program ->
                ProgramTile(program.title, program.image, program.portrait, onClick = { onOpen(program) })
            }
        }
    }
}

/** Shown while no title has focus: today's date. */
@Composable
private fun Greeting() {
    Text(rememberDate(), style = MaterialTheme.typography.headlineLarge, maxLines = 1)
}
