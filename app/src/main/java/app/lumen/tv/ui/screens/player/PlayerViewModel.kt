package app.lumen.tv.ui.screens.player

import android.content.Context
import androidx.core.net.toUri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.model.MediaKind
import app.lumen.tv.playback.PlaybackRepository
import app.lumen.tv.playback.PlaybackExtras
import app.lumen.tv.playback.PlaybackSource
import app.lumen.tv.playback.Segment
import app.lumen.tv.playback.SegmentType
import app.lumen.tv.playback.Trickplay
import app.lumen.tv.data.settings.TrackChoice
import androidx.media3.exoplayer.analytics.AnalyticsListener
import app.lumen.tv.playback.TrailerStreamResolver
import app.lumen.tv.data.livetv.Channel
import app.lumen.tv.data.livetv.LiveTvRepository
import app.lumen.tv.data.livetv.Programme
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import app.lumen.tv.data.settings.SettingsStore
import app.lumen.tv.data.settings.SubtitleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PlayMethod
import java.util.Locale
import java.util.UUID
import androidx.media3.common.MediaItem as ExoMediaItem

data class PlayerUiState(
    val item: MediaItem? = null,
    val loading: Boolean = true,
    val error: Boolean = false,
    val method: PlayMethod? = null,
    val upNext: MediaItem? = null,
    val upNextSeconds: Int? = null,
    val finished: Boolean = false,
    val trailerTitle: String? = null,
    val backdropUrl: String? = null,
    val trailerUnavailable: Boolean = false,
    val liveChannel: Channel? = null,
    val liveGuide: List<Programme> = emptyList(),
    val skip: Segment? = null,
    val trickplay: Trickplay? = null,
    val showStats: Boolean = false,
)

data class PlaybackStats(
    val method: PlayMethod?,
    val transcodeReasons: List<String>,
    val container: String?,
    val video: String?,
    val audio: String?,
    val bitrate: Int?,
    val bufferSeconds: Long,
    val droppedFrames: Int,
)

data class LiveRequest(val channelKey: String)

