package app.lumen.tv.ui.screens.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.lumen.tv.R
import app.lumen.tv.data.livetv.Channel
import app.lumen.tv.data.livetv.Programme
import app.lumen.tv.ui.components.LocalNavFocused
import app.lumen.tv.ui.components.PillButton
import app.lumen.tv.ui.components.PillStyle
import app.lumen.tv.ui.components.ProgressPill
import app.lumen.tv.ui.components.Spinner
import app.lumen.tv.ui.components.TvTextField
import app.lumen.tv.ui.theme.LumenColors
import app.lumen.tv.ui.theme.LumenShapes
import app.lumen.tv.ui.theme.LumenSpacing
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val NavClearance = 92.dp
private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private const val GUIDE_WINDOW_MINUTES = 180L

@Composable
fun LiveTvScreen(
    onWatch: (Channel) -> Unit,
    viewModel: LiveTvViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val focused by viewModel.focused.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().padding(top = NavClearance)) {
        when (val current = state) {
            LiveTvState.Loading -> Spinner(Modifier.align(Alignment.Center))
            is LiveTvState.Setup -> Setup(
                current = current.current,
                canCancel = current.canCancel,
                onSave = viewModel::saveSources,
                onCancel = viewModel::cancelEdit,
            )
            LiveTvState.Error -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.live_error), style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
                Spacer(Modifier.height(LumenSpacing.L))
                Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M)) {
                    PillButton(text = stringResource(R.string.action_retry), onClick = { viewModel.load(forceRefresh = true) }, style = PillStyle.Primary)
                    PillButton(text = stringResource(R.string.live_edit_sources), onClick = viewModel::editSources)
                }
            }
            is LiveTvState.Content -> Column(Modifier.fillMaxSize()) {
                GroupRow(groups = current.groups, group = current.group, onGroup = viewModel::selectGroup)
                val guide = current.guide
                Hero(
                    channel = focused,
                    programmes = focused?.let { guide[it.key] }.orEmpty(),
                    now = now,
                    loading = current.guideLoading,
                    mode = mode,
                    onToggleMode = viewModel::toggleMode,
                    onEdit = viewModel::editSources,
                )
                when (mode) {
                    LiveTvMode.Channels -> ChannelList(current.channels, guide, now, onFocus = viewModel::onFocus, onWatch = onWatch)
                    LiveTvMode.Guide -> GuideGrid(current.channels, guide, now, onFocus = viewModel::onFocus, onWatch = onWatch)
                }
            }
        }
    }
}

@Composable
private fun Setup(current: LiveTvSources, canCancel: Boolean, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var playlist by remember(current) { mutableStateOf(current.playlist) }
    var guide by remember(current) { mutableStateOf(current.guide) }
    BackHandler(enabled = canCancel, onBack = onCancel)
    Column(
        Modifier.fillMaxSize().padding(horizontal = LumenSpacing.SafeHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.live_setup_title), style = MaterialTheme.typography.displayMedium, color = LumenColors.OnSurface)
        Spacer(Modifier.height(LumenSpacing.M))
        Text(
            stringResource(R.string.live_setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(LumenSpacing.XL))
        TvTextField(
            value = playlist,
            onValueChange = { playlist = it },
            placeholder = stringResource(R.string.live_playlist_hint),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            width = 720.dp,
        )
        Spacer(Modifier.height(LumenSpacing.M))
        TvTextField(
            value = guide,
            onValueChange = { guide = it },
            placeholder = stringResource(R.string.live_guide_hint),
            keyboardType = KeyboardType.Uri,
            width = 720.dp,
        )
        Spacer(Modifier.height(LumenSpacing.L))
        Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.M)) {
            PillButton(
                text = stringResource(R.string.live_load),
                onClick = { onSave(playlist, guide) },
                style = PillStyle.Primary,
                enabled = playlist.startsWith("http"),
            )
            if (canCancel) PillButton(text = stringResource(R.string.player_cancel), onClick = onCancel)
        }
    }
}

