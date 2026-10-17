package io.github.impca201.launcherforsimkl.data

import android.content.Context
import io.github.impca201.launcherforsimkl.data.api.ApiException
import io.github.impca201.launcherforsimkl.data.api.Parsers
import io.github.impca201.launcherforsimkl.data.api.SimklApi
import io.github.impca201.launcherforsimkl.data.model.Activities
import io.github.impca201.launcherforsimkl.data.model.LibraryMerge
import io.github.impca201.launcherforsimkl.data.model.LibrarySnapshot
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.SyncPlan
import io.github.impca201.launcherforsimkl.data.model.TrendingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Why the personal rows could not be refreshed. */
enum class SyncError { Offline, SignedOut, NoClientId }

data class ContentState(
    /** Null until the cache was read (or there is none and the first sync is still running). */
    val library: LibrarySnapshot? = null,
    val trending: Map<MediaType, List<TrendingItem>> = emptyMap(),
    /** True once the disk cache was read, so an empty library means "nothing there". */
    val loaded: Boolean = false,
    val syncing: Boolean = false,
    val error: SyncError? = null,
)

/**
 * The SIMKL content of the home screen: the user's library and paused playback (cached in
 * `library.json` so the rows fill at once), and SIMKL's public trending lists. Syncs follow
 * SIMKL's rules: `/sync/activities` first, then only what changed, and only while the launcher is
 * in front (no background calls).
 */
class ContentRepository(
    context: Context,
    private val api: SimklApi,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, LIBRARY_FILE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()

    private val _state = MutableStateFlow(ContentState())
    val state: StateFlow<ContentState> = _state.asStateFlow()

    private var lastSync = 0L
    private var lastTrending = 0L

    /** Ids looked up for titles the lists gave without an IMDb id (see [fullIds]). */
    private val idCache = ConcurrentHashMap<String, MediaIds>()

    fun start() {
        scope.launch {
            val cached =
                withContext(Dispatchers.IO) {
                    runCatching { json.decodeFromString<LibrarySnapshot>(file.readText()) }.getOrNull()
                }
            _state.update { it.copy(library = it.library ?: cached, loaded = true) }
        }
    }

    /** Called whenever the home screen comes to the front. */
    fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastTrending > TRENDING_MAX_AGE_MS) scope.launch { refreshTrending() }
        if (now - lastSync > SYNC_MAX_AGE_MS) scope.launch { sync() }
    }

    suspend fun refreshTrending() {
        lastTrending = System.currentTimeMillis()
        val lists =
            MediaType.entries.map { type ->
                scope.async { type to runCatching { Parsers.trending(api.trending(type), type) }.getOrNull() }
            }.awaitAll()
        _state.update { s -> s.copy(trending = s.trending + lists.mapNotNull { (t, l) -> l?.let { t to it } }) }
    }

    /** Syncs the library and paused playback with SIMKL, downloading only what changed. */
    suspend fun sync() {
        if (settings.token() == null) {
            _state.update { it.copy(error = null) }
            return
        }
        if (!api.hasClientId) {
            _state.update { it.copy(error = SyncError.NoClientId) }
            return
        }
        mutex.withLock {
            lastSync = System.currentTimeMillis()
            _state.update { it.copy(syncing = true) }
            try {
                val previous = _state.value.library ?: LibrarySnapshot()
                val activities = Activities.parse(api.activities()) ?: Activities()
                val items =
                    when (val plan = SyncPlan.decide(previous.activities, activities)) {
                        is SyncPlan.UpToDate -> previous.items
                        SyncPlan.Full -> Parsers.library(api.allItems())
                        is SyncPlan.Changes -> {
                            val changed = Parsers.library(api.allItems(plan.dateFrom))
                            val keep = if (plan.removals) Parsers.libraryKeys(api.allItemIds()) else null
                            LibraryMerge.merge(previous.items, changed, keep)
                        }
                    }
                val playback = Parsers.playback(api.playback(), LibraryMerge.typeLookup(items))
                val snapshot = LibrarySnapshot(items, playback, activities)
                _state.update { it.copy(library = snapshot, error = null) }
                save(snapshot)
                if (settings.current().userName == null) refreshUser()
            } catch (e: ApiException) {
                if (e.unauthorized) {
                    signOut()
                    _state.update { it.copy(error = SyncError.SignedOut) }
                } else {
                    _state.update { it.copy(error = SyncError.Offline) }
                }
            } catch (e: java.io.IOException) {
                _state.update { it.copy(error = SyncError.Offline) }
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    /** Forces the next [refreshIfStale] to sync, e.g. right after signing in. */
    fun syncNow() {
        scope.launch { sync() }
    }

    suspend fun refreshUser() {
        runCatching { Parsers.user(api.userSettings()) }.getOrNull()?.let { settings.setUser(it.name, it.avatar) }
    }

    /**
     * The title's ids with its IMDb id when SIMKL's lists left it out (some trending entries
     * carry only SIMKL's own id). Looked up once per title; the original ids when offline.
     */
    suspend fun fullIds(
        type: MediaType,
        ids: MediaIds,
    ): MediaIds {
        if (!api.hasClientId) return ids
        val key = "${type.name}:${ids.simkl}"
        idCache[key]?.let { return it }
        val found = runCatching { Parsers.detailIds(api.details(type, ids.simkl)) }.getOrNull() ?: return ids
        val merged = ids.copy(imdb = ids.imdb ?: found.imdb, tmdb = ids.tmdb ?: found.tmdb)
        idCache[key] = merged
        return merged
    }

    suspend fun signOut() {
        settings.signOut()
        withContext(Dispatchers.IO) { file.delete() }
        lastSync = 0
        _state.update { it.copy(library = null, error = null) }
    }

    private suspend fun save(snapshot: LibrarySnapshot) =
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.parentFile, "$LIBRARY_FILE.tmp")
                tmp.writeText(json.encodeToString(LibrarySnapshot.serializer(), snapshot))
                tmp.renameTo(file)
            }
        }

    companion object {
        const val LIBRARY_FILE = "library.json"
        private const val SYNC_MAX_AGE_MS = 15 * 60 * 1000L
        private const val TRENDING_MAX_AGE_MS = 60 * 60 * 1000L
    }
}