data class TrailerRequest(val youtubeKey: String, val title: String, val backdropUrl: String?)

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val format: Format,
    val selected: Boolean,
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    context: Context,
    private val itemId: UUID,
    private val startTicks: Long,
    private val repository: PlaybackRepository,
    private val appScope: CoroutineScope,
    private val settings: SettingsStore,
    private val trailer: TrailerRequest? = null,
    private val trailerResolver: TrailerStreamResolver? = null,
    private val live: LiveRequest? = null,
    private val liveRepository: LiveTvRepository? = null,
) : ViewModel() {
    private var liveLineup: List<Channel> = emptyList()
    private var guideJob: Job? = null

    private val _state = MutableStateFlow(PlayerUiState(trailerTitle = trailer?.title, backdropUrl = trailer?.backdropUrl))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Lumen/${BuildConfig.VERSION_NAME}")
        .setAllowCrossProtocolRedirects(true)

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setEnableDecoderFallback(true),
    )
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    private val mediaSession = MediaSession.Builder(context, player).setId("lumen-${UUID.randomUUID()}").build()

    private var source: PlaybackSource? = null
    private var fellBackToTranscode = false
    private var stopped = false
    private var upNextJob: Job? = null
    private var extras = PlaybackExtras()
    private val skippedSegments = mutableSetOf<Segment>()
    private var creditsHandled = false
    private var autoSkip = false
    private var droppedFrames = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = reportProgress()

            override fun onTracksChanged(tracks: Tracks) {
                _tracks.value = tracks
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) _state.update { it.copy(loading = false) }
                if (playbackState == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) = onError()
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) {
                droppedFrames += count
            }
        })
        viewModelScope.launch {
            while (isActive) {
                delay(SEGMENT_CHECK_MS)
                checkSegments()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                if (player.isPlaying) reportProgress()
            }
        }
        when {
            live != null -> startLive(live.channelKey)
            trailer != null -> startTrailer(trailer)
            else -> start(itemId, startTicks)
        }
    }

    private fun startLive(key: String) {
        viewModelScope.launch {
            val repository = liveRepository ?: return@launch
            val channel = repository.channel(key)
            if (channel == null) {
                _state.update { it.copy(loading = false, error = true) }
                return@launch
            }
            liveLineup = repository.playlist()?.channels.orEmpty().filter { it.group == channel.group }.sortedBy { it.number }.ifEmpty { listOf(channel) }
            tune(channel)
        }
    }

    private fun tune(channel: Channel) {
        _state.update { it.copy(liveChannel = channel, liveGuide = emptyList(), loading = true, error = false) }
        httpFactory.setDefaultRequestProperties(emptyMap())
        val media = ExoMediaItem.Builder()
            .setUri(channel.url)
            .setMimeType(if (channel.url.contains(".m3u8", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else null)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(channel.name).build())
            .build()
        player.setMediaItem(media)
        player.prepare()
        player.play()
        guideJob?.cancel()
        guideJob = viewModelScope.launch {
            val guide = runCatching { liveRepository?.guideFor(listOf(channel)) }.getOrNull()
            val logo = guide?.logos?.get(channel.key)?.takeIf { channel.logo == null }
            _state.update {
                if (it.liveChannel?.key != channel.key) return@update it
                it.copy(liveGuide = guide?.programmes?.get(channel.key).orEmpty(), liveChannel = logo?.let { url -> channel.copy(logo = url) } ?: channel)
            }
        }
    }

    fun zap(delta: Int) {
        val current = _state.value.liveChannel ?: return
        if (liveLineup.isEmpty()) return
        val index = liveLineup.indexOfFirst { it.key == current.key }.coerceAtLeast(0)
        tune(liveLineup[(index + delta).mod(liveLineup.size)])
    }

    private fun startTrailer(request: TrailerRequest) {
        viewModelScope.launch {
            val resolved = trailerResolver?.resolve(request.youtubeKey)
            if (resolved == null) {
                _state.update { it.copy(loading = false, trailerUnavailable = true) }
                return@launch
            }
            httpFactory.setDefaultRequestProperties(emptyMap())
            val progressive = ProgressiveMediaSource.Factory(httpFactory)
            val metadata = MediaMetadata.Builder().setTitle(request.title).build()
            val video = progressive.createMediaSource(ExoMediaItem.Builder().setUri(resolved.videoUrl).setMediaMetadata(metadata).build())
            val media = resolved.audioUrl?.let { audioUrl ->
                MergingMediaSource(video, progressive.createMediaSource(ExoMediaItem.fromUri(audioUrl)))
            } ?: video
            player.setMediaSource(media)
            player.prepare()
            player.play()
        }
    }

    private fun start(id: UUID, ticks: Long, known: MediaItem? = null) {
        _state.update { it.copy(loading = true, error = false, upNext = null, upNextSeconds = null, finished = false, skip = null, trickplay = null) }
        extras = PlaybackExtras()
        skippedSegments.clear()
        creditsHandled = false
        droppedFrames = 0
        viewModelScope.launch {
            val current = settings.current()
            autoSkip = current.autoSkipIntros
            applySubtitleMode(current.subtitles)
            runCatching {
                val item = known ?: repository.item(id)
                item.seriesId?.let { series -> settings.trackChoice(series.toString())?.let(::applyTrackChoice) }
                _state.update { it.copy(item = item, backdropUrl = item.backdropUrl) }
                play(repository.resolve(item, ticks, forceTranscode = false), ticks)
            }.onFailure { _state.update { it.copy(loading = false, error = true) } }
        }
    }

    private fun play(resolved: PlaybackSource, ticks: Long) {
        source = resolved
        stopped = false
        httpFactory.setDefaultRequestProperties(resolved.headers)
        val subtitles = resolved.subtitles.map { sub ->
            ExoMediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                .setId(sub.id)
                .setMimeType(sub.mimeType)
                .setLabel(sub.label)
                .setLanguage(sub.language)
                .build()
        }
        val media = ExoMediaItem.Builder()
            .setUri(resolved.url)
            .setMimeType(if (resolved.isHls) MimeTypes.APPLICATION_M3U8 else null)
            .setSubtitleConfigurations(subtitles)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(resolved.item.name)
                    .setSubtitle(if (resolved.item.kind == MediaKind.Episode) resolved.item.title else null)
                    .build(),
            )
            .build()
        player.setMediaItem(media, ticks / TICKS_PER_MS)
        player.prepare()
        player.play()
        _state.update { it.copy(method = resolved.method) }
        appScope.launch { repository.reportStart(resolved, ticks, paused = false) }
        viewModelScope.launch {
            val loaded = repository.extras(resolved)
            if (source !== resolved) return@launch
            extras = loaded
            _state.update { it.copy(trickplay = loaded.trickplay) }
        }
    }

    private fun checkSegments() {
        if (live != null || trailer != null || source == null || _state.value.loading) return
        val position = player.currentPosition
        val active = extras.segments.firstOrNull { position >= it.startMs && position < it.endMs - SEGMENT_END_MARGIN_MS }
        when {
            active == null -> if (_state.value.skip?.type != SegmentType.Credits || _state.value.upNext != null) _state.update { it.copy(skip = null) }
            active.type == SegmentType.Credits -> onCredits(active)
            autoSkip && active !in skippedSegments -> {
                skippedSegments += active
                player.seekTo(active.endMs)
            }
            else -> _state.update { it.copy(skip = active) }
        }
    }

    private fun onCredits(segment: Segment) {
        if (creditsHandled) return
        creditsHandled = true
        val item = _state.value.item ?: return
        if (item.kind != MediaKind.Episode) {
            _state.update { it.copy(skip = segment) }
            return
        }
        upNextJob = viewModelScope.launch {
            val next = repository.nextEpisode(item)
            if (next == null) _state.update { it.copy(skip = segment) } else countdown(next)
        }
    }

    fun skipSegment() {
        val segment = _state.value.skip ?: return
        _state.update { it.copy(skip = null) }
        if (segment.type == SegmentType.Credits) {
            reportStopped(player.duration.coerceAtLeast(player.currentPosition) * TICKS_PER_MS)
            player.pause()
            _state.update { it.copy(finished = true) }
        } else {
            skippedSegments += segment
            player.seekTo(segment.endMs)
        }
    }

    private suspend fun countdown(next: MediaItem) {
        for (seconds in UP_NEXT_SECONDS downTo 1) {
            _state.update { it.copy(upNext = next, upNextSeconds = seconds, skip = null) }
            delay(1_000)
        }
        playUpNext()
    }

    fun toggleStats() = _state.update { it.copy(showStats = !it.showStats) }

    fun stats(): PlaybackStats {
        val video = player.videoFormat
        val audio = player.audioFormat
        return PlaybackStats(
            method = source?.method,
            transcodeReasons = source?.transcodeReasons.orEmpty(),
            container = source?.container,
            video = video?.let {
                listOfNotNull(
                    it.codecs ?: it.sampleMimeType?.substringAfter('/'),
                    if (it.width > 0) "${it.width}×${it.height}" else null,
                    it.frameRate.takeIf { rate -> rate > 0 }?.let { rate -> "%.3g fps".format(rate) },
                ).joinToString(" · ")
            },
            audio = audio?.let {
                listOfNotNull(
                    it.codecs ?: it.sampleMimeType?.substringAfter('/'),
                    it.channelCount.takeIf { count -> count > 0 }?.let { count -> "$count ch" },
                    it.sampleRate.takeIf { rate -> rate > 0 }?.let { rate -> "${rate / 1000} kHz" },
                    it.language,
                ).joinToString(" · ")
            },
            bitrate = (video?.bitrate?.takeIf { it > 0 } ?: 0) + (audio?.bitrate?.takeIf { it > 0 } ?: 0),
            bufferSeconds = ((player.bufferedPosition - player.currentPosition) / 1000).coerceAtLeast(0),
            droppedFrames = droppedFrames,
        )
    }

    private fun onError() {
        if (live != null) {
            _state.update { it.copy(loading = false, error = true) }
            return
        }
        if (trailer != null) {
            _state.update { it.copy(loading = false, trailerUnavailable = true) }
            return
        }
        val current = source
        val item = _state.value.item
        if (current != null && item != null && current.method != PlayMethod.TRANSCODE && !fellBackToTranscode) {
            fellBackToTranscode = true
            val ticks = player.currentPosition * TICKS_PER_MS
            viewModelScope.launch {
                runCatching { play(repository.resolve(item, ticks, forceTranscode = true), ticks) }
                    .onFailure { _state.update { it.copy(loading = false, error = true) } }
            }
        } else {
            _state.update { it.copy(loading = false, error = true) }
        }
    }

    private fun onEnded() {
        if (live != null) return
        if (trailer != null) {
            _state.update { it.copy(finished = true) }
            return
        }
        val item = _state.value.item ?: return
        reportStopped(player.duration.coerceAtLeast(0) * TICKS_PER_MS)
        if (item.kind != MediaKind.Episode) {
            _state.update { it.copy(finished = true) }
            return
        }
        if (upNextJob?.isActive == true) return
        upNextJob = viewModelScope.launch {
            val next = repository.nextEpisode(item)
            if (next == null) {
                _state.update { it.copy(finished = true) }
                return@launch
            }
            countdown(next)
        }
    }

    fun playUpNext() {
        val next = _state.value.upNext ?: return
        upNextJob?.cancel()
        if (!stopped) reportStopped(player.currentPosition * TICKS_PER_MS)
        fellBackToTranscode = false
        start(next.id, 0L, next)
    }

    fun cancelUpNext() {
        upNextJob?.cancel()
        val ended = player.playbackState == Player.STATE_ENDED
        _state.update { it.copy(upNext = null, upNextSeconds = null, finished = ended) }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
        reportProgress()
    }

    fun audioOptions(): List<TrackOption> = options(C.TRACK_TYPE_AUDIO)

    fun subtitleOptions(): List<TrackOption> = options(C.TRACK_TYPE_TEXT)

    fun subtitlesOff(): Boolean = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
        options(C.TRACK_TYPE_TEXT).none { it.selected }

    fun select(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
        val language = option.format.language
        when (group.type) {
            C.TRACK_TYPE_AUDIO -> rememberChoice { it.copy(audioLanguage = language ?: it.audioLanguage) }
            C.TRACK_TYPE_TEXT -> rememberChoice { it.copy(subtitleLanguage = language, subtitlesOff = false) }
        }
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        rememberChoice { it.copy(subtitlesOff = true) }
    }

    private fun rememberChoice(transform: (TrackChoice) -> TrackChoice) {
        val series = _state.value.item?.seriesId ?: return
        appScope.launch { settings.saveTrackChoice(series.toString(), transform) }
    }

    private fun applyTrackChoice(choice: TrackChoice) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            choice.audioLanguage?.let(::setPreferredAudioLanguage)
            when {
                choice.subtitlesOff -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                choice.subtitleLanguage != null -> {
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    setPreferredTextLanguage(choice.subtitleLanguage)
                }
            }
        }.build()
    }

    private fun applySubtitleMode(mode: SubtitleMode) {
        val language = Locale.getDefault().language
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, mode == SubtitleMode.Off)
            .setPreferredTextLanguage(if (mode == SubtitleMode.Always) language else null)
            .setSelectUndeterminedTextLanguage(mode == SubtitleMode.Always)
            .setPreferredAudioLanguage(language)
            .build()
    }

    fun stop() {
        if (!stopped) reportStopped(player.currentPosition * TICKS_PER_MS)
        player.stop()
    }

    private fun options(type: Int): List<TrackOption> =
        player.currentTracks.groups.withIndex()
            .filter { (_, group) -> group.type == type }
            .flatMap { (groupIndex, group) ->
                (0 until group.length)
                    .filter { group.isTrackSupported(it) }
                    .map { TrackOption(groupIndex, it, group.getTrackFormat(it), group.isTrackSelected(it)) }
            }

    private fun reportProgress() {
        val current = source ?: return
        if (stopped) return
        val ticks = player.currentPosition * TICKS_PER_MS
        val paused = !player.isPlaying
        appScope.launch { repository.reportProgress(current, ticks, paused) }
    }

    private fun reportStopped(ticks: Long) {
        val current = source ?: return
        stopped = true
        appScope.launch { repository.reportStopped(current, ticks) }
    }

    override fun onCleared() {
        if (!stopped) reportStopped(player.currentPosition * TICKS_PER_MS)
        mediaSession.release()
        player.release()
    }

    private companion object {
        const val TICKS_PER_MS = 10_000L
        const val SEEK_STEP_MS = 10_000L
        const val PROGRESS_INTERVAL_MS = 10_000L
        const val UP_NEXT_SECONDS = 10
        const val SEGMENT_CHECK_MS = 500L
        const val SEGMENT_END_MARGIN_MS = 1_000L
    }
}
