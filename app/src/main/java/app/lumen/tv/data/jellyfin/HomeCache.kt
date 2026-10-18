package app.lumen.tv.data.jellyfin

import android.content.Context
import app.lumen.tv.data.model.LibraryView
import app.lumen.tv.data.model.Shelf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class HomeSnapshot(val views: List<LibraryView> = emptyList(), val shelves: List<Shelf> = emptyList())

class HomeCache(context: Context) {
    private val dir = File(context.filesDir, "home")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun read(user: UUID): HomeSnapshot? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<HomeSnapshot>(file(user).readText()) }.getOrNull()
    }

    suspend fun update(user: UUID, transform: (HomeSnapshot) -> HomeSnapshot) = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val next = transform(read(user) ?: HomeSnapshot())
                dir.mkdirs()
                val temp = File(dir, "$user.part")
                temp.writeText(json.encodeToString(HomeSnapshot.serializer(), next))
                temp.renameTo(file(user))
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock { dir.deleteRecursively() } }

    private fun file(user: UUID) = File(dir, "$user.json")
}
