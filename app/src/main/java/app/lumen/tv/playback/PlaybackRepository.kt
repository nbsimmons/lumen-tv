package app.lumen.tv.playback

import android.os.Build
import androidx.media3.common.MimeTypes
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.jellyfin.JellyfinClient
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.MediaItem
import app.lumen.tv.data.session.SessionStore
import app.lumen.tv.data.settings.SettingsStore
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

data class ExternalSubtitle(val id: String, val url: String, val mimeType: String, val label: String?, val language: String?)

data class PlaybackSource(
    val item: MediaItem,
    val url: String,
    val isHls: Boolean,
    val method: PlayMethod,
    val mediaSourceId: String?,
    val playSessionId: String?,
    val subtitles: List<ExternalSubtitle>,
    val headers: Map<String, String>,
)

class PlaybackEvents {
    private val _stopped = MutableSharedFlow<UUID>(extraBufferCapacity = 4)
    val stopped: SharedFlow<UUID> = _stopped.asSharedFlow()

    fun emitStopped(itemId: UUID) {
        _stopped.tryEmit(itemId)
    }
}

class PlaybackRepository(
    private val client: JellyfinClient,
    private val library: JellyfinRepository,
    private val profiles: DeviceProfileFactory,
    private val sessions: SessionStore,
    private val events: PlaybackEvents,
    private val settings: SettingsStore,
) {
    private val userId: UUID get() = checkNotNull(client.session.value).userId

    suspend fun item(id: UUID): MediaItem = library.peek(id) ?: library.detail(id).item

    suspend fun resolve(item: MediaItem, startTicks: Long, forceTranscode: Boolean): PlaybackSource {
        val maxBitrate = settings.current().quality.bitrate
        val info = client.io {
            client.api.mediaInfoApi.getPostedPlaybackInfo(
                itemId = item.id,
                data = PlaybackInfoDto(
                    userId = userId,
                    maxStreamingBitrate = maxBitrate,
                    startTimeTicks = startTicks,
                    deviceProfile = profiles.build(maxBitrate),
                    enableDirectPlay = !forceTranscode,
                    enableDirectStream = !forceTranscode,
                    enableTranscoding = true,
                    allowVideoStreamCopy = !forceTranscode,
                    allowAudioStreamCopy = !forceTranscode,
                    autoOpenLiveStream = true,
                ),
            ).content
        }
        val source = info.mediaSources.firstOrNull() ?: error("No playable source")
        val base = client.api.baseUrl.orEmpty().trimEnd('/')

        val (url, method) = when {
            !forceTranscode && source.supportsDirectPlay -> staticUrl(item.id, source, info.playSessionId) to PlayMethod.DIRECT_PLAY
            !forceTranscode && source.supportsDirectStream && source.transcodingUrl == null ->
                staticUrl(item.id, source, info.playSessionId) to PlayMethod.DIRECT_STREAM
            source.transcodingUrl != null -> base + source.transcodingUrl to PlayMethod.TRANSCODE
            else -> error("Server offered no playable stream")
        }

        return PlaybackSource(
            item = item,
            url = url,
            isHls = method == PlayMethod.TRANSCODE,
            method = method,
            mediaSourceId = source.id,
            playSessionId = info.playSessionId,
            subtitles = externalSubtitles(source, base),
            headers = mapOf("Authorization" to authorizationHeader()),
        )
    }

    suspend fun nextEpisode(current: MediaItem): MediaItem? {
        val seriesId = current.seriesId ?: return null
        return runCatching {
            client.io {
                client.api.showApi.getEpisodes(
                    seriesId = seriesId,
                    userId = userId,
                    startItemId = current.id,
                    limit = 2,
                    fields = listOf(ItemFields.OVERVIEW),
                ).content.items
            }.getOrNull(1)?.let(library::toMediaItem)
        }.getOrNull()
    }

    suspend fun reportStart(source: PlaybackSource, positionTicks: Long, paused: Boolean) = report {
        client.api.sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = source.item.id,
                mediaSourceId = source.mediaSourceId,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = source.method,
                playSessionId = source.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportProgress(source: PlaybackSource, positionTicks: Long, paused: Boolean) = report {
        client.api.sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = source.item.id,
                mediaSourceId = source.mediaSourceId,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionTicks,
                playMethod = source.method,
                playSessionId = source.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportStopped(source: PlaybackSource, positionTicks: Long) {
        report {
            client.api.sessionApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = source.item.id,
                    mediaSourceId = source.mediaSourceId,
                    positionTicks = positionTicks,
                    playSessionId = source.playSessionId,
                    failed = false,
                ),
            )
        }
        events.emitStopped(source.item.id)
    }

    private suspend fun report(block: suspend () -> Unit) {
        runCatching { client.io { block() } }
    }

    private fun staticUrl(itemId: UUID, source: MediaSourceInfo, playSessionId: String?): String =
        client.api.videoApi.getVideoStreamUrl(
            itemId = itemId,
            container = source.container,
            static = true,
            mediaSourceId = source.id,
            playSessionId = playSessionId,
            tag = source.eTag,
        )

    private fun externalSubtitles(source: MediaSourceInfo, base: String): List<ExternalSubtitle> =
        source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL && it.deliveryUrl != null }
            .mapNotNull { stream ->
                val mime = when (stream.codec?.lowercase()) {
                    "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> null
                } ?: return@mapNotNull null
                val url = if (stream.isExternalUrl == true) stream.deliveryUrl!! else base + stream.deliveryUrl
                ExternalSubtitle("jf-${stream.index}", url, mime, stream.displayTitle ?: stream.title, stream.language)
            }

    private suspend fun authorizationHeader(): String {
        val session = checkNotNull(client.session.value)
        return AuthorizationHeaderBuilder.buildHeader(
            clientName = "Lumen",
            clientVersion = BuildConfig.VERSION_NAME,
            deviceId = sessions.deviceId(),
            deviceName = Build.MODEL,
            accessToken = session.accessToken,
        )
    }
}
