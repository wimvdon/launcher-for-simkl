package io.github.impca201.launcherforsimkl.data.api

import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.LibraryItem
import io.github.impca201.launcherforsimkl.data.model.ListStatus
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Playback
import io.github.impca201.launcherforsimkl.data.model.TrendingItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A started PIN sign-in (GET /oauth/pin): the user enters [userCode] at [verificationUrl]. */
data class PinCode(
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

/** One check of a PIN sign-in (GET /oauth/pin/{code}). */
sealed interface PinStatus {
    data object Pending : PinStatus

    data class Approved(
        val accessToken: String,
    ) : PinStatus
}

data class SimklUser(
    val name: String,
    val avatar: String?,
)

/** Maps SIMKL JSON to the app's models. Pure functions, so they are unit tested. */
object Parsers {
    fun ids(obj: JsonObject?): MediaIds? {
        obj ?: return null
        val simkl = obj.long("simkl") ?: obj.long("simkl_id") ?: obj.long("id") ?: return null
        return MediaIds(
            simkl = simkl,
            imdb = obj.str("imdb")?.takeIf { it.startsWith("tt") },
            tmdb = obj.str("tmdb"),
        )
    }

    private fun yearOf(obj: JsonObject): Int? =
        obj.int("year")
            ?: listOf("release_date", "released", "first_aired", "date").firstNotNullOfOrNull { k ->
                obj.str(k)?.take(4)?.toIntOrNull()
            }

    /** SIMKL overviews may contain HTML line breaks. */
    private fun plainText(value: String?): String? =
        value
            ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            ?.replace(Regex("<[^>]+>"), "")
            ?.trim()
            ?.ifEmpty { null }

    // ---- /sync/all-items ----------------------------------------------------------------------

    fun library(root: JsonElement?): List<LibraryItem> {
        val obj = root.asObject() ?: return emptyList()
        return buildList {
            obj["shows"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Tv) }
            obj["anime"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Anime) }
            obj["movies"].objects().mapNotNullTo(this) { libraryItem(it, MediaType.Movie) }
        }.distinctBy { it.key }
    }

    fun libraryItem(
        entry: JsonObject,
        type: MediaType,
    ): LibraryItem? {
        val media = entry.obj("show") ?: entry.obj("movie") ?: entry.obj("anime") ?: return null
        val ids = ids(media.obj("ids")) ?: return null
        val status = ListStatus.fromApi(entry.str("status")) ?: return null
        val nextInfo = entry.obj("next_to_watch_info")
        val nextRef =
            nextInfo?.let { info -> info.int("episode")?.let { EpisodeRef(info.int("season"), it) } }
                ?: EpisodeRef.parse(entry.str("next_to_watch"))
        return LibraryItem(
            type = type,
            ids = ids,
            title = media.str("title") ?: "#${ids.simkl}",
            year = yearOf(media),
            poster = media.str("poster"),
            fanart = media.str("fanart"),
            overview = plainText(media.str("overview")),
            status = status,
            addedAt = entry.str("added_to_watchlist_at"),
            lastWatchedAt = entry.str("last_watched_at"),
            nextToWatch = nextRef,
            nextTitle = nextInfo?.str("title"),
            nextDate = nextInfo?.str("date"),
        )
    }

    /** Keys ("Type:simklId") in a /sync/all-items?extended=simkl_ids_only response. */
    fun libraryKeys(root: JsonElement?): Set<String> {
        val obj = root.asObject() ?: return emptySet()
        return buildSet {
            listOf("shows" to MediaType.Tv, "anime" to MediaType.Anime, "movies" to MediaType.Movie).forEach { (key, type) ->
                obj[key].objects().forEach { entry ->
                    val media = entry.obj("show") ?: entry.obj("movie") ?: entry.obj("anime") ?: entry
                    ids(media.obj("ids"))?.let { add("${type.name}:${it.simkl}") }
                }
            }
        }
    }

    /**
     * GET /sync/playback. Shows come as "show" for TV and anime alike; [typeOf] tells which one a
     * show id is (from the library), defaulting to TV.
     */
    fun playback(
        root: JsonElement?,
        typeOf: (Long) -> MediaType?,
    ): List<Playback> =
        root.objects().mapNotNull { e ->
            val id = e.long("id") ?: return@mapNotNull null
            val movie = e.obj("movie")
            val anime = e.obj("anime")
            val media = movie ?: anime ?: e.obj("show") ?: return@mapNotNull null
            val ids = ids(media.obj("ids")) ?: return@mapNotNull null
            val type =
                when {
                    movie != null -> MediaType.Movie
                    anime != null -> MediaType.Anime
                    else -> typeOf(ids.simkl) ?: MediaType.Tv
                }
            val ep = e.obj("episode")
            Playback(
                id = id,
                type = type,
                ids = ids,
                title = media.str("title") ?: "#${ids.simkl}",
                progress = (e.double("progress") ?: 0.0).coerceIn(0.0, 100.0),
                pausedAt = e.str("paused_at"),
                episode = ep?.let { (it.int("episode") ?: it.int("number"))?.let { n -> EpisodeRef(it.int("season"), n) } },
                episodeTitle = ep?.str("title"),
            )
        }

    // ---- data.simkl.in ------------------------------------------------------------------------

    /** A trending file (data.simkl.in/discover/trending/...): a bare array, or wrapped in an object. */
    fun trending(
        root: JsonElement?,
        type: MediaType,
    ): List<TrendingItem> {
        val items = root.asArray() ?: root.asObject()?.let { o -> o.arr("items") ?: o.arr("results") }
        return items.objects().mapNotNull { obj ->
            val ids = ids(obj.obj("ids")) ?: ids(obj) ?: return@mapNotNull null
            TrendingItem(
                type = type,
                ids = ids,
                title = obj.str("title") ?: obj.str("en_title") ?: return@mapNotNull null,
                year = yearOf(obj),
                poster = obj.str("poster") ?: obj.str("img"),
                fanart = obj.str("fanart"),
                overview = plainText(obj.str("overview")),
            )
        }.distinctBy { it.ids.simkl }
    }

    // ---- Catalogue ----------------------------------------------------------------------------

    /** The ids from GET /{type}/{id}, for opening a title whose IMDb id the lists left out. */
    fun detailIds(root: JsonElement?): MediaIds? = ids(root.asObject()?.obj("ids"))

    // ---- Sign-in and user ---------------------------------------------------------------------

    fun pinCode(root: JsonElement?): PinCode? {
        val obj = root.asObject() ?: return null
        if (obj.str("result")?.uppercase() != "OK") return null
        return PinCode(
            userCode = obj.str("user_code") ?: return null,
            verificationUrl = obj.str("verification_url") ?: obj.str("verification_uri") ?: "https://simkl.com/pin",
            expiresInSeconds = obj.long("expires_in")?.takeIf { it > 0 } ?: 900,
            intervalSeconds = (obj.long("interval") ?: 5).coerceIn(1, 60),
        )
    }

    /** Approved with a token; Pending while the user hasn't entered the code yet. */
    fun pinStatus(root: JsonElement?): PinStatus {
        val obj = root.asObject() ?: return PinStatus.Pending
        val token = obj.str("access_token")
        return if (obj.str("result")?.uppercase() == "OK" && token != null) PinStatus.Approved(token) else PinStatus.Pending
    }

    fun user(root: JsonElement?): SimklUser? {
        val user = root.asObject()?.obj("user") ?: return null
        return SimklUser(name = user.str("name") ?: return null, avatar = user.str("avatar"))
    }
}
