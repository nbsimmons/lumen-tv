package app.lumen.tv.data.livetv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object GuideParser {
    private val TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    class Result(val names: Map<String, String>, val icons: Map<String, String>, val programmes: Map<String, List<Programme>>)

    fun parse(input: InputStream, from: Instant, until: Instant): Result {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val names = HashMap<String, String>()
        val icons = HashMap<String, String>()
        val programmes = HashMap<String, MutableList<Programme>>()

        var channelId: String? = null
        var programmeChannel: String? = null
        var start: Instant? = null
        var stop: Instant? = null
        var title: String? = null
        var subtitle: String? = null
        var description: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> channelId = parser.getAttributeValue(null, "id")
                    "icon" -> {
                        val id = channelId
                        val src = parser.getAttributeValue(null, "src")
                        if (id != null && id !in icons && src != null && src.startsWith("http")) icons[id] = src
                    }
                    "display-name" -> if (channelId != null && channelId !in names) names[channelId] = parser.nextText().trim()
                    "programme" -> {
                        programmeChannel = parser.getAttributeValue(null, "channel")
                        start = parser.getAttributeValue(null, "start")?.let(::time)
                        stop = parser.getAttributeValue(null, "stop")?.let(::time)
                        title = null
                        subtitle = null
                        description = null
                    }
                    "title" -> if (programmeChannel != null && title == null) title = parser.nextText().trim()
                    "sub-title" -> if (programmeChannel != null && subtitle == null) subtitle = parser.nextText().trim()
                    "desc" -> if (programmeChannel != null && description == null) description = parser.nextText().trim()
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "channel" -> channelId = null
                    "programme" -> {
                        val begins = start
                        val ends = stop
                        val heading = title
                        val owner = programmeChannel
                        if (owner != null && begins != null && ends != null && heading != null && ends.isAfter(from) && begins.isBefore(until)) {
                            programmes.getOrPut(owner) { mutableListOf() } += Programme(heading, subtitle, description, begins, ends)
                        }
                        programmeChannel = null
                    }
                }
            }
            event = parser.next()
        }
        programmes.values.forEach { list -> list.sortBy { it.start } }
        return Result(names, icons, programmes)
    }

    private fun time(value: String): Instant? = runCatching { OffsetDateTime.parse(value.trim(), TIME).toInstant() }.getOrNull()
}
