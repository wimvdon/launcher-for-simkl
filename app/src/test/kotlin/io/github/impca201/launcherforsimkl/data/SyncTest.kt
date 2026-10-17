package io.github.impca201.launcherforsimkl.data

import io.github.impca201.launcherforsimkl.data.model.Activities
import io.github.impca201.launcherforsimkl.data.model.LibraryItem
import io.github.impca201.launcherforsimkl.data.model.LibraryMerge
import io.github.impca201.launcherforsimkl.data.model.ListStatus
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.SyncPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTest {
    private fun item(
        id: Long,
        status: ListStatus = ListStatus.Watching,
    ) = LibraryItem(MediaType.Tv, MediaIds(id), "T$id", status = status)

    @Test
    fun `activities take the latest removal and playback of any type`() {
        val obj =
            Json.parseToJsonElement(
                """
                {"all":"2024-05-03","tv_shows":{"removed_from_list":"2024-05-01","playing":"2024-04-01"},
                 "movies":{"removed_from_list":"2024-05-02","playing":"2024-05-03"},"anime":{}}
                """,
            ) as JsonObject
        assertEquals(Activities("2024-05-03", "2024-05-02", "2024-05-03"), Activities.parse(obj))
    }

    @Test
    fun `first sync downloads everything`() {
        assertEquals(SyncPlan.Full, SyncPlan.decide(null, Activities("a")))
        assertEquals(SyncPlan.Full, SyncPlan.decide(Activities(null), Activities("a")))
    }

    @Test
    fun `unchanged lists only refresh playback when it changed`() {
        assertEquals(SyncPlan.UpToDate(playback = false), SyncPlan.decide(Activities("a", playing = "p"), Activities("a", playing = "p")))
        assertEquals(SyncPlan.UpToDate(playback = true), SyncPlan.decide(Activities("a", playing = "p"), Activities("a", playing = "q")))
    }

    @Test
    fun `changed lists download changes since the last sync`() {
        assertEquals(SyncPlan.Changes("a", removals = false), SyncPlan.decide(Activities("a", "r"), Activities("b", "r")))
        assertEquals(SyncPlan.Changes("a", removals = true), SyncPlan.decide(Activities("a", "r"), Activities("b", "s")))
    }

    @Test
    fun `merge replaces changed items and drops removed ones`() {
        val current = listOf(item(1), item(2), item(3))
        val changed = listOf(item(2, ListStatus.Completed), item(4))
        val merged = LibraryMerge.merge(current, changed, keep = null)
        assertEquals(listOf(1L, 2L, 3L, 4L), merged.map { it.ids.simkl })
        assertEquals(ListStatus.Completed, merged[1].status)
        val afterRemoval = LibraryMerge.merge(current, changed, keep = setOf("Tv:1", "Tv:2", "Tv:4"))
        assertEquals(listOf(1L, 2L, 4L), afterRemoval.map { it.ids.simkl })
    }
}
