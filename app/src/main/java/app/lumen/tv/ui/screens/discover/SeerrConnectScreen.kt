package app.lumen.tv.ui.screens.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.ui.components.DiscoveredServers
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.TvTextField
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun SeerrConnectScreen(viewModel: SeerrConnectViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != ConnectStep.Server) { viewModel.back() }
    val stepFocus = remember { FocusRequester() }
    LaunchedEffect(state.step) {
        if (state.step != ConnectStep.Server) runCatching { stepFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().padding(top = 92.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 720.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.seerr_connect_title),
                style = MaterialTheme.typography.displayMedium,
                color = LumenColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(LumenSpacing.M))
            when (state.step) {
                ConnectStep.Server -> {
                    Text(
                        stringResource(R.string.seerr_connect_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LumenColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(LumenSpacing.XL))
                    DiscoveredServers(servers = state.discovered, searching = state.discovering, onPick = viewModel::pick)
                    if (state.discovered.isNotEmpty() || state.discovering) Spacer(Modifier.height(LumenSpacing.L))
                    TvTextField(
                        value = state.url,
                        onValueChange = viewModel::onUrlChange,
                        placeholder = stringResource(R.string.seerr_url_hint),
                        keyboardType = KeyboardType.Uri,
                    )
                    Spacer(Modifier.height(LumenSpacing.L))
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M)) {
                        PillButton(
                            text = stringResource(if (state.busy) R.string.onboarding_connecting else R.string.onboarding_use_quickconnect),
                            onClick = viewModel::connectWithQuickConnect,
                            style = PillStyle.Primary,
                            enabled = !state.busy,
                        )
                        PillButton(
                            text = stringResource(R.string.onboarding_use_password),
                            onClick = viewModel::connectWithPassword,
                            enabled = !state.busy,
                        )
                    }
                }
                ConnectStep.QuickConnect -> {
                    Text(
                        stringResource(R.string.onboarding_qc_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LumenColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(LumenSpacing.L))
                    Text(
                        text = state.code?.chunked(3)?.joinToString(" ") ?: "··· ···",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, letterSpacing = 12.sp),
                        color = LumenColors.OnSurface,
                    )
                    Spacer(Modifier.height(LumenSpacing.M))
                    Text(stringResource(R.string.onboarding_qc_waiting), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceFaint)
                    Spacer(Modifier.height(LumenSpacing.L))
                    PillButton(
                        text = stringResource(R.string.onboarding_use_password),
                        onClick = viewModel::connectWithPassword,
                        modifier = Modifier.focusRequester(stepFocus),
                    )
                }
                ConnectStep.Password -> {
                    Spacer(Modifier.height(LumenSpacing.L))
                    TvTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChange,
                        placeholder = stringResource(R.string.onboarding_username),
                        imeAction = ImeAction.Next,
                        modifier = Modifier.focusRequester(stepFocus),
                    )
                    Spacer(Modifier.height(LumenSpacing.M))
                    TvTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        placeholder = stringResource(R.string.onboarding_password),
                        password = true,
                        onImeAction = viewModel::submitPassword,
                    )
                    Spacer(Modifier.height(LumenSpacing.L))
                    PillButton(
                        text = stringResource(if (state.busy) R.string.onboarding_signing_in else R.string.onboarding_sign_in),
                        onClick = viewModel::submitPassword,
                        style = PillStyle.Primary,
                        enabled = !state.busy,
                    )
                }
            }
            state.error?.let { error ->
                Spacer(Modifier.height(LumenSpacing.L))
                Text(
                    stringResource(
                        when (error) {
                            ConnectError.Unreachable -> R.string.seerr_error_unreachable
                            ConnectError.InvalidCredentials -> R.string.error_invalid_credentials
                            ConnectError.QuickConnectFailed -> R.string.error_quickconnect
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.Error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
