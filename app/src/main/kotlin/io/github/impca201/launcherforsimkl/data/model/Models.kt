package io.github.impca201.launcherforsimkl.data.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

/** The three SIMKL catalogues, with the names the API uses for each of them. */
@Serializable
enum class MediaType(
    /** Path segment of the catalogue endpoints: /tv/{id}, /anime/{id}, /movies/{id}. */
    val catalogPath: String,
    /** Folder of the trending files on data.simkl.in. */
    val trendingPath: String,
) {
    Tv("tv", "tv"),
    Anime("anime", "anime"),
    Movie("movies", "movies"),
    ;

    val isEpisodic get() = this != Movie
}

/** SIMKL list statuses. Movies only use plan to watch, completed and dropped. */
@Serializable
enum class ListStatus(
    val api: String,
) {
    Watching("watching"),
    PlanToWatch("plantowatch"),
    OnHold("hold"),
    Completed("completed"),
    Dropped("dropped"),
    ;

    companion object {
        fun fromApi(value: String?): ListStatus? = entries.firstOrNull { it.api == value?.lowercase() }
    }
}

/** The ids SIMKL knows for a title. Stremio and Nuvio open titles by their IMDb (or TMDB) id. */
@Serializable
data class MediaIds(
    val simkl: Long,
    val imdb: String? = null,
    val tmdb: String? = null,
)

@Serializable
data class EpisodeRef(
    val season: Int?,
    val episode: Int,
) {
    val label: String
        get() =
            if (season != null) {
                "S%02dE%02d".format(Locale.ROOT, season, episode)
            } else {
                "E%02d".format(Locale.ROOT, episode)
            }

    companion object {
        private val pattern = Regex("""(?:S(\d+))?\s*E(\d+)""", RegexOption.IGNORE_CASE)

        /** Parses SIMKL's "S01E05" / "E05" episode labels. */
        fun parse(value: String?): EpisodeRef? {
            val m = pattern.find(value ?: return null) ?: return null
            return EpisodeRef(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull() ?: return null)
        }
    }
}

/** One title in the user's SIMKL lists (from /sync/all-items). Only what the launcher shows. */
@Serializable
data class LibraryItem(
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val fanart: String? = null,
    val overview: String? = null,
    val status: ListStatus,
    val addedAt: String? = null,
    val lastWatchedAt: String? = null,
    val nextToWatch: EpisodeRef? = null,
    val nextTitle: String? = null,
    /** ISO date (or date-time) when [nextToWatch] airs; null when unknown. */
    val nextDate: String? = null,
) {
    val key: String get() = "${type.name}:${ids.simkl}"
}

/**
 * A paused, partly watched episode or movie (GET /sync/playback).
 */
@Serializable
data class Playback(
    val id: Long,
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    /** 0 to 100. */
    val progress: Double,
    val pausedAt: String? = null,
    val episode: EpisodeRef? = null,
    val episodeTitle: String? = null,
)

/** A title from SIMKL's public trending files. */
@Serializable
data class TrendingItem(
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    val year: Int? = null,
    val poster: String? = null,
    val fanart: String? = null,
    val overview: String? = null,
)

/** What a tile on the home screen shows and opens. */
data class Tile(
    /** Unique within its row. */
    val key: String,
    val type: MediaType,
    val ids: MediaIds,
    val title: String,
    val year: Int?,
    /** SIMKL image path or absolute URL. */
    val poster: String?,
    val fanart: String?,
    val overview: String?,
    /** The episode to open (Up next, Continue watching); null opens the title itself. */
    val episode: EpisodeRef? = null,
    val episodeTitle: String? = null,
    /** 0 to 100 for paused playback, otherwise null. */
    val progress: Double? = null,
)

/**
 * Image URLs as documented by SIMKL: {domain}{prefix}{image}{suffix}{extension}, loaded through the
 * wsrv.nl proxy SIMKL asks apps to use. Coil caches them on disk (the URLs never change).
 */
object Images {
    private const val CDN = "https://wsrv.nl/?url=https://simkl.in"

    private fun absolute(path: String): String? =
        when {
            path.isBlank() -> null
            path.startsWith("//") -> "https:$path"
            path.startsWith("http") -> path
            else -> null
        }

    /** `_ca` (190x279) for rows, `_m` (340 wide) when larger. */
    fun poster(
        path: String?,
        large: Boolean = false,
    ): String? {
        path ?: return null
        absolute(path)?.let { return it }
        return "$CDN/posters/${path}_${if (large) "m" else "ca"}.webp"
    }

    /** `_mobile` (960x540) for the backdrop behind the home screen. */
    fun fanart(path: String?): String? {
        path ?: return null
        absolute(path)?.let { return it }
        return "$CDN/fanart/${path}_mobile.webp"
    }

    fun avatar(path: String?): String? = path?.let { absolute(it) ?: "$CDN/avatars/${it}_100.jpg" }
}

object Dates {
    /** Parses SIMKL dates ("2024-05-01T20:00:00Z", "2024-05-01T20:00:00-04:00" or "2024-05-01"). */
    fun parse(value: String?): Instant? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            OffsetDateTime.parse(v).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(v)
            } catch (_: DateTimeParseException) {
                try {
                    // A date-time without an offset ("2024-05-01T20:00:00"): SIMKL times are UTC.
                    LocalDateTime.parse(v.replace(' ', 'T')).toInstant(ZoneOffset.UTC)
                } catch (_: DateTimeParseException) {
                    try {
                        LocalDate.parse(v.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    } catch (_: DateTimeParseException) {
                        null
                    }
                }
            }
        }
    }
}
