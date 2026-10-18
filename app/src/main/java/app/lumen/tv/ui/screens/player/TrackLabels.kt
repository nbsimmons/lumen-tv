package app.lumen.tv.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import app.lumen.tv.R
import java.util.Locale

@Composable
fun audioLabel(format: Format): String {
    val codec = when (format.sampleMimeType) {
        MimeTypes.AUDIO_AC3 -> stringResource(R.string.codec_dd)
        MimeTypes.AUDIO_E_AC3 -> stringResource(R.string.codec_ddp)
        MimeTypes.AUDIO_E_AC3_JOC -> stringResource(R.string.codec_atmos)
        MimeTypes.AUDIO_TRUEHD -> stringResource(R.string.codec_truehd)
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> stringResource(R.string.codec_dts)
        MimeTypes.AUDIO_AAC -> stringResource(R.string.codec_aac)
        else -> null
    }
    val channels = when (format.channelCount) {
        1 -> stringResource(R.string.channels_mono)
        2 -> stringResource(R.string.channels_stereo)
        6 -> stringResource(R.string.channels_51)
        8 -> stringResource(R.string.channels_71)
        Format.NO_VALUE -> null
        else -> stringResource(R.string.channels_count, format.channelCount)
    }
    return listOfNotNull(languageName(format), codec, channels).joinToString(" · ")
}

@Composable
fun subtitleLabel(format: Format): String {
    val forced = if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) stringResource(R.string.subtitle_forced) else null
    return listOfNotNull(languageName(format), format.label?.takeIf { it != languageName(format) }, forced).joinToString(" · ")
}

@Composable
private fun languageName(format: Format): String {
    val locale = LocalConfiguration.current.locales[0]
    val tag = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
    return tag?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale).ifBlank { null } }
        ?: format.label
        ?: stringResource(R.string.track_unknown)
}
