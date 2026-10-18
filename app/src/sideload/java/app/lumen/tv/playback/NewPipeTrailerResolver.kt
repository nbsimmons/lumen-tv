package app.lumen.tv.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

class NewPipeTrailerResolver : TrailerStreamResolver {
    override suspend fun resolve(youtubeKey: String): ResolvedTrailer? = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialised()
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$youtubeKey")
            val video = info.videoOnlyStreams
                .filter { it.isUrl && it.height in 1..MAX_HEIGHT && it.format == MediaFormat.MPEG_4 && it.codec.orEmpty().startsWith("avc1") }
                .maxByOrNull { it.height }
            val m4a = info.audioStreams.filter { it.isUrl && it.format == MediaFormat.M4A }
            val original = m4a.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
                .ifEmpty { m4a.filter { it.audioTrackType == null } }
                .ifEmpty { m4a }
            val audio = original.maxByOrNull { it.averageBitrate }
            if (video != null && audio != null) {
                ResolvedTrailer(video.content, audio.content)
            } else {
                info.videoStreams.filter { it.isUrl }.maxByOrNull { it.height }?.let { ResolvedTrailer(it.content, null) }
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_HEIGHT = 1080

        @Volatile
        private var initialised = false

        fun ensureInitialised() {
            if (initialised) return
            synchronized(this) {
                if (!initialised) {
                    NewPipe.init(OkHttpDownloader())
                    initialised = true
                }
            }
        }
    }
}

private class OkHttpDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)
            .header("User-Agent", USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == TOO_MANY_REQUESTS) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(response.code, response.message, response.headers.toMultimap(), response.body.string(), response.request.url.toString())
        }
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
