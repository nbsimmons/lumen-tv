package app.lumen.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumen.tv.BuildConfig
import app.lumen.tv.R
import app.lumen.tv.data.update.UpdateRepository
import app.lumen.tv.data.update.UpdateState
import org.koin.compose.koinInject

@Composable
fun UpdateDialog(updates: UpdateRepository = koinInject()) {
    val prompt by updates.prompt.collectAsStateWithLifecycle()
    val state by updates.state.collectAsStateWithLifecycle()
    if (!prompt) return
    val title = stringResource(R.string.update_title)
    val cancel = DialogOption(stringResource(R.string.player_cancel)) { updates.dismiss() }
    when (val current = state) {
        is UpdateState.Available -> OptionsDialog(
            title = title,
            message = listOfNotNull(
                stringResource(R.string.update_available_body, current.release.version, BuildConfig.VERSION_NAME),
                current.release.notes?.take(NOTES_LIMIT),
            ).joinToString("\n\n"),
            options = listOf(
                DialogOption(stringResource(R.string.update_install)) { updates.install() },
                DialogOption(stringResource(R.string.update_later)) { updates.later() },
            ),
            onDismiss = {},
        )
        is UpdateState.Downloading -> OptionsDialog(
            title = title,
            message = stringResource(R.string.update_downloading, (current.progress * 100).toInt()),
            options = listOf(cancel),
            onDismiss = {},
        )
        is UpdateState.Installing -> OptionsDialog(
            title = title,
            message = stringResource(R.string.update_installing),
            options = listOf(DialogOption(stringResource(R.string.update_ok)) { updates.dismiss() }),
            onDismiss = {},
        )
        is UpdateState.NeedsPermission -> OptionsDialog(
            title = title,
            message = stringResource(R.string.update_permission_body),
            options = listOf(
                DialogOption(stringResource(R.string.update_open_settings)) { updates.openPermissionSettings() },
                DialogOption(stringResource(R.string.update_try_again)) { updates.install() },
                cancel,
            ),
            onDismiss = {},
        )
        is UpdateState.Failed -> OptionsDialog(
            title = title,
            message = stringResource(R.string.update_failed),
            options = listOf(DialogOption(stringResource(R.string.update_ok)) { updates.dismiss() }),
            onDismiss = {},
        )
        UpdateState.UpToDate -> OptionsDialog(
            title = title,
            message = stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
            options = listOf(DialogOption(stringResource(R.string.update_ok)) { updates.dismiss() }),
            onDismiss = {},
        )
        else -> Unit
    }
}

private const val NOTES_LIMIT = 400
