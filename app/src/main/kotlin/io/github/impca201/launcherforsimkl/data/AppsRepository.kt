package io.github.impca201.launcherforsimkl.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.data.model.AppSorting
import io.github.impca201.launcherforsimkl.data.model.HomeActivity
import io.github.impca201.launcherforsimkl.data.model.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The installed apps that can be opened (TV apps and phone apps), refreshed when an app is
 * installed, updated or removed. Uses the `<queries>` in the manifest, not "all packages".
 */
class AppsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val pm: PackageManager = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first load finished. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val drawables = ConcurrentHashMap<String, Drawable>()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                drawables.keys.removeAll { it.startsWith("${intent.data?.schemeSpecificPart}:") }
                refresh()
            }
        }

    fun start() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun refresh() {
        scope.launch { _apps.value = withContext(Dispatchers.IO) { load() } }
    }

    private fun load(): List<AppEntry> {
        fun query(category: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)
        val entries =
            (tv + phone).map { info ->
                val app = info.activityInfo.applicationInfo
                AppEntry(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString().ifBlank { info.activityInfo.packageName },
                    hasBanner = info.activityInfo.banner != 0 || app.banner != 0,
                )
            }
        return AppSorting.sorted(entries, context.packageName)
    }

    /** The app's TV banner, or its icon when it has none. Cached; loaded off the main thread. */
    suspend fun picture(
        packageName: String,
        banner: Boolean,
    ): Drawable? {
        val key = "$packageName:$banner"
        drawables[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val drawable =
                    if (banner) {
                        launchIntent(packageName)?.let { pm.getActivityBanner(it) } ?: pm.getApplicationBanner(packageName)
                    } else {
                        pm.getApplicationIcon(packageName)
                    }
                drawable?.also { drawables[key] = it }
            }.getOrNull()
        }
    }

    fun launchIntent(packageName: String): Intent? = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)

    fun open(packageName: String): Boolean {
        val intent = launchIntent(packageName) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun openInfo(packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun installedPlayers(): Set<Player> = Player.entries.filter { p -> p.packages.any(::isInstalled) }.toSet()

    fun installedPackage(player: Player): String? = player.packages.firstOrNull(::isInstalled)

    private fun isInstalled(packageName: String): Boolean =
        runCatching { pm.getApplicationInfo(packageName, 0).enabled }.getOrDefault(false)

    /** Every activity that answers the Home button, and the current default. */
    fun homeActivities(): Pair<List<HomeActivity>, String?> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val all =
            pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS).map {
                HomeActivity(it.activityInfo.packageName, it.activityInfo.name, it.activityInfo.targetActivity)
            }
        val default = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return all to default
    }
}
