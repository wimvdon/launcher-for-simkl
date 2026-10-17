package io.github.impca201.launcherforsimkl.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

// Material 3 tonal scheme (dark) from the icon's middle gradient stop, seed #E0408A. TV home screens
// are dark, so there is no light scheme; Material's TV guidance recommends dark themes too.
private val Colors =
    darkColorScheme(
        primary = Color(0xFFFFB0CB),
        onPrimary = Color(0xFF541D34),
        primaryContainer = Color(0xFF6F334B),
        onPrimaryContainer = Color(0xFFFFD9E3),
        inversePrimary = Color(0xFF8B4A62),
        secondary = Color(0xFFE2BDC7),
        onSecondary = Color(0xFF422932),
        secondaryContainer = Color(0xFF5A3F48),
        onSecondaryContainer = Color(0xFFFFD9E3),
        tertiary = Color(0xFFF0BC95),
        onTertiary = Color(0xFF48290D),
        tertiaryContainer = Color(0xFF623F21),
        onTertiaryContainer = Color(0xFFFFDCC3),
        background = Color(0xFF191114),
        onBackground = Color(0xFFEFDFE2),
        surface = Color(0xFF191114),
        onSurface = Color(0xFFEFDFE2),
        surfaceVariant = Color(0xFF514347),
        onSurfaceVariant = Color(0xFFD5C2C6),
        surfaceTint = Color(0xFFFFB0CB),
        inverseSurface = Color(0xFFEFDFE2),
        inverseOnSurface = Color(0xFF372E31),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        border = Color(0xFF9E8C91),
        borderVariant = Color(0xFF514347),
    )

/**
 * The surface container roles of the same scheme. TV Material's ColorScheme has no
 * surfaceContainer* roles, so the launcher's grouped surfaces (tiles without a picture, dialogs)
 * take them from here.
 */
object SurfaceRoles {
    val containerLow = Color(0xFF22191C)
    val container = Color(0xFF261D20)
    val containerHigh = Color(0xFF31282A)
    val containerHighest = Color(0xFF3C3235)
}

/** App-level shapes, as `AppShapes` in the sibling apps: tiles 12dp (TV cards), groups 24dp. */
object AppShapes {
    val Item = RoundedCornerShape(12.dp)
    val Group = RoundedCornerShape(24.dp)
    val Dialog = RoundedCornerShape(28.dp)
}

/**
 * Spacing for a 10-foot screen: content stays inside the TV overscan-safe area (5% of the screen:
 * 48dp left and right, 27dp top and bottom on a 960x540dp TV).
 */
object TvSpacing {
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val RowGap = 20.dp
    val TileGap = 16.dp
}

/** The theme plus a background surface, which also gives text its default colour (onSurface). */
@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = Colors.background, contentColor = Colors.onBackground),
        ) { content() }
    }
}

/** For windows that show only a dialog over another app (OpenActivity): no background. */
@Composable
fun LauncherDialogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
