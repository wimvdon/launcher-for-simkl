package io.github.impca201.launcherforsimkl.data.model

import java.text.Collator
import java.util.Locale

/** An installed app that can be opened from the launcher. */
data class AppEntry(
    val packageName: String,
    val label: String,
    /** True when the app has a TV banner (a 16:9 picture); otherwise its icon is shown. */
    val hasBanner: Boolean,
)

/** The pinned apps row: package names in the user's order. Pure logic, the store saves the list. */
object PinnedApps {
    fun pin(
        pinned: List<String>,
        packageName: String,
    ): List<String> = if (packageName in pinned) pinned else pinned + packageName

    fun unpin(
        pinned: List<String>,
        packageName: String,
    ): List<String> = pinned - packageName

    /** Moves [packageName] one place to the left ([left]) or right. */
    fun move(
        pinned: List<String>,
        packageName: String,
        left: Boolean,
    ): List<String> {
        val index = pinned.indexOf(packageName)
        val other = if (left) index - 1 else index + 1
        if (index < 0 || other !in pinned.indices) return pinned
        return pinned.toMutableList().apply {
            this[index] = this[other]
            this[other] = packageName
        }
    }

    /** The pinned apps that are still installed, in the pinned order. */
    fun resolve(
        pinned: List<String>,
        installed: List<AppEntry>,
    ): List<AppEntry> {
        val byPackage = installed.associateBy { it.packageName }
        return pinned.mapNotNull { byPackage[it] }
    }

    /**
     * The first time the launcher opens, the streaming apps it deep-links into (and the store)
     * are pinned, so the row isn't empty.
     */
    fun defaults(installed: List<AppEntry>): List<String> {
        val wanted = Player.entries.flatMap { it.packages } + listOf("com.google.android.youtube.tv", "com.android.vending")
        val have = installed.map { it.packageName }.toSet()
        return wanted.filter { it in have }
    }

    /** Saved as one line, packages separated by commas (package names never contain one). */
    fun parse(value: String?): List<String> = value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()

    fun format(pinned: List<String>): String = pinned.joinToString(",")
}

object AppSorting {
    /** All apps A to Z in the user's language, leaving out this launcher itself. */
    fun sorted(
        apps: List<AppEntry>,
        ownPackage: String,
        locale: Locale = Locale.getDefault(),
    ): List<AppEntry> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return apps
            .filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}
