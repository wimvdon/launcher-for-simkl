package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.data.model.AppSorting
import io.github.impca201.launcherforsimkl.data.model.HomeActivity
import io.github.impca201.launcherforsimkl.data.model.PinnedApps
import io.github.impca201.launcherforsimkl.data.model.StockHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppsTest {
    private fun app(
        pkg: String,
        label: String = pkg,
    ) = AppEntry(pkg, label, hasBanner = false)

    @Test
    fun `pinning adds at the end once, unpinning removes`() {
        assertEquals(listOf("a", "b"), PinnedApps.pin(listOf("a"), "b"))
        assertEquals(listOf("a", "b"), PinnedApps.pin(listOf("a", "b"), "a"))
        assertEquals(listOf("b"), PinnedApps.unpin(listOf("a", "b"), "a"))
    }

    @Test
    fun `pinned apps move one place and stop at the ends`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), PinnedApps.move(list, "b", left = true))
        assertEquals(listOf("a", "c", "b"), PinnedApps.move(list, "b", left = false))
        assertEquals(list, PinnedApps.move(list, "a", left = true))
        assertEquals(list, PinnedApps.move(list, "c", left = false))
        assertEquals(list, PinnedApps.move(list, "x", left = true))
    }

    @Test
    fun `uninstalled pinned apps are left out, in the pinned order`() {
        val installed = listOf(app("a"), app("c"))
        assertEquals(listOf("c", "a"), PinnedApps.resolve(listOf("c", "b", "a"), installed).map { it.packageName })
    }

    @Test
    fun `the first start pins the streaming apps that are installed`() {
        val installed = listOf(app("com.nuvio.tv"), app("com.example"), app("com.google.android.youtube.tv"))
        assertEquals(listOf("com.nuvio.tv", "com.google.android.youtube.tv"), PinnedApps.defaults(installed))
    }

    @Test
    fun `the pinned list survives saving`() {
        val list = listOf("a.b", "c.d")
        assertEquals(list, PinnedApps.parse(PinnedApps.format(list)))
        assertEquals(emptyList<String>(), PinnedApps.parse(null))
        assertEquals(listOf("a"), PinnedApps.parse(" a, ,a"))
    }

    @Test
    fun `all apps are sorted by name without the launcher itself`() {
        val sorted = AppSorting.sorted(listOf(app("z", "zebra"), app("me", "Me"), app("a", "Älpha"), app("z", "dup")), "me", Locale.ENGLISH)
        assertEquals(listOf("Älpha", "zebra"), sorted.map { it.label })
    }

    @Test
    fun `the override replaces only the stock home activity, never the launcher`() {
        val activities =
            listOf(
                HomeActivity("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity"),
                HomeActivity("com.vendor.home", "com.vendor.home.Main", targetClassName = "com.vendor.home.RealMain"),
                HomeActivity("io.github.impca201.launcherforsimkl", "io.github.impca201.launcherforsimkl.ui.MainActivity"),
                HomeActivity("com.other.home", "com.other.home.Main"),
            )
        val targets = StockHome.targets(activities, defaultHome = "com.vendor.home", ownPackages = setOf("io.github.impca201.launcherforsimkl"))
        assertEquals(
            setOf(
                "com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity",
                "com.vendor.home/com.vendor.home.Main",
                "com.vendor.home/com.vendor.home.RealMain",
            ),
            targets,
        )
        val home = "com.google.android.apps.tv.launcherx.home.HomeActivity"
        assertTrue(StockHome.shouldTakeOver("com.google.android.apps.tv.launcherx", home, targets, 10_000))
        // Bounce protection, other screens of the same app, other apps.
        assertFalse(StockHome.shouldTakeOver("com.google.android.apps.tv.launcherx", home, targets, 100))
        assertFalse(StockHome.shouldTakeOver("com.google.android.apps.tv.launcherx", "android.widget.FrameLayout", targets, 10_000))
        assertFalse(StockHome.shouldTakeOver("com.other.home", "com.other.home.Main", targets, 10_000))
        assertFalse(StockHome.shouldTakeOver(null, null, targets, 10_000))
    }

    @Test
    fun `the system resolver is never taken for a home screen`() {
        val activities = listOf(HomeActivity("android", "com.android.internal.app.ResolverActivity"))
        assertTrue(StockHome.targets(activities, defaultHome = "android", ownPackages = emptySet()).isEmpty())
    }
}
