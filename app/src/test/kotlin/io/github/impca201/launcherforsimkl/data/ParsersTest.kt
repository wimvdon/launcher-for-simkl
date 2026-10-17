package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.api.Parsers
import io.github.impca201.launcherforsimkl.data.api.PinStatus
import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.ListStatus
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers against SIMKL's documented response shapes. */
class ParsersTest {
    private fun json(text: String) = Json.parseToJsonElement(text)

    private val allItems =
        json(
            """
            {
              "shows": [{
                "added_to_watchlist_at": "2024-01-01T10:00:00Z",
                "last_watched_at": "2024-05-01T20:00:00Z",
                "status": "watching",
                "next_to_watch": "S01E06",
                "next_to_watch_info": { "title": "The Wolf", "season": 1, "episode": 6, "date": "2024-05-02T01:00:00Z" },
                "show": { "title": "Show A", "poster": "12/abc", "year": 2023, "ids": { "simkl": 101, "slug": "show-a", "imdb": "tt1234567", "tmdb": 55 } }
              }],
              "anime": [{
                "status": "plantowatch",
                "show": { "title": "Anime B", "ids": { "simkl": 202, "mal": "9" } }
              }],
              "movies": [{
                "status": "plantowatch",
                "added_to_watchlist_at": "2024-03-01T10:00:00Z",
                "movie": { "title": "Movie C", "year": 2020, "ids": { "simkl": 303, "imdb": "tt7654321" } }
              }, {
                "status": "nonsense",
                "movie": { "title": "Broken", "ids": { "simkl": 404 } }
              }]
            }
            """,
        )

    @Test
    fun `library reads every type and skips unknown statuses`() {
        val items = Parsers.library(allItems)
        assertEquals(listOf("Tv:101", "Anime:202", "Movie:303"), items.map { it.key })
        val show = items[0]
        assertEquals(ListStatus.Watching, show.status)
        assertEquals(EpisodeRef(1, 6), show.nextToWatch)
        assertEquals("The Wolf", show.nextTitle)
        assertEquals(MediaIds(101, "tt1234567", "55"), show.ids)
        assertEquals(2023, show.year)
        assertNull(items[1].ids.imdb)
    }

    @Test
    fun `next episode falls back to the S01E05 label`() {
        val entry =
            json("""{"status":"watching","next_to_watch":"S02E03","show":{"title":"X","ids":{"simkl":1}}}""")
        val item = Parsers.libraryItem(entry as kotlinx.serialization.json.JsonObject, MediaType.Tv)!!
        assertEquals(EpisodeRef(2, 3), item.nextToWatch)
    }

    @Test
    fun `ids that are not IMDb ids are dropped`() {
        val ids = Parsers.ids(json("""{"simkl_id": 7, "imdb": "", "tmdb": "99"}""") as kotlinx.serialization.json.JsonObject)
        assertEquals(MediaIds(7, null, "99"), ids)
    }

    @Test
    fun `library keys come from the ids-only list`() {
        val keys = Parsers.libraryKeys(json("""{"shows":[{"show":{"ids":{"simkl":1}}}],"movies":[{"movie":{"ids":{"simkl":2}}}]}"""))
        assertEquals(setOf("Tv:1", "Movie:2"), keys)
    }

    @Test
    fun `playback knows anime from the library and keeps the episode`() {
        val root =
            json(
                """
                [
                  {"id": 1, "progress": 42.5, "paused_at": "2024-05-01T20:00:00Z",
                   "episode": {"season": 1, "number": 3, "title": "Ep"}, "show": {"title": "Show", "ids": {"simkl": 202, "imdb": "tt1"}}},
                  {"id": 2, "progress": 150, "movie": {"title": "Film", "ids": {"simkl": 303}}}
                ]
                """,
            )
        val list = Parsers.playback(root) { if (it == 202L) MediaType.Anime else null }
        assertEquals(MediaType.Anime, list[0].type)
        assertEquals(EpisodeRef(1, 3), list[0].episode)
        assertEquals(42.5, list[0].progress, 0.0)
        assertEquals(MediaType.Movie, list[1].type)
        assertEquals(100.0, list[1].progress, 0.0)
    }

    @Test
    fun `trending files read wrapped and bare lists`() {
        val bare = json("""[{"title":"T","poster":"p","fanart":"f","ids":{"simkl_id":5,"imdb":"tt5"},"release_date":"2021-02-03"}]""")
        val item = Parsers.trending(bare, MediaType.Movie).single()
        assertEquals(MediaIds(5, "tt5"), item.ids)
        assertEquals(2021, item.year)
        assertEquals("f", item.fanart)
        val wrapped = json("""{"items":[{"title":"U","ids":{"simkl":6}},{"ids":{"simkl":7}}]}""")
        assertEquals(listOf("U"), Parsers.trending(wrapped, MediaType.Tv).map { it.title })
    }

    @Test
    fun `pin code and status follow SIMKL's PIN sign-in`() {
        val code =
            Parsers.pinCode(
                json("""{"result":"OK","device_code":"D","user_code":"ABCDE","verification_url":"https://simkl.com/pin","expires_in":900,"interval":5}"""),
            )!!
        assertEquals("ABCDE", code.userCode)
        assertEquals("https://simkl.com/pin", code.verificationUrl)
        assertEquals(900, code.expiresInSeconds)
        assertEquals(5, code.intervalSeconds)
        assertNull(Parsers.pinCode(json("""{"result":"KO"}""")))
        assertEquals(PinStatus.Pending, Parsers.pinStatus(json("""{"result":"KO","message":"Authorization pending"}""")))
        assertEquals(PinStatus.Approved("tok"), Parsers.pinStatus(json("""{"result":"OK","access_token":"tok"}""")))
    }

    @Test
    fun `user name and avatar come from the settings`() {
        val user = Parsers.user(json("""{"user":{"name":"Wim","avatar":"https://x/a.jpg"},"account":{"id":1}}"""))!!
        assertEquals("Wim", user.name)
        assertTrue(user.avatar!!.endsWith("a.jpg"))
    }

    @Test
    fun `detail ids give the IMDb id the lists left out`() {
        assertEquals(MediaIds(9, "tt9", "90"), Parsers.detailIds(json("""{"title":"X","ids":{"simkl":9,"imdb":"tt9","tmdb":"90"}}""")))
    }
}
