package app.lumen.tv.data.collections

import android.content.Context
import app.lumen.tv.data.jellyfin.JellyfinRepository
import app.lumen.tv.data.model.CollectionRef
import app.lumen.tv.data.seerr.SeerrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class LibraryCollection(val ref: CollectionRef, val owned: Int, val watched: Int = 0)

@Serializable
private data class IndexFile(val entries: Map<Int, CollectionRef?> = emptyMap())

class CollectionIndex(
    context: Context,
    private val jellyfin: JellyfinRepository,
    private val seerr: SeerrRepository,
) {
    private val file = File(context.filesDir, "collections.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun libraryCollections(): List<LibraryCollection> = lock.withLock {
        val library = jellyfin.allMovies().filter { it.tmdbId != null }.distinctBy { it.tmdbId }
        val movies = library.mapNotNull { it.tmdbId }
        val played = library.filter { it.played }.mapNotNull { it.tmdbId }.toSet()
        val known = read().toMutableMap()
        val missing = movies.filterNot { it in known }
        if (missing.isNotEmpty()) {
            val gate = Semaphore(PARALLEL_LOOKUPS)
            val found = coroutineScope {
                missing.map { id ->
                    async { gate.withPermit { id to runCatching { seerr.collectionOf(id) }.getOrElse { return@withPermit null } } }
                }.awaitAll()
            }
            found.filterNotNull().forEach { (id, ref) -> known[id] = ref }
            write(known)
        }
        movies.mapNotNull { id -> known[id]?.let { it to (id in played) } }
            .groupBy { it.first.tmdbId }
            .map { (_, refs) -> LibraryCollection(refs.first().first, refs.size, refs.count { it.second }) }
            .sortedBy { it.ref.name.lowercase() }
    }

    private suspend fun read(): Map<Int, CollectionRef?> = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<IndexFile>(file.readText()).entries }.getOrDefault(emptyMap())
    }

    private suspend fun write(entries: Map<Int, CollectionRef?>) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(file.parentFile, "collections.part")
            temp.writeText(json.encodeToString(IndexFile.serializer(), IndexFile(entries)))
            temp.renameTo(file)
        }
    }

    private companion object {
        const val PARALLEL_LOOKUPS = 6
    }
}
