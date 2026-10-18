package app.lumen.tv.data.ratings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

data class ImdbScore(val rating: Double, val votes: Int)

class ImdbRatings(context: Context, private val enabled: Boolean) {
    private val index = File(File(context.filesDir, "imdb").apply { mkdirs() }, "ratings.bin")
    private val lock = Mutex()
    private val rebuilding = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun lookup(imdbId: String?): ImdbScore? {
        if (!enabled) return null
        val id = imdbId?.removePrefix("tt")?.toIntOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureIndex()
                search(id)
            }.getOrNull()
        }
    }

    suspend fun warmUp() {
        if (!enabled) return
        withContext(Dispatchers.IO) { runCatching { ensureIndex() } }
    }

    private suspend fun ensureIndex() {
        if (index.exists()) {
            val stale = Instant.ofEpochMilli(index.lastModified()).plus(MAX_AGE).isBefore(Instant.now())
            if (stale && rebuilding.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        lock.withLock { runCatching { build() } }
                    } finally {
                        rebuilding.set(false)
                    }
                }
            }
            return
        }
        lock.withLock { if (!index.exists()) build() }
    }

    private fun build() {
        val ids = IntArray(CAPACITY)
        val ratings = ByteArray(CAPACITY)
        val votes = IntArray(CAPACITY)
        var count = 0
        val connection = URL(SOURCE).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        try {
            GZIPInputStream(connection.inputStream).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val first = line.indexOf('\t')
                    val second = line.indexOf('\t', first + 1)
                    if (first < 0 || second < 0) return@forEach
                    val voteCount = line.substring(second + 1).toIntOrNull() ?: return@forEach
                    if (voteCount < MIN_VOTES || count >= CAPACITY) return@forEach
                    val id = line.substring(2, first).toIntOrNull() ?: return@forEach
                    val rating = line.substring(first + 1, second).toDoubleOrNull() ?: return@forEach
                    ids[count] = id
                    ratings[count] = (rating * 10).toInt().toByte()
                    votes[count] = voteCount
                    count++
                }
            }
        } finally {
            connection.disconnect()
        }
        val order = LongArray(count) { (ids[it].toLong() shl 32) or it.toLong() }.also { it.sort() }
        val temp = File(index.parentFile, "ratings.part")
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            order.forEach { packed ->
                val i = (packed and 0xFFFFFFFFL).toInt()
                out.writeInt(ids[i])
                out.writeByte(ratings[i].toInt())
                out.writeInt(votes[i])
            }
        }
        temp.renameTo(index)
    }

    private fun search(id: Int): ImdbScore? = RandomAccessFile(index, "r").use { file ->
        var low = 0L
        var high = file.length() / RECORD - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            file.seek(mid * RECORD)
            val current = file.readInt()
            when {
                current < id -> low = mid + 1
                current > id -> high = mid - 1
                else -> return@use ImdbScore(file.readUnsignedByte() / 10.0, file.readInt())
            }
        }
        null
    }

    private companion object {
        const val SOURCE = "https://datasets.imdbws.com/title.ratings.tsv.gz"
        const val MIN_VOTES = 20
        const val CAPACITY = 1_500_000
        const val RECORD = 9L
        val MAX_AGE: Duration = Duration.ofDays(7)
    }
}
