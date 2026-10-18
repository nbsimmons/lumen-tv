package app.lumen.tv.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import app.lumen.tv.ui.theme.LumenMotion
import app.lumen.tv.ui.theme.LumenSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step != OnboardingStep.Server) { viewModel.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(LumenColors.SurfaceRaised, LumenColors.Background), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                fadeIn(tween(LumenMotion.Medium, easing = LumenMotion.Standard)) togetherWith
                    fadeOut(tween(LumenMotion.Fast))
            },
            label = "onboarding",
        ) { step ->
            Column(
                Modifier.widthIn(max = 720.dp).padding(LumenSpacing.SafeHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (step) {
                    OnboardingStep.Server -> ServerStep(state, viewModel)
                    OnboardingStep.Method -> MethodStep(state, viewModel)
                    OnboardingStep.Password -> PasswordStep(state, viewModel)
                    OnboardingStep.QuickConnect -> QuickConnectStep(state, viewModel)
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(LumenSpacing.L))
                    Text(
                        text = stringResource(
                            when (error) {
                                OnboardingError.ServerUnreachable -> R.string.error_server_unreachable
                                OnboardingError.InvalidCredentials -> R.string.error_invalid_credentials
                                OnboardingError.QuickConnectFailed -> R.string.error_quickconnect
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
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurface, textAlign = TextAlign.Center)
    Spacer(Modifier.height(LumenSpacing.M))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurfaceMuted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(LumenSpacing.XL))
}

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    return requester
}

@Composable
private fun ServerStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Heading(stringResource(R.string.onboarding_welcome), stringResource(R.string.onboarding_server_prompt))
    val firstServer = remember { FocusRequester() }
    LaunchedEffect(state.discovered.isNotEmpty()) {
        if (state.discovered.isNotEmpty()) runCatching { firstServer.requestFocus() }
    }
    DiscoveredServers(
        servers = state.discovered,
        searching = state.discovering,
        onPick = viewModel::pick,
        firstModifier = Modifier.focusRequester(firstServer),
    )
    if (state.discovered.isNotEmpty() || state.discovering) Spacer(Modifier.height(LumenSpacing.L))
    TvTextField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        placeholder = stringResource(R.string.onboarding_server_hint),
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Go,
        onImeAction = viewModel::submitServer,
        modifier = Modifier.focusRequester(rememberInitialFocus()),
    )
    Spacer(Modifier.height(LumenSpacing.L))
    PillButton(
        text = stringResource(if (state.busy) R.string.onboarding_connecting else R.string.onboarding_continue),
        onClick = viewModel::submitServer,
        style = PillStyle.Primary,
        enabled = !state.busy,
    )
}

@Composable
private fun MethodStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val server = state.server
    Heading(
        stringResource(R.string.onboarding_signin_title, server?.name.orEmpty()),
        stringResource(R.string.onboarding_method_prompt),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M)) {
        PillButton(
            text = stringResource(R.string.onboarding_use_quickconnect),
            onClick = viewModel::chooseQuickConnect,
            style = PillStyle.Primary,
            modifier = Modifier.focusRequester(rememberInitialFocus()),
        )
        PillButton(text = stringResource(R.string.onboarding_use_password), onClick = viewModel::choosePassword)
    }
    if (server != null) {
        Spacer(Modifier.height(LumenSpacing.XL))
        Text(
            stringResource(R.string.onboarding_server_version, server.version) + "  ·  " + server.url,
            style = MaterialTheme.typography.labelMedium,
            color = LumenColors.OnSurfaceFaint,
        )
    }
}

@Composable
private fun PasswordStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Heading(
        stringResource(R.string.onboarding_signin_title, state.server?.name.orEmpty()),
        stringResource(R.string.onboarding_server_version, state.server?.version.orEmpty()),
    )
    if (state.server?.url?.startsWith("http://") == true) {
        Text(
            stringResource(R.string.onboarding_insecure),
            style = MaterialTheme.typography.bodyMedium,
            color = LumenColors.Pending,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(LumenSpacing.L))
    }
    TvTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        placeholder = stringResource(R.string.onboarding_username),
        imeAction = ImeAction.Next,
        modifier = Modifier.focusRequester(rememberInitialFocus()),
    )
    Spacer(Modifier.height(LumenSpacing.M))
    TvTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        placeholder = stringResource(R.string.onboarding_password),
        password = true,
        imeAction = ImeAction.Done,
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

@Composable
private fun QuickConnectStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Heading(stringResource(R.string.onboarding_use_quickconnect), stringResource(R.string.onboarding_qc_prompt))
    val code = state.quickConnectCode
    Text(
        text = code?.chunked(3)?.joinToString(" ") ?: "··· ···",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, letterSpacing = 12.sp),
        color = LumenColors.OnSurface,
    )
    Spacer(Modifier.height(LumenSpacing.L))
    Text(stringResource(R.string.onboarding_qc_waiting), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceFaint)
    Spacer(Modifier.height(LumenSpacing.XL))
    Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M)) {
        if (state.error != null) {
            PillButton(text = stringResource(R.string.action_retry), onClick = viewModel::chooseQuickConnect, style = PillStyle.Primary)
        }
        PillButton(
            text = stringResource(R.string.onboarding_use_password),
            onClick = viewModel::choosePassword,
            modifier = Modifier.focusRequester(rememberInitialFocus()),
        )
    }
}
