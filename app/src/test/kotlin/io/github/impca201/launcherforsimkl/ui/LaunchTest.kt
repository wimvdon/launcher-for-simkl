package io.github.impca201.launcherforsimkl.ui

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.impca201.launcherforsimkl.LauncherApp
import io.github.impca201.launcherforsimkl.data.model.EpisodeRef
import io.github.impca201.launcherforsimkl.data.model.MediaIds
import io.github.impca201.launcherforsimkl.data.model.MediaType
import io.github.impca201.launcherforsimkl.data.model.Tile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Opens the launcher like a user does; any exception during start-up or composition fails the test. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LaunchTest {
    private fun launchAndSettle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // DataStore loads on a background thread; let it emit and the UI recompose.
            repeat(20) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(100)
            }
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }

    @Test
    fun freshInstallShowsHome() = launchAndSettle()

    @Test
    fun signedInShowsHome() {
        val app = ApplicationProvider.getApplicationContext<LauncherApp>()
        runBlocking { app.settings.signIn("test-token") }
        launchAndSettle()
    }

    @Test
    fun channelIntentKeepsTheTitle() {
        val tile =
            Tile(
                key = "k",
                type = MediaType.Tv,
                ids = MediaIds(42, imdb = "tt0944947", tmdb = "1399"),
                title = "Game of Thrones",
                year = 2011,
                poster = null,
                fanart = null,
                overview = null,
                episode = EpisodeRef(2, 5),
            )
        val back = OpenActivity.tileFrom(OpenActivity.intent(ApplicationProvider.getApplicationContext(), tile))!!
        assertEquals(tile.ids, back.ids)
        assertEquals(tile.type, back.type)
        assertEquals(tile.title, back.title)
        assertEquals(tile.episode, back.episode)
    }

    @Test
    fun foreignIntentsOpenNothing() {
        assertNull(OpenActivity.tileFrom(Intent()))
        assertNull(OpenActivity.tileFrom(Intent().putExtra("type", "Tv").putExtra("title", "x")))
    }
}
