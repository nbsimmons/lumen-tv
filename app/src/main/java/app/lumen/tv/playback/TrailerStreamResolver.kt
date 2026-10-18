package app.lumen.tv.playback

data class ResolvedTrailer(val videoUrl: String, val audioUrl: String?)

interface TrailerStreamResolver {
    suspend fun resolve(youtubeKey: String): ResolvedTrailer?
}
