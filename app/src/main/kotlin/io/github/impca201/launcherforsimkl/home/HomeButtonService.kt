package io.github.impca201.launcherforsimkl.home

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.impca201.launcherforsimkl.LauncherApp
import io.github.impca201.launcherforsimkl.data.model.StockHome
import io.github.impca201.launcherforsimkl.ui.MainActivity

/**
 * The Home button override for devices that don't let users choose a home app (Google TV,
 * Chromecast, Fire TV). An accessibility service, because that is the only way Android lets an app
 * notice the Home button: it opens this launcher when Home is pressed, and when the built-in home
 * screen comes to the front anyway (after starting up, or on remotes whose Home button the system
 * handles itself). It reads no window content and sends nothing anywhere.
 */
class HomeButtonService : AccessibilityService() {
    private var targets: Set<String> = emptySet()
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        reloadTargets()
    }

    private fun reloadTargets() {
        val (activities, default) = (application as LauncherApp).apps.homeActivities()
        targets = StockHome.targets(activities, default, setOf(packageName))
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Swallow the whole press and act when the button is released: a short press opens the
        // launcher, a long one the device settings (the built-in home screen's long press, which
        // the override takes away, opens its settings panel).
        if (event.action == KeyEvent.ACTION_UP) {
            if (event.eventTime - event.downTime < LONG_PRESS_MS) openLauncher() else openDeviceSettings()
        }
        return true
    }

    private fun openDeviceSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        // A home app installed or updated since: look again once.
        if (pkg != null && pkg in StockHome.known && targets.none { it.startsWith("$pkg/") }) reloadTargets()
        if (StockHome.shouldTakeOver(pkg, cls, targets, SystemClock.elapsedRealtime() - lastLaunch)) openLauncher()
    }

    override fun onInterrupt() = Unit

    private fun openLauncher() {
        lastLaunch = SystemClock.elapsedRealtime()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    companion object {
        private const val LONG_PRESS_MS = 600L

        /** Whether the user turned the override on in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, HomeButtonService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** "package/class" as `adb shell settings put secure enabled_accessibility_services` needs it. */
        fun flattened(context: Context): String = ComponentName(context, HomeButtonService::class.java).flattenToShortString()
    }
}
