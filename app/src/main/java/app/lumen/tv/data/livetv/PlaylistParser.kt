package app.lumen.tv.data.livetv

import java.security.MessageDigest

object PlaylistParser {
    private val ATTRIBUTE = Regex("""([\w-]+)="([^"]*)"""")
    private val BLOCKED_LOGO_HOST = Regex("^https?://([a-z0-9-]+\\.)*imgur\\.com/", RegexOption.IGNORE_CASE)
    private val RADIO_NAME = Regex("\\bradio\\b|asian network|world service|6 music|1xtra", RegexOption.IGNORE_CASE)
    private val RADIO_URL = Regex("radio|/audio/|\\.(mp3|aac)(\\?|$)", RegexOption.IGNORE_CASE)
    private val MARKERS = Regex("""\s*[ⒼⓈⓎⓋ]""")

    fun parse(text: String): Playlist {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val header = lines.firstOrNull { it.startsWith("#EXTM3U") }.orEmpty()
        val guideUrls = ATTRIBUTE.findAll(header)
            .filter { it.groupValues[1] == "x-tvg-url" || it.groupValues[1] == "url-tvg" }
            .flatMap { match -> match.groupValues[2].split(',').map { it.trim() } }
            .filter { it.startsWith("http") }
            .toList()

        val channels = mutableListOf<Channel>()
        var pending: Map<String, String>? = null
        var pendingName = ""
        for (line in lines) {
            when {
                line.startsWith("#EXTINF") -> {
                    val info = line.substringBefore(',', line)
                    pending = ATTRIBUTE.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingName = line.substringAfter(',', "").replace(MARKERS, "").trim()
                }
                line.startsWith("#") -> Unit
                pending != null -> {
                    val attrs = pending
                    val name = pendingName.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { line }
                    channels += Channel(
                        key = keyFor(line),
                        number = attrs["tvg-chno"]?.toIntOrNull() ?: 0,
                        name = name,
                        logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") && !BLOCKED_LOGO_HOST.containsMatchIn(it) },
                        group = attrs["group-title"]?.ifBlank { null } ?: UNGROUPED,
                        country = attrs["tvg-country"]?.ifBlank { null },
                        tvgId = attrs["tvg-id"]?.ifBlank { null },
                        url = line,
                        isRadio = attrs["radio"].equals("true", ignoreCase = true) || RADIO_NAME.containsMatchIn(name) || RADIO_URL.containsMatchIn(line),
                    )
                    pending = null
                }
            }
        }
        val numbered = channels.groupBy { it.group }.flatMap { (_, group) ->
            group.mapIndexed { index, channel -> if (channel.number > 0) channel else channel.copy(number = index + 1) }
        }
        return Playlist(numbered.distinctBy { it.key }, guideUrls)
    }

    private fun keyFor(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    const val UNGROUPED = "Channels"
}