@Composable
private fun GroupRow(groups: List<String>, group: String, onGroup: (String) -> Unit) {
    val selected = remember { FocusRequester() }
    val rowState = rememberLazyListState()
    LaunchedEffect(group) {
        val index = groups.indexOf(group)
        if (index > 0) rowState.scrollToItem(index)
    }
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { onEnter = { selected.requestFocus() } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S),
        contentPadding = PaddingValues(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.S),
    ) {
        items(groups, key = { it }) { name ->
            PillButton(
                text = name,
                onClick = { onGroup(name) },
                style = if (name == group) PillStyle.Selected else PillStyle.Secondary,
                modifier = if (name == group) Modifier.focusRequester(selected) else Modifier,
            )
        }
    }
}

@Composable
private fun Hero(
    channel: Channel?,
    programmes: List<Programme>,
    now: Instant,
    loading: Boolean,
    mode: LiveTvMode,
    onToggleMode: () -> Unit,
    onEdit: () -> Unit,
) {
    val onNow = programmes.onNow(now)
    val next = programmes.upNext(now)
    Row(
        Modifier.fillMaxWidth().height(150.dp).padding(horizontal = LumenSpacing.SafeHorizontal, vertical = LumenSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel != null) {
            ChannelLogo(channel, Modifier.width(160.dp).height(90.dp))
            Spacer(Modifier.width(LumenSpacing.L))
        }
        Column(Modifier.weight(1f)) {
            if (channel == null) return@Column
            Text(
                "${channel.number}  ·  ${channel.name}",
                style = MaterialTheme.typography.labelLarge,
                color = LumenColors.OnSurfaceMuted,
                maxLines = 1,
            )
            Text(
                onNow?.title ?: stringResource(
                    when {
                        loading -> R.string.live_guide_loading
                        else -> R.string.live_no_guide
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = LumenColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onNow != null) {
                Spacer(Modifier.height(LumenSpacing.XS))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${TIME.format(onNow.start)} – ${TIME.format(onNow.stop)}", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
                    Spacer(Modifier.width(LumenSpacing.M))
                    ProgressPill(onNow.progress(now), Modifier.width(200.dp).height(4.dp))
                    Spacer(Modifier.width(LumenSpacing.M))
                    Text(
                        stringResource(R.string.live_minutes_left, Duration.between(now, onNow.stop).toMinutes().coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelLarge,
                        color = LumenColors.OnSurfaceMuted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                (onNow.subtitle ?: onNow.description)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(LumenSpacing.L))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
            if (next != null) {
                Text(
                    stringResource(R.string.live_next_inline, TIME.format(next.start), next.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = LumenColors.OnSurfaceFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LumenSpacing.S)) {
                PillButton(
                    text = stringResource(if (mode == LiveTvMode.Channels) R.string.live_show_guide else R.string.live_show_channels),
                    onClick = onToggleMode,
                )
                PillButton(text = stringResource(R.string.live_edit_sources), onClick = onEdit)
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<Channel>,
    guide: Map<String, List<Programme>>,
    now: Instant,
    onFocus: (Channel) -> Unit,
    onWatch: (Channel) -> Unit,
) {
    val first = remember { FocusRequester() }
    val navFocused = LocalNavFocused.current
    LaunchedEffect(channels) { if (!navFocused) runCatching { first.requestFocus() } }
    LazyColumn(
        contentPadding = PaddingValues(start = LumenSpacing.SafeHorizontal, end = LumenSpacing.SafeHorizontal, bottom = LumenSpacing.XXL),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(channels, key = { it.key }) { channel ->
            val programmes = guide[channel.key].orEmpty()
            val onNow = programmes.onNow(now)
            Surface(
                onClick = { onWatch(channel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (channel == channels.first()) Modifier.focusRequester(first) else Modifier)
                    .onFocusChanged { if (it.isFocused) onFocus(channel) },
                shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = LumenColors.Surface.copy(alpha = 0.5f),
                    contentColor = LumenColors.OnSurface,
                    focusedContainerColor = LumenColors.FocusFill,
                    focusedContentColor = LumenColors.OnFocusFill,
                ),
            ) {
                Row(Modifier.padding(horizontal = LumenSpacing.M, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(channel.number.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp))
                    ChannelLogo(channel, Modifier.width(72.dp).height(40.dp))
                    Spacer(Modifier.width(LumenSpacing.M))
                    Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(240.dp))
                    Spacer(Modifier.width(LumenSpacing.M))
                    Column(Modifier.weight(1f)) {
                        Text(onNow?.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (onNow != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressPill(onNow.progress(now), Modifier.width(140.dp).height(3.dp), color = LocalContentColor.current)
                                Spacer(Modifier.width(LumenSpacing.S))
                                Text(
                                    "${TIME.format(onNow.start)} – ${TIME.format(onNow.stop)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Unspecified,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            }
                        }
                    }
                    programmes.upNext(now)?.let { next ->
                        Text(
                            stringResource(R.string.live_next_inline, TIME.format(next.start), next.title),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = LumenSpacing.M).width(280.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideGrid(
    channels: List<Channel>,
    guide: Map<String, List<Programme>>,
    now: Instant,
    onFocus: (Channel) -> Unit,
    onWatch: (Channel) -> Unit,
) {
    val windowStart = now.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofMinutes(if (now.atZone(ZoneId.systemDefault()).minute >= 30) 30 else 0))
    val windowEnd = windowStart.plus(Duration.ofMinutes(GUIDE_WINDOW_MINUTES))
    val channelColumn = 220.dp
    val first = remember { FocusRequester() }
    val navFocused = LocalNavFocused.current
    LaunchedEffect(channels) { if (!navFocused) runCatching { first.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = LumenSpacing.SafeHorizontal)) {
        val timelineWidth = maxWidth - channelColumn
        val perMinute = timelineWidth / GUIDE_WINDOW_MINUTES.toFloat()
        Column {
            Row(Modifier.padding(start = channelColumn).height(28.dp)) {
                (0 until GUIDE_WINDOW_MINUTES step 30).forEach { offset ->
                    Text(
                        TIME.format(windowStart.plus(Duration.ofMinutes(offset))),
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.OnSurfaceFaint,
                        modifier = Modifier.width(perMinute * 30),
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(contentPadding = PaddingValues(bottom = LumenSpacing.XXL), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(channels, key = { it.key }) { channel ->
                        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(channelColumn), verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(channel, Modifier.width(64.dp).height(36.dp))
                                Spacer(Modifier.width(LumenSpacing.S))
                                Text(
                                    channel.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LumenColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = LumenSpacing.S),
                                )
                            }
                            val visible = guide[channel.key].orEmpty().filter { it.stop.isAfter(windowStart) && it.start.isBefore(windowEnd) }
                            Box(Modifier.width(timelineWidth).fillMaxHeight()) {
                                if (visible.isEmpty()) {
                                    GuideCell(
                                        title = stringResource(R.string.live_no_guide),
                                        live = true,
                                        width = timelineWidth,
                                        offset = 0.dp,
                                        onClick = { onWatch(channel) },
                                        onFocused = { onFocus(channel) },
                                        modifier = if (channel == channels.first()) Modifier.focusRequester(first) else Modifier,
                                    )
                                }
                                visible.forEachIndexed { index, programme ->
                                    val start = maxOf(programme.start, windowStart)
                                    val end = minOf(programme.stop, windowEnd)
                                    GuideCell(
                                        title = programme.title,
                                        live = programme.isOn(now),
                                        width = perMinute * Duration.between(start, end).toMinutes().toFloat(),
                                        offset = perMinute * Duration.between(windowStart, start).toMinutes().toFloat(),
                                        onClick = { if (programme.isOn(now)) onWatch(channel) },
                                        onFocused = { onFocus(channel) },
                                        modifier = if (channel == channels.first() && index == 0) Modifier.focusRequester(first) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
                val nowOffset = perMinute * Duration.between(windowStart, now).toMinutes().toFloat()
                Box(
                    Modifier
                        .offset(x = channelColumn + nowOffset)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(LumenColors.Accent),
                )
            }
        }
    }
}

@Composable
private fun GuideCell(
    title: String,
    live: Boolean,
    width: Dp,
    offset: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .offset(x = offset)
            .width((width - 3.dp).coerceAtLeast(8.dp))
            .fillMaxHeight()
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(LumenShapes.Card),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (live) LumenColors.SurfaceRaised else LumenColors.Surface.copy(alpha = 0.6f),
            contentColor = if (live) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            focusedContainerColor = LumenColors.FocusFill,
            focusedContentColor = LumenColors.OnFocusFill,
        ),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White.copy(alpha = 0.08f), LumenShapes.Card), contentAlignment = Alignment.Center) {
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        } else {
            Text(channel.name.take(2).uppercase(), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
        }
    }
}
