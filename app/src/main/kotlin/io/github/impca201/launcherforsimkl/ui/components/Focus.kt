package io.github.impca201.launcherforsimkl.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Moves focus to this requester once it is laid out: lazy rows and grids place their first item
 * only in the next frame, and during a screen transition later still. Tries for a few frames and
 * gives up quietly when the item never shows.
 */
suspend fun FocusRequester.requestAfterLayout() {
    repeat(FRAMES) {
        withFrameNanos { }
        if (runCatching { requestFocus() }.getOrDefault(false)) return
    }
}

private const val FRAMES = 30
