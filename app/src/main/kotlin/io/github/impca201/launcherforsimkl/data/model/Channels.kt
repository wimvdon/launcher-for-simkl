package io.github.impca201.launcherforsimkl.data.model

import java.time.Instant

/** The SIMKL rows ("channels") the home screen can show below the apps. */
enum class Channel(
    /** Needs a signed-in SIMKL account. */
    val personal: Boolean,
) {
    ContinueWatching(true),
    UpNext(true),
    Watchlist(true),
    TrendingShows(false),
    TrendingMovies(false),
    TrendingAnime(false),
    ;

    companion object {
        val defaultOrder = entries.toList()

        fun fromName(value: String): Channel? = entries.firstOrNull { it.name == value }
    }
}

/** Which channels the home screen shows, in the user's order. */
data class ChannelConfig(
    /** Every channel, in the user's order. */
    val order: List<Channel> = Channel.defaultOrder,
    val hidden: Set<Channel> = emptySet(),
) {
    val visible: List<Channel> get() = order.filter { it !in hidden }

    fun toggled(channel: Channel): ChannelConfig = copy(hidden = if (channel in hidden) hidden - channel else hidden + channel)

    /** Moves [channel] one place up or down. */
    fun moved(
        channel: Channel,
        up: Boolean,
    ): ChannelConfig {
        val index = order.indexOf(channel)
        val other = if (up) index - 1 else index + 1
        if (index < 0 || other !in order.indices) return this
        val list = order.toMutableList()
        list[index] = list[other]
        list[other] = channel
        return copy(order = list)
    }

    /** Saved as "Name,Name,…"; hidden channels as a set of names. */
    fun orderString(): String = order.joinToString(",") { it.name }

    companion object {
        /**
         * Reads the saved order, ignoring names this version doesn't know, and appending channels
         * that are newer than the saved order (so an update adds its new rows at the end).
         */
        fun parse(
            order: String?,
            hidden: Set<String>?,
        ): ChannelConfig {
            val saved = order?.split(",")?.mapNotNull { Channel.fromName(it.trim()) }?.distinct().orEmpty()
            return ChannelConfig(
                order = saved + Channel.defaultOrder.filter { it !in saved },
                hidden = hidden.orEmpty().mapNotNull { Channel.fromName(it) }.toSet(),
            )
        }
    }
}

/** A row on the home screen. */
data class ContentRow(
    val channel: Channel,
    val tiles: List<Tile>,
)

/** Builds the home screen's SIMKL rows from the library, paused playback and trending lists. */
object Rows {
    const val MAX_TILES = 30

    fun build(
        config: ChannelConfig,
        library: LibrarySnapshot?,
        trending: Map<MediaType, List<TrendingItem>>,
        now: Instant = Instant.now(),
    ): List<ContentRow> =
        config.visible.mapNotNull { channel ->
            val tiles =
                when (channel) {
                    Channel.ContinueWatching -> continueWatching(library)
                    Channel.UpNext -> upNext(library, now)
                    Channel.Watchlist -> watchlist(library)
                    Channel.TrendingShows -> trending[MediaType.Tv].orEmpty().map(::tile)
                    Channel.TrendingMovies -> trending[MediaType.Movie].orEmpty().map(::tile)
                    Channel.TrendingAnime -> trending[MediaType.Anime].orEmpty().map(::tile)
                }.take(MAX_TILES)
            ContentRow(channel, tiles).takeIf { tiles.isNotEmpty() }
        }

    /** Paused movies and episodes, the most recently paused first. */
    fun continueWatching(library: LibrarySnapshot?): List<Tile> {
        library ?: return emptyList()
        val items = library.items.associateBy { "${it.type.name}:${it.ids.simkl}" }
        return library.playback
            .sortedByDescending { Dates.parse(it.pausedAt) ?: Instant.EPOCH }
            .map { p ->
                val item = items["${p.type.name}:${p.ids.simkl}"]
                Tile(
                    key = "playback:${p.id}",
                    type = p.type,
                    ids = mergeIds(p.ids, item?.ids),
                    title = p.title,
                    year = item?.year,
                    poster = item?.poster,
                    fanart = item?.fanart,
                    overview = item?.overview,
                    episode = p.episode,
                    episodeTitle = p.episodeTitle,
                    progress = p.progress,
                )
            }
    }

    /**
     * Shows the user is watching whose next episode has aired, the most recently watched first.
     * Shows that already have a paused episode are in Continue watching instead.
     */
    fun upNext(
        library: LibrarySnapshot?,
        now: Instant,
    ): List<Tile> {
        library ?: return emptyList()
        val paused = library.playback.map { it.ids.simkl }.toSet()
        return library.items
            .filter { it.type.isEpisodic && it.status == ListStatus.Watching && it.nextToWatch != null && it.ids.simkl !in paused }
            .filter { item -> Dates.parse(item.nextDate)?.let { !it.isAfter(now) } ?: true }
            .sortedByDescending { Dates.parse(it.lastWatchedAt) ?: Dates.parse(it.addedAt) ?: Instant.EPOCH }
            .map { item ->
                tile(item).copy(key = "next:${item.key}", episode = item.nextToWatch, episodeTitle = item.nextTitle)
            }
    }

    /** Titles on the Plan to watch list, the most recently added first. */
    fun watchlist(library: LibrarySnapshot?): List<Tile> =
        library?.items
            ?.filter { it.status == ListStatus.PlanToWatch }
            ?.sortedByDescending { Dates.parse(it.addedAt) ?: Instant.EPOCH }
            ?.map(::tile)
            .orEmpty()

    fun tile(item: LibraryItem) =
        Tile(
            key = item.key,
            type = item.type,
            ids = item.ids,
            title = item.title,
            year = item.year,
            poster = item.poster,
            fanart = item.fanart,
            overview = item.overview,
        )

    fun tile(item: TrendingItem) =
        Tile(
            key = "${item.type.name}:${item.ids.simkl}",
            type = item.type,
            ids = item.ids,
            title = item.title,
            year = item.year,
            poster = item.poster,
            fanart = item.fanart,
            overview = item.overview,
        )

    private fun mergeIds(
        a: MediaIds,
        b: MediaIds?,
    ) = MediaIds(simkl = a.simkl, imdb = a.imdb ?: b?.imdb, tmdb = a.tmdb ?: b?.tmdb)
}

/** A channel another app published for the home screen (see `tv/SystemChannels`). */
data class AppChannel(
    val key: String,
    val name: String,
    val packageName: String,
    val programs: List<AppProgram>,
)

data class AppProgram(
    val key: String,
    val title: String,
    val image: String?,
    /** A 2:3 poster; otherwise the picture is shown 16:9. */
    val portrait: Boolean,
    val intentUri: String?,
)
