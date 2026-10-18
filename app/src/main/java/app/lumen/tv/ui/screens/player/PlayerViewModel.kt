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
import app.lumen.tv.playback.PlaybackSource
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
        _state.update { it.copy(loading = true, error = false, upNext = null, upNextSeconds = null, finished = false) }
        viewModelScope.launch {
            applySubtitleMode(settings.current().subtitles)
            runCatching {
                val item = known ?: repository.item(id)
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
        upNextJob = viewModelScope.launch {
            val next = repository.nextEpisode(item)
            if (next == null) {
                _state.update { it.copy(finished = true) }
                return@launch
            }
            for (seconds in UP_NEXT_SECONDS downTo 1) {
                _state.update { it.copy(upNext = next, upNextSeconds = seconds) }
                delay(1_000)
            }
            playUpNext()
        }
    }

    fun playUpNext() {
        val next = _state.value.upNext ?: return
        upNextJob?.cancel()
        fellBackToTranscode = false
        start(next.id, 0L, next)
    }

    fun cancelUpNext() {
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null, upNextSeconds = null, finished = true) }
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
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
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
    }
}
