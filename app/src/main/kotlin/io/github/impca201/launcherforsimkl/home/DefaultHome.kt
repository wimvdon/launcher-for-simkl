package io.github.impca201.launcherforsimkl.home

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Making the launcher the home app: Android's own choice where the device offers one. */
object DefaultHome {
    /** Whether the Home button already opens this launcher by Android's own setting. */
    fun isDefault(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    }

    /**
     * Opens Android's home app choice: the "default home app" request (Android 10+), else the
     * Home settings page. False when the device has neither (Google TV, Fire TV): the Home button
     * override is the way there.
     */
    fun request(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
                if (start(activity, intent)) return true
            }
        }
        return start(activity, Intent(Settings.ACTION_HOME_SETTINGS))
    }

    /** Opens Android's accessibility settings, where the Home button override is turned on. */
    fun openAccessibilitySettings(activity: Activity): Boolean = start(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Opens Android's own settings (the built-in home screen's settings panel is gone when it's replaced). */
    fun openDeviceSettings(activity: Activity): Boolean = start(activity, Intent(Settings.ACTION_SETTINGS))

    private fun start(
        activity: Activity,
        intent: Intent,
    ): Boolean {
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
