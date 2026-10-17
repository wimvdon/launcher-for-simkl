package io.github.impca201.launcherforsimkl

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.impca201.launcherforsimkl.data.AppsRepository
import io.github.impca201.launcherforsimkl.data.ContentRepository
import io.github.impca201.launcherforsimkl.data.PlayerLauncher
import io.github.impca201.launcherforsimkl.data.SettingsStore
import io.github.impca201.launcherforsimkl.data.api.SimklApi
import io.github.impca201.launcherforsimkl.data.model.Rows
import io.github.impca201.launcherforsimkl.tv.HomeChannel
import io.github.impca201.launcherforsimkl.tv.SystemChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class LauncherApp :
    Application(),
    SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    val settings by lazy { SettingsStore(this) }
    val api by lazy { SimklApi(http) { settings.token() } }
    val content by lazy { ContentRepository(this, api, settings, scope) }
    val apps by lazy { AppsRepository(this, scope) }
    val players by lazy { PlayerLauncher(this, apps, content) }
    val systemChannels by lazy { SystemChannels(this) }
    private val homeChannel by lazy { HomeChannel(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Before the libraries' start-up providers run, so their crashes are reported too.
        CrashReporter.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        content.start()
        apps.start()
        publishHomeChannel()
    }

    /**
     * Keeps the "Up next" channel on the system home screen in step with the launcher's rows.
     * Local only: it reuses what the last sync downloaded.
     */
    @OptIn(FlowPreview::class)
    private fun publishHomeChannel() {
        combine(content.state, settings.settings) { state, s ->
            val tiles = if (state.library != null) Rows.continueWatching(state.library) + Rows.upNext(state.library, java.time.Instant.now()) else emptyList()
            Triple(tiles.distinctBy { it.ids.simkl }, s.homeChannel && s.signedIn, state.loaded)
        }.distinctUntilChanged()
            .debounce(2_000)
            .onEach { (tiles, enabled, loaded) -> if (loaded) scope.launch(Dispatchers.IO) { homeChannel.publish(tiles, enabled) } }
            .launchIn(scope)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .crossfade(true)
            .build()
}
