package app.lumen.tv.playback

import android.os.Build
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

data class DeviceCapabilities(
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
    val videoRangeTypes: List<String>,
    val maxAudioChannels: Int,
)

@OptIn(UnstableApi::class)
class DeviceProfileFactory(private val context: Context) {
    val capabilities: DeviceCapabilities by lazy { detect() }

    fun build(maxBitrate: Int): DeviceProfile {
        val caps = capabilities
        val video = caps.videoCodecs.joinToString(",")
        val audio = caps.audioCodecs.joinToString(",")
        val transcodeVideo = if ("hevc" in caps.videoCodecs) "hevc,h264" else "h264"
        val transcodeAudio = listOf("aac", "ac3", "eac3").filter { it == "aac" || it in caps.audioCodecs }.joinToString(",")

        return DeviceProfile(
            name = "Lumen Fire TV",
            maxStreamingBitrate = maxBitrate,
            maxStaticBitrate = maxBitrate,
            directPlayProfiles = DIRECT_PLAY_CONTAINERS.map { container ->
                DirectPlayProfile(container = container, videoCodec = video, audioCodec = audio, type = DlnaProfileType.VIDEO)
            } + DirectPlayProfile(container = "mp3,aac,flac,m4a,ogg,opus,wav", type = DlnaProfileType.AUDIO),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = transcodeVideo,
                    audioCodec = transcodeAudio,
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    maxAudioChannels = caps.maxAudioChannels.coerceAtMost(6).toString(),
                    minSegments = 1,
                    breakOnNonKeyFrames = true,
                    conditions = emptyList(),
                ),
            ),
            containerProfiles = emptyList(),
            codecProfiles = listOf(
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "h264",
                    conditions = listOf(
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, "8"),
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, "3840"),
                    ),
                    applyConditions = emptyList(),
                ),
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = "hevc",
                    conditions = listOf(
                        condition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_RANGE_TYPE, caps.videoRangeTypes.joinToString("|")),
                    ),
                    applyConditions = emptyList(),
                ),
                CodecProfile(
                    type = CodecType.VIDEO_AUDIO,
                    conditions = listOf(
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.AUDIO_CHANNELS, caps.maxAudioChannels.toString()),
                    ),
                    applyConditions = emptyList(),
                ),
            ),
            subtitleProfiles = EMBEDDED_SUBTITLES.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED) } +
                EXTERNAL_SUBTITLES.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL) } +
                IMAGE_SUBTITLES.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.ENCODE) },
        )
    }

    private fun detect(): DeviceCapabilities {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.toList() }
            .map { it.lowercase() }
            .toSet()

        val video = buildList {
            add("h264")
            if ("video/hevc" in decoders) add("hevc")
            if ("video/x-vnd.on2.vp9" in decoders) add("vp9")
            if ("video/av01" in decoders) add("av1")
            if ("video/mpeg2" in decoders) add("mpeg2video")
        }

        val sink = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
        val audio = buildList {
            addAll(listOf("aac", "mp3", "mp2", "opus", "vorbis", "flac", "alac", "pcm_s16le", "pcm_s24le"))
            if ("audio/ac3" in decoders || sink.supportsEncoding(C.ENCODING_AC3)) add("ac3")
            if ("audio/eac3" in decoders || sink.supportsEncoding(C.ENCODING_E_AC3)) add("eac3")
            if (sink.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)) add("truehd")
            if ("audio/vnd.dts" in decoders || sink.supportsEncoding(C.ENCODING_DTS)) {
                add("dts")
                add("dca")
            }
        }

        @Suppress("DEPRECATION")
        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.hdrCapabilities
                ?.supportedHdrTypes
                ?.toSet()
                .orEmpty()
        } else {
            emptySet()
        }
        val dolbyVisionDecoder = decoders.any { it.startsWith("video/dolby-vision") }
        val ranges = buildList {
            add("SDR")
            if (Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes) {
                add("HDR10")
                add("DOVIWithHDR10")
            }
            if (Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdrTypes) add("HDR10Plus")
            if (Display.HdrCapabilities.HDR_TYPE_HLG in hdrTypes) {
                add("HLG")
                add("DOVIWithHLG")
            }
            if (dolbyVisionDecoder && Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes) {
                add("DOVI")
                add("DOVIWithSDR")
            }
        }

        return DeviceCapabilities(
            videoCodecs = video,
            audioCodecs = audio,
            videoRangeTypes = ranges,
            maxAudioChannels = sink.maxChannelCount.coerceAtLeast(2),
        )
    }

    private fun condition(type: ProfileConditionType, property: ProfileConditionValue, value: String) =
        ProfileCondition(condition = type, property = property, value = value, isRequired = false)

    private companion object {
        val DIRECT_PLAY_CONTAINERS = listOf("mp4,m4v,mov", "mkv,webm", "ts,mpegts,m2ts")
        val EMBEDDED_SUBTITLES = listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "pgssub", "pgs", "dvdsub", "dvbsub", "mov_text", "ttml")
        val EXTERNAL_SUBTITLES = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa")
        val IMAGE_SUBTITLES = listOf("pgssub", "pgs", "dvdsub", "dvbsub")
    }
}
