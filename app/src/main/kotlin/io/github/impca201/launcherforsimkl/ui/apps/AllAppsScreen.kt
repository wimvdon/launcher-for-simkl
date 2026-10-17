package io.github.impca201.launcherforsimkl.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.model.AppEntry
import io.github.impca201.launcherforsimkl.ui.components.AppTile
import io.github.impca201.launcherforsimkl.ui.components.TileSize
import io.github.impca201.launcherforsimkl.ui.components.requestAfterLayout
import io.github.impca201.launcherforsimkl.ui.theme.TvSpacing

/** Every installed app, A to Z. OK opens one; holding OK pins it to the home screen or unpins it. */
@Composable
fun AllAppsScreen(
    apps: List<AppEntry>?,
    pinned: Set<String>,
    loadPicture: suspend (String, Boolean) -> android.graphics.drawable.Drawable?,
    onOpen: (AppEntry) -> Unit,
    onOptions: (AppEntry) -> Unit,
) {
    val first = remember { FocusRequester() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = TvSpacing.SafeVertical),
    ) {
        Text(
            stringResource(R.string.all_apps),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = TvSpacing.SafeHorizontal),
        )
        Text(
            stringResource(R.string.all_apps_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = TvSpacing.SafeHorizontal, vertical = 4.dp),
        )
        if (apps == null) return@Column
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.all_apps_empty), style = MaterialTheme.typography.titleMedium)
            }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(TileSize.AppWidth),
            contentPadding = PaddingValues(horizontal = TvSpacing.SafeHorizontal, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.TileGap),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.TileGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppTile(
                        app = app,
                        loadPicture = loadPicture,
                        onClick = { onOpen(app) },
                        onLongClick = { onOptions(app) },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                    // Banners often leave the name out; a pinned app is marked as such.
                    Text(
                        if (app.packageName in pinned) stringResource(R.string.app_label_pinned, app.label) else app.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        LaunchedEffect(Unit) { first.requestAfterLayout() }
    }
}
