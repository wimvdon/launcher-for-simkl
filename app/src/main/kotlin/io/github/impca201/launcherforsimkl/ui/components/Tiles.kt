package io.github.impca201.launcherforsimkl.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.data.model.Images
import io.github.impca201.launcherforsimkl.data.model.Tile
import io.github.impca201.launcherforsimkl.ui.theme.AppShapes
import io.github.impca201.launcherforsimkl.ui.theme.SurfaceRoles

object TileSize {
    val PosterWidth = 116.dp
    val PosterHeight = 174.dp
    val AppWidth = 168.dp
    val AppHeight = 94.dp
}

/** Cards grow a little and get a light outline when focused, as TV Material's cards do. */
@Composable
private fun focusBorder() =
    CardDefaults.border(
        focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = AppShapes.Item),
    )

/** A title's poster. Episodes and paused titles show the episode and how far it was watched. */
@Composable
fun PosterTile(
    tile: Tile,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            modifier
                .size(TileSize.PosterWidth, TileSize.PosterHeight)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics { contentDescription = description },
        shape = CardDefaults.shape(AppShapes.Item),
        colors = CardDefaults.colors(containerColor = SurfaceRoles.containerHigh),
        border = focusBorder(),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The title shows until (or when no) poster loads.
            Text(
                tile.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            AsyncImage(
                model = Images.poster(tile.poster),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (tile.episode != null || tile.progress != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tile.episode?.let {
                        Text(it.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
                    }
                    tile.progress?.let { ProgressBar((it / 100).toFloat()) }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

/** An installed app: its TV banner, or its icon and name when it has no banner. */
@Composable
fun AppTile(
    app: AppEntry,
    loadPicture: suspend (String, Boolean) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var picture by remember(app.packageName, app.hasBanner) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName, app.hasBanner) {
        picture = loadPicture(app.packageName, app.hasBanner)?.let { d ->
            val (w, h) = if (app.hasBanner) 320 to 180 else 144 to 144
            runCatching { d.toBitmap(w, h).asImageBitmap() }.getOrNull()
        }
    }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier =
            modifier
                .size(TileSize.AppWidth, TileSize.AppHeight)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics { contentDescription = app.label },
        shape = CardDefaults.shape(AppShapes.Item),
        colors = CardDefaults.colors(containerColor = SurfaceRoles.containerHigh),
        border = focusBorder(),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        val bitmap = picture
        if (app.hasBanner && bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A tile that does something (All apps, Sign in) instead of showing content. */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TileSize.AppWidth,
    height: Dp = TileSize.AppHeight,
    supporting: String? = null,
    onFocused: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.size(width, height).onFocusChanged { if (it.isFocused) onFocused() },
        shape = CardDefaults.shape(AppShapes.Item),
        colors =
            CardDefaults.colors(
                containerColor = SurfaceRoles.containerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        border = focusBorder(),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(width - 24.dp).padding(top = 4.dp),
                )
            }
        }
    }
}

/** A programme from another app's channel. */
@Composable
fun ProgramTile(
    title: String,
    image: String?,
    portrait: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (w, h) = if (portrait) TileSize.PosterWidth to TileSize.PosterHeight else TileSize.AppWidth * 1.3f to TileSize.AppHeight * 1.3f
    Card(
        onClick = onClick,
        modifier = modifier.size(w, h).semantics { contentDescription = title },
        shape = CardDefaults.shape(AppShapes.Item),
        colors = CardDefaults.colors(containerColor = SurfaceRoles.containerHigh),
        border = focusBorder(),
        scale = CardDefaults.scale(focusedScale = 1.08f),
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
