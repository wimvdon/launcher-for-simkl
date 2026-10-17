package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.model.DeepLinks
import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Player
import io.github.impca201.launcherforsimkl.data.model.PlayerChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The link formats Stremio (deep-links.md) and Nuvio (DeepLinkParser) document. */
class DeepLinksTest {
    private val movie = MediaIds(1, imdb = "tt0066921", tmdb = "185")
    private val show = MediaIds(2, imdb = "tt0108778", tmdb = "1668")

    @Test
    fun `Stremio opens movies, shows and episodes by IMDb id`() {
        assertEquals("stremio:///detail/movie/tt0066921/tt0066921", DeepLinks.build(Player.Stremio, MediaType.Movie, movie, "A"))
        assertEquals("stremio:///detail/series/tt0108778", DeepLinks.build(Player.Stremio, MediaType.Tv, show, "Friends"))
        assertEquals(
            "stremio:///detail/series/tt0108778/tt0108778:1:1",
            DeepLinks.build(Player.Stremio, MediaType.Tv, show, "Friends", EpisodeRef(1, 1)),
        )
        // Anime episodes without a season are season 1 in Cinemeta.
        assertEquals(
            "stremio:///detail/series/tt0108778/tt0108778:1:7",
            DeepLinks.build(Player.Stremio, MediaType.Anime, show, "X", EpisodeRef(null, 7)),
        )
    }

    @Test
    fun `Stremio searches by title without an IMDb id`() {
        assertEquals(
            "stremio:///search?search=Fullmetal%20Alchemist%3A%20Brotherhood",
            DeepLinks.build(Player.Stremio, MediaType.Anime, MediaIds(3), "Fullmetal Alchemist: Brotherhood"),
        )
    }

    @Test
    fun `Nuvio opens titles by IMDb id, else by TMDB id`() {
        assertEquals("nuvio://meta?type=movie&id=tt0066921", DeepLinks.build(Player.Nuvio, MediaType.Movie, movie, "A"))
        assertEquals("nuvio://meta?type=series&id=tt0108778", DeepLinks.build(Player.Nuvio, MediaType.Tv, show, "F", EpisodeRef(1, 1)))
        assertEquals("nuvio://tmdb/tv/1668", DeepLinks.build(Player.Nuvio, MediaType.Anime, MediaIds(2, tmdb = "1668"), "F"))
        assertEquals("nuvio://tmdb/movie/185", DeepLinks.build(Player.Nuvio, MediaType.Movie, MediaIds(1, tmdb = "185"), "A"))
        assertNull(DeepLinks.build(Player.Nuvio, MediaType.Movie, MediaIds(1, imdb = "nope", tmdb = "x1"), "A"))
    }

    @Test
    fun `ids are looked up only when the link needs them`() {
        assertFalse(DeepLinks.needsLookup(Player.Stremio, movie))
        assertTrue(DeepLinks.needsLookup(Player.Stremio, MediaIds(1, tmdb = "5")))
        assertFalse(DeepLinks.needsLookup(Player.Nuvio, MediaIds(1, tmdb = "5")))
        assertTrue(DeepLinks.needsLookup(Player.Nuvio, MediaIds(1)))
    }

    @Test
    fun `the setting wins when that app is installed, otherwise the only one, otherwise ask`() {
        val both = setOf(Player.Stremio, Player.Nuvio)
        assertEquals(PlayerChoice.Open(Player.Nuvio), PlayerChoice.decide(Player.Nuvio, both))
        assertEquals(PlayerChoice.Ask, PlayerChoice.decide(null, both))
        assertEquals(PlayerChoice.Open(Player.Stremio), PlayerChoice.decide(Player.Nuvio, setOf(Player.Stremio)))
        assertEquals(PlayerChoice.Open(Player.Stremio), PlayerChoice.decide(null, setOf(Player.Stremio)))
        assertEquals(PlayerChoice.NoneInstalled, PlayerChoice.decide(Player.Stremio, emptySet()))
    }
}
