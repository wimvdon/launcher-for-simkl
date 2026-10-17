package io.github.impca201.launcherforsimkl.data.model

import io.github.impca201.launcherforsimkl.data.api.obj
import io.github.impca201.launcherforsimkl.data.api.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The timestamps of POST /sync/activities that decide what a sync downloads. */
@Serializable
data class Activities(
    val all: String? = null,
    /** Latest `removed_from_list` of any type. */
    val removed: String? = null,
    /** Latest `playing` (paused playback) of any type. */
    val playing: String? = null,
) {
    companion object {
        private val typeKeys = listOf("tv_shows", "anime", "movies")

        fun parse(obj: JsonObject?): Activities? {
            obj ?: return null
            fun latest(key: String) = typeKeys.mapNotNull { obj.obj(it)?.str(key) }.maxOrNull()
            return Activities(all = obj.str("all"), removed = latest("removed_from_list"), playing = latest("playing"))
        }
    }
}

/** What the next sync must download, from the previous and the current activities. */
sealed interface SyncPlan {
    /** Nothing in the lists changed. [playback] is true when paused playback did. */
    data class UpToDate(
        val playback: Boolean,
    ) : SyncPlan

    /** The whole library (first sync, or the previous state is unknown). */
    data object Full : SyncPlan

    /** Only what changed since [dateFrom]; [removals] when something was removed from a list. */
    data class Changes(
        val dateFrom: String,
        val removals: Boolean,
    ) : SyncPlan

    companion object {
        fun decide(
            previous: Activities?,
            current: Activities,
        ): SyncPlan {
            val from = previous?.all ?: return Full
            if (current.all == from) return UpToDate(playback = current.playing != previous.playing)
            return Changes(dateFrom = from, removals = current.removed != previous.removed)
        }
    }
}

/** The library as saved on disk between starts, so the home screen fills at once. */
@Serializable
data class LibrarySnapshot(
    val items: List<LibraryItem> = emptyList(),
    val playback: List<Playback> = emptyList(),
    val activities: Activities? = null,
)

object LibraryMerge {
    /**
     * The library after an incremental sync: [changed] items replace those with the same key, and
     * when [keep] is known (after a removal) only the keys in it remain.
     */
    fun merge(
        current: List<LibraryItem>,
        changed: List<LibraryItem>,
        keep: Set<String>?,
    ): List<LibraryItem> {
        val byKey = LinkedHashMap<String, LibraryItem>()
        current.forEach { byKey[it.key] = it }
        changed.forEach { byKey[it.key] = it }
        return byKey.values.filter { keep == null || it.key in keep }
    }

    /** Which type a playback show id is, from the library (TV and anime both come as "show"). */
    fun typeLookup(items: List<LibraryItem>): (Long) -> MediaType? {
        val types = items.associate { it.ids.simkl to it.type }
        return { types[it] }
    }
}
