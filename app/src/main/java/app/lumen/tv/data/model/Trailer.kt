package app.lumen.tv.data.model

import java.util.UUID

sealed interface Trailer {
    data class Local(val id: UUID) : Trailer
    data class YouTube(val key: String) : Trailer

    companion object {
        private val YOUTUBE_ID = Regex("(?:youtube\\.com/(?:watch\\?v=|embed/)|youtu\\.be/)([A-Za-z0-9_-]{11})")

        fun fromUrl(url: String?): YouTube? = url?.let { YOUTUBE_ID.find(it)?.groupValues?.get(1) }?.let(::YouTube)
    }
}
