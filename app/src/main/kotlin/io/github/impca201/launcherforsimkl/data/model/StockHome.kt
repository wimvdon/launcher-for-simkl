package io.github.impca201.launcherforsimkl.data.model

/** A home screen activity, as Android lists it for the Home intent. */
data class HomeActivity(
    val packageName: String,
    val className: String,
    /** The real activity when [className] is an alias (what window events report). */
    val targetClassName: String? = null,
)

/**
 * The built-in home screens that the Home button override replaces. Google TV, Chromecast and
 * Fire TV don't let users choose another home app, so the override (an accessibility service)
 * opens this launcher whenever the built-in home screen comes to the front.
 */
object StockHome {
    val known =
        setOf(
            // Google TV (Chromecast with Google TV, Google TV Streamer, TVs with Google TV).
            "com.google.android.apps.tv.launcherx",
            // Android TV home (Nvidia Shield, older TVs and boxes).
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            // Fire TV.
            "com.amazon.tv.launcher",
        )

    /** Packages that are never a home screen even when Android reports them as the default. */
    private val notHome = setOf("android", "com.android.settings", "com.android.tv.settings")

    /**
     * The home screen activities to replace ("package/class"): those of the known home apps and of
     * the device's current default home, never this launcher itself (release or debug build).
     * Only the home activity counts, so other screens of the same app (Google TV's settings panel,
     * its app list) keep working.
     */
    fun targets(
        homeActivities: List<HomeActivity>,
        defaultHome: String?,
        ownPackages: Set<String>,
    ): Set<String> {
        val packages = (known + listOfNotNull(defaultHome?.takeIf { it !in notHome })) - ownPackages
        return homeActivities
            .filter { it.packageName in packages }
            .flatMap { listOfNotNull(it.className, it.targetClassName).map { cls -> "${it.packageName}/$cls" } }
            .toSet()
    }

    /**
     * Whether a window of [packageName]/[className] coming to the front means the stock home
     * screen opened, so the launcher should take over. [sinceOwnLaunchMs] is how long ago the
     * override last opened the launcher: a home screen still finishing its animation then is
     * ignored, so the two don't bounce.
     */
    fun shouldTakeOver(
        packageName: String?,
        className: String?,
        targets: Set<String>,
        sinceOwnLaunchMs: Long,
    ): Boolean = packageName != null && className != null && "$packageName/$className" in targets && sinceOwnLaunchMs > DEBOUNCE_MS

    const val DEBOUNCE_MS = 1_500L
}
