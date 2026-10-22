package app.lumen.tv.ui.screens.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.update.UpdateState
import app.lumen.tv.R
import app.lumen.tv.data.settings.StreamingQuality
import app.lumen.tv.data.settings.SearchKeyboard
import app.lumen.tv.data.settings.SubtitleMode
import app.lumen.tv.ui.screens.home.homeSectionTitle
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(onOpenPrivacy: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val caps = viewModel.capabilities
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    var editingTmdb by remember { mutableStateOf(false) }
    val tmdbKey by viewModel.tmdbKey.collectAsStateWithLifecycle()
    val tmdbCheck by viewModel.tmdbCheck.collectAsStateWithLifecycle()
    val tmdbSaving by viewModel.tmdbSaving.collectAsStateWithLifecycle()
    val firstRow = remember { FocusRequester() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .focusProperties { onEnter = { firstRow.requestFocus() } }
                .focusGroup(),
            contentPadding = PaddingValues(top = 100.dp, bottom = LumenSpacing.XXL),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { Section(stringResource(R.string.settings_account)) }
            item {
                SettingRow(
                    stringResource(R.string.settings_server),
                    "${state.serverName}  ·  ${state.serverUrl}",
                    modifier = Modifier.focusRequester(firstRow),
                )
            }
            item { SettingRow(stringResource(R.string.settings_user), state.userName) }
            item {
                SettingRow(
                    label = stringResource(if (confirmSignOut) R.string.settings_sign_out_confirm else R.string.settings_sign_out),
                    value = null,
                    destructive = true,
                    onClick = { if (confirmSignOut) viewModel.signOut() else confirmSignOut = true },
                )
            }

            if (sections.isNotEmpty()) {
                item { Section(stringResource(R.string.settings_home)) }
                items(sections, key = { "section-${it.key}" }) { section ->
                    SettingRow(
                        homeSectionTitle(section.kind, section.library),
                        stringResource(if (section.key in state.hiddenSections) R.string.settings_section_hidden else R.string.settings_section_shown),
                        onClick = { viewModel.toggleSection(section.key) },
                    )
                }
            }

            item { Section(stringResource(R.string.settings_seerr)) }
            item {
                SettingRow(
                    stringResource(R.string.settings_status),
                    if (state.seerrConnected) {
                        stringResource(R.string.settings_seerr_connected, state.seerrUrl.orEmpty())
                    } else {
                        stringResource(R.string.settings_seerr_not_connected)
                    },
                )
            }
            if (state.seerrConnected) {
                item { SettingRow(stringResource(R.string.settings_seerr_disconnect), null, destructive = true, onClick = viewModel::disconnectSeerr) }
            }

            item { Section(stringResource(R.string.settings_artwork)) }
            item {
                SettingRow(
                    stringResource(R.string.settings_tmdb_key),
                    tmdbKey?.let { stringResource(R.string.settings_tmdb_set, it.takeLast(4)) } ?: stringResource(R.string.settings_tmdb_not_set),
                    onClick = {
                        viewModel.clearTmdbCheck()
                        editingTmdb = true
                    },
                )
            }

            item { Section(stringResource(R.string.settings_playback)) }
            item {
                SettingRow(
                    stringResource(R.string.settings_quality),
                    stringResource(qualityLabel(state.settings.quality)),
                    onClick = viewModel::cycleQuality,
                )
            }
            item {
                SettingRow(
                    stringResource(R.string.settings_subtitles),
                    stringResource(subtitleLabel(state.settings.subtitles)),
                    onClick = viewModel::cycleSubtitles,
                )
            }
            item {
                SettingRow(
                    stringResource(R.string.settings_search_keyboard),
                    stringResource(
                        if (state.settings.searchKeyboard == SearchKeyboard.System) R.string.search_keyboard_system else R.string.search_keyboard_one_line,
                    ),
                    onClick = viewModel::toggleSearchKeyboard,
                )
            }

            item { Section(stringResource(R.string.settings_about)) }
            item { SettingRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME) }
            if (viewModel.updatesEnabled) {
                item {
                    SettingRow(
                        stringResource(R.string.settings_check_updates),
                        when (val update = updateState) {
                            UpdateState.Checking -> stringResource(R.string.settings_update_checking)
                            UpdateState.UpToDate -> stringResource(R.string.settings_update_current)
                            is UpdateState.Available -> stringResource(R.string.settings_update_available, update.release.version)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading, (update.progress * 100).toInt())
                            is UpdateState.Failed -> stringResource(R.string.settings_update_failed)
                            else -> null
                        },
                        onClick = viewModel::checkForUpdates,
                    )
                }
            }
            item { SettingRow(stringResource(R.string.settings_privacy), null, onClick = onOpenPrivacy) }
            item { SettingRow(stringResource(R.string.settings_video), caps.videoCodecs.joinToString(", ") { it.uppercase() }) }
            item { SettingRow(stringResource(R.string.settings_hdr), caps.videoRangeTypes.joinToString(", ")) }
            item { SettingRow(stringResource(R.string.settings_audio), caps.audioCodecs.filterNot { it.startsWith("pcm") }.joinToString(", ") { it.uppercase() }) }
            item { SettingRow(stringResource(R.string.settings_channels), caps.maxAudioChannels.toString()) }
            item {
                Text(
                    stringResource(R.string.settings_tmdb_attribution),
                    style = MaterialTheme.typography.labelMedium,
                    color = LumenColors.OnSurfaceFaint,
                    modifier = Modifier.padding(horizontal = LumenSpacing.M),
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_not_affiliated),
                    style = MaterialTheme.typography.labelMedium,
                    color = LumenColors.OnSurfaceFaint,
                    modifier = Modifier.padding(LumenSpacing.M),
                )
            }
        }
        if (editingTmdb) {
            TmdbKeyDialog(
                hasKey = tmdbKey != null,
                check = tmdbCheck,
                saving = tmdbSaving,
                onSave = { key -> viewModel.saveTmdbKey(key) { editingTmdb = false } },
                onRemove = {
                    viewModel.removeTmdbKey()
                    editingTmdb = false
                },
                onDismiss = { editingTmdb = false },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnSurfaceFaint,
        modifier = Modifier.padding(start = LumenSpacing.M, top = LumenSpacing.L, bottom = LumenSpacing.XS),
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Surface.copy(alpha = 0.6f),
            contentColor = if (destructive) LumenColors.Error else LumenColors.OnSurface,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = if (destructive) LumenColors.Error else LumenColors.OnFocusFill,
            pressedContainerColor = LumenColors.FocusFill,
        ),
    ) {
        Row(Modifier.padding(horizontal = LumenSpacing.L, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value != null) {
                Spacer(Modifier.width(LumenSpacing.L))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Unspecified,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
        }
    }
}

private fun qualityLabel(quality: StreamingQuality): Int = when (quality) {
    StreamingQuality.Auto -> R.string.quality_auto
    StreamingQuality.High -> R.string.quality_high
    StreamingQuality.Medium -> R.string.quality_medium
    StreamingQuality.Low -> R.string.quality_low
    StreamingQuality.Minimum -> R.string.quality_minimum
}

private fun subtitleLabel(mode: SubtitleMode): Int = when (mode) {
    SubtitleMode.Off -> R.string.subtitles_off
    SubtitleMode.ForcedOnly -> R.string.subtitles_forced
    SubtitleMode.Always -> R.string.subtitles_always
}
