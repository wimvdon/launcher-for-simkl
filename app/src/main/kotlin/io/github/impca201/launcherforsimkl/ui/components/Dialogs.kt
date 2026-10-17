package io.github.impca201.launcherforsimkl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.impca201.launcherforsimkl.ui.theme.AppShapes
import io.github.impca201.launcherforsimkl.ui.theme.SurfaceRoles

data class DialogOption(
    val label: String,
    val icon: ImageVector? = null,
    val supporting: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A Material 3 dialog for the remote: a title, an optional message and a list of choices. The
 * first enabled choice has focus; Back closes it.
 */
@Composable
fun OptionsDialog(
    title: String,
    options: List<DialogOption>,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AppShapes.Dialog,
            colors = SurfaceDefaults.colors(containerColor = SurfaceRoles.containerHigh),
            modifier = Modifier.width(420.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }
                val focus = remember { FocusRequester() }
                val first = options.indexOfFirst { it.enabled }
                options.forEachIndexed { index, option ->
                    ListItem(
                        selected = false,
                        onClick = option.onClick,
                        enabled = option.enabled,
                        headlineContent = { Text(option.label) },
                        supportingContent = option.supporting?.let { { Text(it) } },
                        leadingContent = option.icon?.let { { Icon(it, contentDescription = null) } },
                        modifier = if (index == first) Modifier.focusRequester(focus) else Modifier,
                    )
                }
                LaunchedEffect(Unit) { if (first >= 0) focus.requestAfterLayout() }
            }
        }
    }
}
