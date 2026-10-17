package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.model.Channel
import io.github.impca201.launcherforsimkl.data.model.ChannelConfig
import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.LibraryItem
import io.github.impca201.launcherforsimkl.data.model.LibrarySnapshot
import io.github.impca201.launcherforsimkl.data.model.ListStatus
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Playback
import io.github.impca201.launcherforsimkl.data.model.Rows
import io.github.impca201.launcherforsimkl.data.model.TrendingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChannelsTest {
    private val now = Instant.parse("2024-06-01T12:00:00Z")

    private fun show(
        id: Long,
        status: ListStatus = ListStatus.Watching,
        next: EpisodeRef? = EpisodeRef(1, 2),
        nextDate: String? = null,
        lastWatched: String? = null,
        added: String? = null,
        type: MediaType = MediaType.Tv,
    ) = LibraryItem(type, MediaIds(id, imdb = "tt$id"), "T$id", status = status, nextToWatch = next, nextDate = nextDate, lastWatchedAt = lastWatched, addedAt = added)

    @Test
    fun `saved order keeps known channels and appends new ones`() {
        val config = ChannelConfig.parse("Watchlist,Bogus,UpNext,Watchlist", setOf("UpNext", "Nope"))
        assertEquals(Channel.Watchlist, config.order[0])
        assertEquals(Channel.UpNext, config.order[1])
        assertEquals(Channel.entries.toSet(), config.order.toSet())
        assertEquals(Channel.entries.size, config.order.size)
        assertEquals(setOf(Channel.UpNext), config.hidden)
        assertEquals(config, ChannelConfig.parse(config.orderString(), config.hidden.map { it.name }.toSet()))
    }

    @Test
    fun `channels move one place and stop at the ends`() {
        val config = ChannelConfig()
        assertEquals(Channel.UpNext, config.moved(Channel.UpNext, up = true).order[0])
        assertEquals(config, config.moved(Channel.ContinueWatching, up = true))
        assertEquals(config, config.moved(Channel.entries.last(), up = false))
        assertTrue(Channel.Watchlist in config.toggled(Channel.Watchlist).hidden)
        assertEquals(config, config.toggled(Channel.Watchlist).toggled(Channel.Watchlist))
    }

    @Test
    fun `continue watching shows paused titles, the latest first, with the library's art`() {
        val library =
            LibrarySnapshot(
                items = listOf(show(1).copy(poster = "p1")),
                playback =
                    listOf(
                        Playback(10, MediaType.Tv, MediaIds(1), "T1", 40.0, "2024-05-01T00:00:00Z", EpisodeRef(1, 3)),
                        Playback(11, MediaType.Movie, MediaIds(2, imdb = "tt2"), "M2", 10.0, "2024-05-02T00:00:00Z"),
                    ),
            )
        val tiles = Rows.continueWatching(library)
        assertEquals(listOf("playback:11", "playback:10"), tiles.map { it.key })
        assertEquals("p1", tiles[1].poster)
        assertEquals("tt1", tiles[1].ids.imdb)
        assertEquals(EpisodeRef(1, 3), tiles[1].episode)
    }

    @Test
    fun `up next leaves out unaired, paused and finished shows`() {
        val library =
            LibrarySnapshot(
                items =
                    listOf(
                        show(1, lastWatched = "2024-05-01T00:00:00Z"),
                        show(2, lastWatched = "2024-05-03T00:00:00Z"),
                        show(3, nextDate = "2024-07-01T00:00:00Z"),
                        show(4),
                        show(5, status = ListStatus.Completed),
                        show(6, next = null),
                        show(7, type = MediaType.Movie),
                    ),
                playback = listOf(Playback(1, MediaType.Tv, MediaIds(4), "T4", 5.0)),
            )
        assertEquals(listOf("next:Tv:2", "next:Tv:1"), Rows.upNext(library, now).map { it.key })
    }

    @Test
    fun `watchlist is plan to watch, the latest added first`() {
        val library =
            LibrarySnapshot(
                items =
                    listOf(
                        show(1, ListStatus.PlanToWatch, added = "2024-01-01T00:00:00Z"),
                        show(2, ListStatus.PlanToWatch, added = "2024-02-01T00:00:00Z", type = MediaType.Movie),
                        show(3, ListStatus.OnHold),
                    ),
            )
        assertEquals(listOf(2L, 1L), Rows.watchlist(library).map { it.ids.simkl })
    }

    @Test
    fun `rows follow the user's order, skip hidden and empty channels and cap their length`() {
        val trending = (1..50L).map { TrendingItem(MediaType.Movie, MediaIds(it), "M$it") }
        val config = ChannelConfig(order = listOf(Channel.TrendingMovies, Channel.TrendingShows, Channel.Watchlist) + Channel.entries, hidden = setOf(Channel.Watchlist))
        val rows = Rows.build(config.copy(order = config.order.distinct()), null, mapOf(MediaType.Movie to trending), now)
        assertEquals(listOf(Channel.TrendingMovies), rows.map { it.channel })
        assertEquals(Rows.MAX_TILES, rows[0].tiles.size)
    }
}
