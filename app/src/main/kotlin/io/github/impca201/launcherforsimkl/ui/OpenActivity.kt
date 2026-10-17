package io.github.impca201.launcherforsimkl.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import io.github.impca201.launcherforsimkl.LauncherApp
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Player
import io.github.impca201.launcherforsimkl.data.model.PlayerChoice
import io.github.impca201.launcherforsimkl.data.model.Tile
import io.github.impca201.launcherforsimkl.ui.components.DialogOption
import io.github.impca201.launcherforsimkl.ui.components.OptionsDialog
import io.github.impca201.launcherforsimkl.ui.theme.LauncherDialogTheme
import kotlinx.coroutines.launch

/**
 * Opens a title from the launcher's channel on the system home screen in Stremio or Nuvio. Shows
 * nothing unless it has to ask which app, or neither is installed.
 */
class OpenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LauncherApp
        val tile = tileFrom(intent) ?: return finish()
        lifecycleScope.launch {
            val installed = app.apps.installedPlayers()
            when (val choice = PlayerChoice.decide(app.settings.current().player, installed)) {
                is PlayerChoice.Open -> {
                    app.players.open(choice.player, tile)
                    finish()
                }
                PlayerChoice.Ask, PlayerChoice.NoneInstalled ->
                    setContent {
                        LauncherDialogTheme {
                            if (choice == PlayerChoice.Ask) {
                                OptionsDialog(
                                    title = tile.title,
                                    message = stringResource(R.string.open_in_title),
                                    options =
                                        Player.entries.filter { it in installed }.map { player ->
                                            DialogOption(
                                                label = stringResource(if (player == Player.Stremio) R.string.player_stremio else R.string.player_nuvio),
                                                icon = Icons.Rounded.PlayArrow,
                                            ) {
                                                lifecycleScope.launch {
                                                    app.players.open(player, tile)
                                                    finish()
                                                }
                                            }
                                        },
                                    onDismiss = ::finish,
                                )
                            } else {
                                OptionsDialog(
                                    title = stringResource(R.string.no_player_title),
                                    message = stringResource(R.string.no_player_text),
                                    options = listOf(DialogOption(stringResource(R.string.ok), onClick = ::finish)),
                                    onDismiss = ::finish,
                                )
                            }
                        }
                    }
            }
        }
    }

    companion object {
        private const val TYPE = "type"
        private const val SIMKL = "simkl"
        private const val IMDB = "imdb"
        private const val TMDB = "tmdb"
        private const val TITLE = "title"
        private const val SEASON = "season"
        private const val EPISODE = "episode"

        fun intent(
            context: Context,
            tile: Tile,
        ): Intent =
            Intent(context, OpenActivity::class.java)
                .putExtra(TYPE, tile.type.name)
                .putExtra(SIMKL, tile.ids.simkl)
                .putExtra(IMDB, tile.ids.imdb)
                .putExtra(TMDB, tile.ids.tmdb)
                .putExtra(TITLE, tile.title)
                .apply {
                    tile.episode?.let { ep ->
                        ep.season?.let { putExtra(SEASON, it) }
                        putExtra(EPISODE, ep.episode)
                    }
                }

        /** The title in an intent from [intent]; null when it is incomplete (another app's intent). */
        fun tileFrom(intent: Intent?): Tile? {
            intent ?: return null
            val type = MediaType.entries.firstOrNull { it.name == intent.getStringExtra(TYPE) } ?: return null
            val simkl = intent.getLongExtra(SIMKL, -1).takeIf { it > 0 } ?: return null
            val title = intent.getStringExtra(TITLE) ?: return null
            val episode = intent.getIntExtra(EPISODE, -1).takeIf { it > 0 }
            return Tile(
                key = "${type.name}:$simkl",
                type = type,
                ids = MediaIds(simkl, intent.getStringExtra(IMDB), intent.getStringExtra(TMDB)),
                title = title,
                year = null,
                poster = null,
                fanart = null,
                overview = null,
                episode = episode?.let { EpisodeRef(intent.getIntExtra(SEASON, -1).takeIf { s -> s >= 0 }, it) },
            )
        }
    }
}
