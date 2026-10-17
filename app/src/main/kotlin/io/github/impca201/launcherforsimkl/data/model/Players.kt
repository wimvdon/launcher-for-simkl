package io.github.impca201.launcherforsimkl.data.model

import java.net.URLEncoder

/** The streaming apps the launcher opens titles in. */
enum class Player(
    /** Package names, the usual one first (Nuvio has a separate Google Play build). */
    val packages: List<String>,
) {
    Stremio(listOf("com.stremio.one")),
    Nuvio(listOf("com.nuvio.tv", "com.nuvio.app")),
    ;

    companion object {
        fun fromName(value: String?): Player? = entries.firstOrNull { it.name == value }
    }
}

/** Which app a title opens in, given the setting and what is installed. */
sealed interface PlayerChoice {
    data class Open(
        val player: Player,
    ) : PlayerChoice

    /** Both are installed and no default is set: ask. */
    data object Ask : PlayerChoice

    data object NoneInstalled : PlayerChoice

    companion object {
        fun decide(
            preferred: Player?,
            installed: Set<Player>,
        ): PlayerChoice =
            when {
                preferred != null && preferred in installed -> Open(preferred)
                installed.size == 1 -> Open(installed.first())
                installed.isEmpty() -> NoneInstalled
                else -> Ask
            }
    }
}

/**
 * Deep links into Stremio and Nuvio. Both open titles by IMDb id (Stremio's Cinemeta catalogue and
 * Nuvio's metadata use it):
 *
 * - Stremio (docs: Stremio/stremio-addon-sdk, "deep-links.md"): `stremio:///detail/{type}/{id}`, with
 *   the video id `{id}` for a movie or `{id}:{season}:{episode}` for an episode. Without an IMDb id
 *   Stremio's search opens: `stremio:///search?search={title}`.
 * - Nuvio (`DeepLinkParser` in NuvioMedia/NuvioTV): `nuvio://meta?type={movie|series}&id={imdb}`, or
 *   `nuvio://tmdb/{movie|tv}/{tmdb}` with only a TMDB id. Nuvio opens the show, not one episode.
 */
object DeepLinks {
    fun forTile(
        player: Player,
        tile: Tile,
    ): String? = build(player, tile.type, tile.ids, tile.title, tile.episode)

    fun build(
        player: Player,
        type: MediaType,
        ids: MediaIds,
        title: String,
        episode: EpisodeRef? = null,
    ): String? {
        val imdb = ids.imdb?.takeIf { it.matches(IMDB) }
        val tmdb = ids.tmdb?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
        val series = type.isEpisodic
        return when (player) {
            Player.Stremio ->
                when {
                    imdb == null -> "stremio:///search?search=${encode(title)}"
                    !series -> "stremio:///detail/movie/$imdb/$imdb"
                    episode != null -> "stremio:///detail/series/$imdb/$imdb:${episode.season ?: 1}:${episode.episode}"
                    else -> "stremio:///detail/series/$imdb"
                }
            Player.Nuvio ->
                when {
                    imdb != null -> "nuvio://meta?type=${if (series) "series" else "movie"}&id=$imdb"
                    tmdb != null -> "nuvio://tmdb/${if (series) "tv" else "movie"}/$tmdb"
                    else -> null
                }
        }
    }

    /** Whether a link to [player] needs ids SIMKL's lists may leave out (worth looking up first). */
    fun needsLookup(
        player: Player,
        ids: MediaIds,
    ): Boolean = ids.imdb?.matches(IMDB) != true && (player == Player.Stremio || ids.tmdb.isNullOrEmpty())

    private val IMDB = Regex("tt\\d+")

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
