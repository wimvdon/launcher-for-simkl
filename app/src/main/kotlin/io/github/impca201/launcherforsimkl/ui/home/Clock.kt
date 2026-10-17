package io.github.impca201.launcherforsimkl.ui.home

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Date

/** The current time, updated on the minute. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** The time in the device's 12- or 24-hour format. */
@Composable
fun Clock() {
    val now = rememberNow()
    val context = LocalContext.current
    Text(DateFormat.getTimeFormat(context).format(Date(now)), style = MaterialTheme.typography.headlineSmall)
}

/** Today, written out in the user's language ("Friday 17 October"). */
@Composable
fun rememberDate(): String {
    val now = rememberNow()
    val locale = Locale.current.platformLocale
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
    return remember(now, pattern) { java.text.SimpleDateFormat(pattern, locale).format(Date(now)) }
}
