package app.lumen.tv.data.livetv

import java.time.Instant

data class Channel(
    val key: String,
    val number: Int,
    val name: String,
    val logo: String?,
    val group: String,
    val country: String?,
    val tvgId: String?,
    val url: String,
    val isRadio: Boolean = false,
)

data class Programme(
    val title: String,
    val subtitle: String?,
    val description: String?,
    val start: Instant,
    val stop: Instant,
) {
    fun isOn(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(stop)

    fun progress(now: Instant): Float {
        val total = stop.toEpochMilli() - start.toEpochMilli()
        if (total <= 0) return 0f
        return ((now.toEpochMilli() - start.toEpochMilli()).toFloat() / total).coerceIn(0f, 1f)
    }
}

data class Guide(val programmes: Map<String, List<Programme>>, val logos: Map<String, String>)

data class Playlist(val channels: List<Channel>, val guideUrls: List<String>)
