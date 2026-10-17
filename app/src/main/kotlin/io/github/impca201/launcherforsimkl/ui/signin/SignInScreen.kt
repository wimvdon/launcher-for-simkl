package io.github.impca201.launcherforsimkl.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import io.github.impca201.launcherforsimkl.R
import io.github.impca201.launcherforsimkl.data.SignInFlow
import io.github.impca201.launcherforsimkl.data.SignInState
import io.github.impca201.launcherforsimkl.data.SyncError
import io.github.impca201.launcherforsimkl.ui.components.requestAfterLayout
import io.github.impca201.launcherforsimkl.ui.theme.AppShapes
import io.github.impca201.launcherforsimkl.ui.theme.SurfaceRoles
import io.github.impca201.launcherforsimkl.ui.theme.TvSpacing

@Composable
fun SignInScreen(
    flow: SignInFlow,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var state by remember { mutableStateOf<SignInState>(SignInState.Starting) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) { flow.run { state = it } }
    LaunchedEffect(state) { if (state == SignInState.Done) onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = TvSpacing.SafeHorizontal, vertical = TvSpacing.SafeVertical),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            SignInState.Starting, SignInState.Done -> Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium)
            is SignInState.ShowCode -> ShowCode(s, onCancel)
            SignInState.Expired ->
                Message(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.sign_in_expired),
                    text = null,
                    primary = stringResource(R.string.sign_in_new_code) to { attempt++ },
                    secondary = stringResource(R.string.cancel) to onCancel,
                )
            is SignInState.Failed ->
                if (s.error == SyncError.NoClientId) {
                    Message(
                        icon = Icons.Rounded.Key,
                        title = stringResource(R.string.no_client_id_title),
                        text = stringResource(R.string.no_client_id_text),
                        primary = stringResource(R.string.back) to onCancel,
                        secondary = null,
                    )
                } else {
                    Message(
                        icon = Icons.Rounded.CloudOff,
                        title = stringResource(R.string.sign_in_offline),
                        text = null,
                        primary = stringResource(R.string.retry) to { attempt++ },
                        secondary = stringResource(R.string.cancel) to onCancel,
                    )
                }
        }
    }
}

@Composable
private fun ShowCode(
    s: SignInState.ShowCode,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.widthIn(max = 640.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.sign_in_steps, s.url.removePrefix("https://").removePrefix("http://")),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Surface(
            shape = AppShapes.Group,
            colors = SurfaceDefaults.colors(containerColor = SurfaceRoles.containerHigh),
            modifier = Modifier.semantics { contentDescription = s.code.toList().joinToString(" ") },
        ) {
            Text(
                s.code,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 8.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
            )
        }
        Text(
            stringResource(R.string.sign_in_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.cancel)) }
    }
    LaunchedEffect(Unit) { focus.requestAfterLayout() }
}

/** The shared empty/setup state: an icon in a circle, a title, optional text and up to two buttons. */
@Composable
fun Message(
    icon: ImageVector,
    title: String,
    text: String?,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
) {
    val focus = remember { FocusRequester() }
    Column(
        Modifier.widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(72.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = primary.second, modifier = Modifier.focusRequester(focus)) { Text(primary.first) }
            if (secondary != null) OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
        }
    }
    LaunchedEffect(title) { focus.requestAfterLayout() }
}
