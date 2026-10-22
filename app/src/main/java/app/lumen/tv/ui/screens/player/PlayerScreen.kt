package app.lumen.tv.ui.screens.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
import app.lumen.tv.data.livetv.Channel
import app.lumen.tv.data.livetv.Programme
import app.lumen.tv.ui.screens.livetv.ChannelLogo
import app.lumen.tv.ui.screens.livetv.onNow
import app.lumen.tv.ui.screens.livetv.upNext
import app.lumen.tv.ui.components.ProgressPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import app.lumen.tv.ui.screens.trailer.TrailerActivity
import app.lumen.tv.ui.theme.LumenMotion
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.playback.SegmentType
import app.lumen.tv.playback.Trickplay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clip
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.PlayMethod
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.UUID

private const val LIVE_CLOCK_MS = 30_000L
private val LIVE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private const val OVERLAY_TIMEOUT_MS = 5_000L
private const val SEEK_MS = 10_000L
private const val SEEK_LONG_MS = 30_000L
private const val SCRUB_PREVIEW_MS = 1_500L
private val TRICKPLAY_PREVIEW_WIDTH = 240.dp

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    itemId: UUID,
    startTicks: Long,
    onExit: () -> Unit,
    trailer: TrailerRequest? = null,
    live: LiveRequest? = null,
    viewModel: PlayerViewModel = koinViewModel(
        key = live?.let { "live" } ?: trailer?.let { "trailer-${it.youtubeKey}" } ?: "player-$itemId-$startTicks",
    ) {
        when {
            live != null -> parametersOf(itemId, startTicks, live)
            trailer != null -> parametersOf(itemId, startTicks, trailer)
            else -> parametersOf(itemId, startTicks)
        }
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player = viewModel.player
    val rootFocus = remember { FocusRequester() }
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayToken by remember { mutableIntStateOf(0) }
    var panelOpen by remember { mutableStateOf(false) }
    var panelSection by remember { mutableStateOf(PanelSection.Subtitles) }
    var controlsFocused by remember { mutableStateOf(false) }
    val subtitlesButton = remember { FocusRequester() }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playWhenReady by remember { mutableStateOf(player.playWhenReady) }
    var scrubToken by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(scrubToken) {
        if (scrubToken == 0) return@LaunchedEffect
        scrubbing = true
        delay(SCRUB_PREVIEW_MS)
        scrubbing = false
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playWhenReady = ready
            }
        }
        player.addListener(listener)
        onDispose {
            view.keepScreenOn = false
            player.removeListener(listener)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    fun exit() {
        viewModel.stop()
        onExit()
    }

    fun poke() {
        overlayVisible = true
        overlayToken++
    }

    LaunchedEffect(overlayToken, playWhenReady, panelOpen) {
        if (!playWhenReady || panelOpen) return@LaunchedEffect
        delay(OVERLAY_TIMEOUT_MS)
        if (controlsFocused) {
            runCatching { rootFocus.requestFocus() }
            controlsFocused = false
        }
        overlayVisible = false
    }
    LaunchedEffect(state.finished) { if (state.finished) exit() }
    val context = LocalContext.current
    LaunchedEffect(state.trailerUnavailable) {
        if (state.trailerUnavailable && trailer != null) {
            context.startActivity(TrailerActivity.intent(context, trailer.youtubeKey, trailer.title))
            onExit()
        }
    }
    LaunchedEffect(panelOpen, state.upNext) {
        if (!panelOpen && state.upNext == null && !controlsFocused) runCatching { rootFocus.requestFocus() }
    }

    fun leaveControls() {
        runCatching { rootFocus.requestFocus() }
        controlsFocused = false
        poke()
    }

    fun openPanel(section: PanelSection) {
        panelSection = section
        panelOpen = true
        poke()
    }

    fun handleBack() {
        when {
            panelOpen -> {
                runCatching { if (controlsFocused) subtitlesButton.requestFocus() else rootFocus.requestFocus() }
                panelOpen = false
            }
            controlsFocused -> leaveControls()
            state.upNext != null -> {
                runCatching { rootFocus.requestFocus() }
                viewModel.cancelUpNext()
            }
            overlayVisible && isPlaying -> overlayVisible = false
            else -> exit()
        }
    }

    BackHandler { handleBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.key.nativeKeyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.type == KeyEventType.KeyUp) handleBack()
                    return@onPreviewKeyEvent true
                }
                if (event.type == KeyEventType.KeyDown && (panelOpen || controlsFocused)) overlayToken++
                if (panelOpen || controlsFocused || state.upNext != null || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount > 0
                if (state.liveChannel != null) {
                    when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> viewModel.zap(-1)
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> viewModel.zap(1)
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (overlayVisible) {
                                overlayVisible = false
                                return@onPreviewKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> viewModel.togglePlayPause()
                        KeyEvent.KEYCODE_MENU -> {
                            openPanel(PanelSection.Audio)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> Unit
                        else -> return@onPreviewKeyEvent false
                    }
                    poke()
                    return@onPreviewKeyEvent true
                }
                when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (state.skip != null) viewModel.skipSegment() else viewModel.togglePlayPause()
                        poke()
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.togglePlayPause()
                        poke()
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        player.play()
                        poke()
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        player.pause()
                        poke()
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        viewModel.seekBy(-(if (repeat) SEEK_LONG_MS else SEEK_MS))
                        scrubToken++
                        poke()
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        viewModel.seekBy(if (repeat) SEEK_LONG_MS else SEEK_MS)
                        scrubToken++
                        poke()
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        viewModel.seekBy(SEEK_LONG_MS)
                        scrubToken++
                        poke()
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (overlayVisible && !state.error && state.trailerTitle == null) {
                            controlsFocused = true
                            runCatching { subtitlesButton.requestFocus() }
                        }
                        poke()
                    }
                    KeyEvent.KEYCODE_MENU -> openPanel(PanelSection.Subtitles)
                    KeyEvent.KEYCODE_DPAD_UP -> poke()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        VideoSurface(player = player, modifier = Modifier.fillMaxSize())
        Subtitles(player, overlayVisible)
        state.liveChannel?.takeIf { it.isRadio }?.let { station -> RadioCard(station, state.liveGuide, Modifier.align(Alignment.Center)) }

        AnimatedVisibility(visible = state.loading && state.backdropUrl != null, enter = fadeIn(), exit = fadeOut(tween(LumenMotion.Slow))) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(model = state.backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }

        if (state.loading && !state.error) {
            Spinner(Modifier.align(Alignment.Center), size = 48.dp)
        }
        if (state.error && state.liveChannel != null) {
            Text(
                stringResource(R.string.live_channel_unavailable),
                style = MaterialTheme.typography.titleLarge,
                color = LumenColors.OnSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.error) {
            ErrorMessage(onExit = ::exit, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = overlayVisible && state.liveChannel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            state.liveChannel?.let { channel -> LiveOverlay(channel, state.liveGuide) }
        }

        AnimatedVisibility(
            visible = overlayVisible && !state.error && state.liveChannel == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ControlsOverlay(
                player = player,
                item = state.item,
                trailerTitle = state.trailerTitle,
                trickplay = state.trickplay.takeIf { scrubbing },
                isPlaying = isPlaying,
                viewModel = viewModel,
                subtitlesButton = subtitlesButton,
                onOpenPanel = ::openPanel,
                onLeave = ::leaveControls,
            )
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TrackPanel(viewModel = viewModel, method = state.method, section = panelSection)
        }

        state.skip?.takeIf { state.upNext == null && !panelOpen && !state.error }?.let { segment ->
            SkipButton(
                type = segment.type,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LumenSpacing.XXL, bottom = if (overlayVisible) 200.dp else LumenSpacing.XXL),
            )
        }

        if (state.showStats) {
            StatsOverlay(viewModel, Modifier.align(Alignment.TopStart).padding(LumenSpacing.XL))
        }

        state.upNext?.let { next ->
            UpNextCard(
                next = next,
                seconds = state.upNextSeconds ?: 0,
                onPlay = viewModel::playUpNext,
                onCancel = viewModel::cancelUpNext,
                modifier = Modifier.align(Alignment.BottomEnd).padding(LumenSpacing.XXL),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Subtitles(player: Player, overlayVisible: Boolean) {
    var subtitleView by remember { mutableStateOf<SubtitleView?>(null) }
    DisposableEffect(player, subtitleView) {
        val target = subtitleView
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                target?.setCues(cueGroup.cues)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        factory = { context ->
            SubtitleView(context).apply {
                val captioning = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
                if (captioning?.isEnabled == true) {
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                } else {
                    setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null,
                        ),
                    )
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
                }
                subtitleView = this
            }
        },
        update = { it.setBottomPaddingFraction(if (overlayVisible) 0.32f else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ControlsOverlay(
    player: Player,
    item: MediaItem?,
    trailerTitle: String?,
    trickplay: Trickplay?,
    isPlaying: Boolean,
    viewModel: PlayerViewModel,
    subtitlesButton: FocusRequester,
    onOpenPanel: (PanelSection) -> Unit,
    onLeave: () -> Unit,
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val audioValue = remember(tracks) { viewModel.audioOptions().firstOrNull { it.selected } }
    val subtitleValue = remember(tracks) {
        if (viewModel.subtitlesOff()) null else viewModel.subtitleOptions().firstOrNull { it.selected }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            buffered = player.bufferedPosition
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.XL),
        ) {
            if (item != null) {
                if (item.kind == MediaKind.Episode) {
                    Text(item.title, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
                }
                Text(
                    if (item.kind == MediaKind.Episode) item.subtitle.orEmpty() else item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = LumenColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(LumenSpacing.M))
            } else if (trailerTitle != null) {
                Text(stringResource(R.string.trailer_label), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
                Text(trailerTitle, style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(LumenSpacing.M))
            }
            trickplay?.takeIf { duration > 0 }?.let { preview ->
                TrickplayPreview(preview, position, (position.toFloat() / duration).coerceIn(0f, 1f))
                Spacer(Modifier.height(LumenSpacing.S))
            }
            ProgressTrack(position = position, buffered = buffered, duration = duration)
            Spacer(Modifier.height(LumenSpacing.S))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null,
                    tint = LumenColors.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(LumenSpacing.S))
                Text(formatTime(position), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                Spacer(Modifier.weight(1f))
                Text(
                    "-" + formatTime((duration - position).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
            if (trailerTitle == null) {
                Spacer(Modifier.height(LumenSpacing.M))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M),
                    modifier = Modifier.onPreviewKeyEvent { event ->
                        val up = event.type == KeyEventType.KeyDown && event.key.nativeKeyCode == KeyEvent.KEYCODE_DPAD_UP
                        if (up) onLeave()
                        up
                    },
                ) {
                    ControlButton(
                        icon = R.drawable.ic_subtitles,
                        label = stringResource(R.string.player_subtitles),
                        value = subtitleValue?.let { subtitleLabel(it.format) } ?: stringResource(R.string.player_off),
                        onClick = { onOpenPanel(PanelSection.Subtitles) },
                        modifier = Modifier.focusRequester(subtitlesButton),
                    )
                    ControlButton(
                        icon = R.drawable.ic_audio,
                        label = stringResource(R.string.player_audio),
                        value = audioValue?.let { audioLabel(it.format) },
                        onClick = { onOpenPanel(PanelSection.Audio) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Pill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = LumenColors.Glass,
            contentColor = LumenColors.OnSurface,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 280.dp).alpha(0.7f),
                )
            }
        }
    }
}

enum class PanelSection { Audio, Subtitles }

@Composable
private fun ProgressTrack(position: Long, buffered: Long, duration: Long) {
    val total = duration.coerceAtLeast(1)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Color.White.copy(alpha = 0.2f), LumenShapes.Pill),
    ) {
        Box(
            Modifier
                .fillMaxWidth((buffered.toFloat() / total).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f), LumenShapes.Pill),
        )
        Box(
            Modifier
                .fillMaxWidth((position.toFloat() / total).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(LumenColors.OnSurface, LumenShapes.Pill),
        )
    }
}

@Composable
private fun TrackPanel(viewModel: PlayerViewModel, method: PlayMethod?, section: PanelSection) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val audio = remember(tracks) { viewModel.audioOptions() }
    val subtitles = remember(tracks) { viewModel.subtitleOptions() }
    val subtitlesOff = remember(tracks) { viewModel.subtitlesOff() }
    val audioFocus = remember { FocusRequester() }
    val subtitleFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val wantsAudio = section == PanelSection.Audio && audio.isNotEmpty()
        if (!wantsAudio) listState.scrollToItem(audio.size + 1)
        runCatching { if (wantsAudio) audioFocus.requestFocus() else subtitleFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(380.dp)
            .padding(LumenSpacing.L)
            .background(LumenColors.Glass.copy(alpha = 0.92f), LumenShapes.Panel)
            .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
            .padding(vertical = LumenSpacing.M),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = LumenSpacing.M),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { PanelHeading(stringResource(R.string.player_audio)) }
            items(audio, key = { "a-${it.groupIndex}-${it.trackIndex}" }) { option ->
                val index = audio.indexOf(option)
                TrackRow(
                    label = audioLabel(option.format),
                    selected = option.selected,
                    onClick = { viewModel.select(option) },
                    modifier = if (option.selected || (index == 0 && audio.none { it.selected })) Modifier.focusRequester(audioFocus) else Modifier,
                )
            }
            item { PanelHeading(stringResource(R.string.player_subtitles)) }
            item {
                TrackRow(
                    label = stringResource(R.string.player_off),
                    selected = subtitlesOff,
                    onClick = viewModel::disableSubtitles,
                    modifier = Modifier.focusRequester(subtitleFocus),
                )
            }
            items(subtitles, key = { "s-${it.groupIndex}-${it.trackIndex}" }) { option ->
                TrackRow(
                    label = subtitleLabel(option.format),
                    selected = option.selected && !subtitlesOff,
                    onClick = { viewModel.select(option) },
                )
            }
            item {
                PanelHeading(stringResource(R.string.player_info))
            }
            item {
                TrackRow(
                    label = stringResource(R.string.player_show_info),
                    selected = viewModel.state.collectAsStateWithLifecycle().value.showStats,
                    onClick = viewModel::toggleStats,
                )
            }
            method?.let {
                item {
                    Text(
                        stringResource(
                            when (it) {
                                PlayMethod.DIRECT_PLAY -> R.string.player_method_direct
                                PlayMethod.DIRECT_STREAM -> R.string.player_method_stream
                                PlayMethod.TRANSCODE -> R.string.player_method_transcode
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.OnSurfaceFaint,
                        modifier = Modifier.padding(start = LumenSpacing.M, top = LumenSpacing.L),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(type: SegmentType, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(LumenColors.FocusFill, LumenShapes.Pill)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(
                when (type) {
                    SegmentType.Intro -> R.string.player_skip_intro
                    SegmentType.Recap -> R.string.player_skip_recap
                    SegmentType.Preview -> R.string.player_skip_preview
                    SegmentType.Credits -> R.string.player_skip_credits
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = LumenColors.OnFocusFill,
        )
        Text(stringResource(R.string.player_skip_hint), style = MaterialTheme.typography.labelMedium, color = LumenColors.OnFocusFill.copy(alpha = 0.6f))
    }
}

@Composable
private fun TrickplayPreview(trickplay: Trickplay, position: Long, fraction: Float) {
    val frame = trickplay.frame(position)
    val width = TRICKPLAY_PREVIEW_WIDTH
    val height = width * trickplay.height / trickplay.width.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val offset = ((maxWidth - width) * fraction).coerceAtLeast(0.dp)
        Column(Modifier.offset(x = offset), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(width, height)
                    .clip(LumenShapes.Card)
                    .border(2.dp, LumenColors.OnSurface, LumenShapes.Card)
                    .background(LumenColors.Surface),
            ) {
                AsyncImage(
                    model = frame.url,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize(width * trickplay.columns, height * trickplay.rows)
                        .offset(x = -width * frame.column, y = -height * frame.row),
                )
            }
            Spacer(Modifier.height(LumenSpacing.XS))
            Text(formatTime(position), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
        }
    }
}

@Composable
private fun StatsOverlay(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(viewModel.stats()) }
    LaunchedEffect(Unit) {
        while (true) {
            stats = viewModel.stats()
            delay(1_000)
        }
    }
    val unknown = stringResource(R.string.player_info_unknown)
    val rows = listOf(
        stringResource(R.string.player_info_method) to when (stats.method) {
            PlayMethod.DIRECT_PLAY -> stringResource(R.string.player_method_direct)
            PlayMethod.DIRECT_STREAM -> stringResource(R.string.player_method_stream)
            PlayMethod.TRANSCODE -> stringResource(R.string.player_method_transcode)
            null -> unknown
        },
        stringResource(R.string.player_info_reason) to stats.transcodeReasons.joinToString(", ") { reasonLabel(it) }.ifEmpty { "—" },
        stringResource(R.string.player_info_container) to (stats.container ?: unknown),
        stringResource(R.string.player_info_video) to (stats.video ?: unknown),
        stringResource(R.string.player_info_audio) to (stats.audio ?: unknown),
        stringResource(R.string.player_info_bitrate) to (stats.bitrate?.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1_000_000f) } ?: unknown),
        stringResource(R.string.player_info_buffer) to stringResource(R.string.player_info_seconds, stats.bufferSeconds),
        stringResource(R.string.player_info_dropped) to stats.droppedFrames.toString(),
    )
    Column(
        modifier
            .widthIn(max = 520.dp)
            .background(Color.Black.copy(alpha = 0.72f), LumenShapes.Card)
            .padding(LumenSpacing.M),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEach { (label, value) ->
            Row {
                Text(label, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint, modifier = Modifier.width(130.dp))
                Text(value, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurface)
            }
        }
    }
}

private fun reasonLabel(reason: String): String =
    reason.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace("Not Supported", "not supported")

@Composable
private fun PanelHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnSurfaceFaint,
        modifier = Modifier.padding(start = LumenSpacing.M, top = LumenSpacing.M, bottom = LumenSpacing.XS),
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = LumenColors.OnSurface,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
        ),
    ) {
        Row(Modifier.padding(horizontal = LumenSpacing.M, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(26.dp)) {
                if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UpNextCard(
    next: MediaItem,
    seconds: Int,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(next.id) { runCatching { playFocus.requestFocus() } }
    Row(
        modifier
            .background(LumenColors.Glass.copy(alpha = 0.92f), LumenShapes.Panel)
            .border(1.dp, LumenColors.GlassStroke, LumenShapes.Panel)
            .padding(LumenSpacing.M),
        horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = next.thumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(200.dp).aspectRatio(16f / 9f).background(LumenColors.Surface, LumenShapes.Card),
        )
        Column(Modifier.width(260.dp)) {
            Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceFaint)
            Text(next.subtitle.orEmpty(), style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface, maxLines = 2)
            Spacer(Modifier.height(LumenSpacing.M))
            Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
                PillButton(
                    text = stringResource(R.string.player_playing_in, seconds),
                    onClick = onPlay,
                    style = PillStyle.Primary,
                    modifier = Modifier.focusRequester(playFocus),
                )
                PillButton(text = stringResource(R.string.player_cancel), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun RadioCard(station: Channel, guide: List<Programme>, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LIVE_CLOCK_MS)
            now = Instant.now()
        }
    }
    val onNow = guide.onNow(now)
    Column(modifier.padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(station, Modifier.width(320.dp).height(180.dp))
        Spacer(Modifier.height(LumenSpacing.L))
        Text(station.name, style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurface)
        onNow?.let {
            Spacer(Modifier.height(LumenSpacing.S))
            Text(it.title, style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(LumenSpacing.S))
        Text(stringResource(R.string.live_radio_live), style = MaterialTheme.typography.labelLarge, color = LumenColors.Accent)
    }
}

@Composable
private fun LiveOverlay(channel: Channel, guide: List<Programme>) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(LIVE_CLOCK_MS)
            now = Instant.now()
        }
    }
    val onNow = guide.onNow(now)
    val next = guide.upNext(now)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.XL),
            verticalAlignment = Alignment.Bottom,
        ) {
            ChannelLogo(channel, Modifier.width(140.dp).height(80.dp))
            Spacer(Modifier.width(LumenSpacing.L))
            Column(Modifier.weight(1f)) {
                Text(
                    "${channel.number}  ·  ${channel.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = LumenColors.OnSurfaceMuted,
                )
                Text(
                    onNow?.title ?: channel.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = LumenColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onNow != null) {
                    Spacer(Modifier.height(LumenSpacing.S))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LIVE_TIME.format(onNow.start), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                        Spacer(Modifier.width(LumenSpacing.S))
                        ProgressPill(onNow.progress(now), Modifier.weight(1f).height(6.dp))
                        Spacer(Modifier.width(LumenSpacing.S))
                        Text(LIVE_TIME.format(onNow.stop), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
                    }
                }
                Spacer(Modifier.height(LumenSpacing.XS))
                Text(
                    next?.let { stringResource(R.string.live_next_inline, LIVE_TIME.format(it.start), it.title) }
                        ?: stringResource(R.string.live_zap_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = LumenColors.OnSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ErrorMessage(onExit: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.player_error), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
        Spacer(Modifier.height(LumenSpacing.L))
        PillButton(text = stringResource(R.string.player_back), onClick = onExit, modifier = Modifier.focusRequester(focus))
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
