package app.lumen.tv.data.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.Jellyfin
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class DiscoveredServer(val id: String, val name: String, val url: String)

class LocalDiscovery(private val jellyfin: Jellyfin) {
    private val json = Json { ignoreUnknownKeys = true }

    fun jellyfinServers(): Flow<DiscoveredServer> = channelFlow {
        val seen = ConcurrentHashMap.newKeySet<String>()
        suspend fun offer(server: DiscoveredServer?) {
            if (server != null && seen.add(server.id)) send(server)
        }

        launch {
            jellyfin.discovery.discoverLocalServers(timeout = BROADCAST_TIMEOUT_MS)
                .catch { }
                .collect { info ->
                    val candidates = listOfNotNull(
                        info.endpointAddress?.let { "http://$it:$JELLYFIN_PORT" },
                        info.address.takeIf { it.startsWith("http") },
                        "http://${info.address}:$JELLYFIN_PORT".takeUnless { info.address.startsWith("http") },
                    )
                    offer(candidates.firstNotNullOfOrNull(::probeJellyfin))
                }
        }
        launch {
            scanSubnet { host -> offer(probeJellyfin("http://$host:$JELLYFIN_PORT")) }
        }
    }.flowOn(Dispatchers.IO)

    fun seerrServers(hintHosts: List<String>): Flow<DiscoveredServer> = channelFlow {
        val seen = ConcurrentHashMap.newKeySet<String>()
        suspend fun offer(server: DiscoveredServer?) {
            if (server != null && seen.add(server.url)) send(server)
        }

        hintHosts.distinct().forEach { host -> offer(probeSeerr("http://$host:$SEERR_PORT")) }
        scanSubnet { host -> if (host !in hintHosts) offer(probeSeerr("http://$host:$SEERR_PORT")) }
    }.flowOn(Dispatchers.IO)

    private fun probeJellyfin(url: String): DiscoveredServer? {
        val info = getJson("$url/System/Info/Public") ?: return null
        val id = info.string("Id") ?: return null
        if (info.string("ProductName")?.contains("Jellyfin", ignoreCase = true) != true) return null
        return DiscoveredServer(id = id, name = info.string("ServerName")?.trim().orEmpty().ifEmpty { url }, url = url)
    }

    private fun probeSeerr(url: String): DiscoveredServer? {
        val status = getJson("$url/api/v1/status") ?: return null
        if (status.string("version") == null || !status.containsKey("commitTag")) return null
        val title = getJson("$url/api/v1/settings/public")?.string("applicationTitle")
        return DiscoveredServer(id = url, name = title?.ifBlank { null } ?: DEFAULT_SEERR_NAME, url = url)
    }

    private suspend fun scanSubnet(probe: suspend (String) -> Unit) = coroutineScope {
        val gate = Semaphore(SCAN_CONCURRENCY)
        localSubnetHosts().map { host -> async { gate.withPermit { probe(host) } } }.awaitAll()
    }

    private fun localSubnetHosts(): List<String> {
        val own = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
        }.getOrDefault(emptyList())
        return own.flatMap { address ->
            val octets = address.hostAddress?.split(".") ?: return@flatMap emptyList()
            val prefix = octets.take(3).joinToString(".")
            (1..254).map { "$prefix.$it" }.filter { it != address.hostAddress }
        }.distinct()
    }

    private fun getJson(url: String): JsonObject? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val JELLYFIN_PORT = 8096
        const val SEERR_PORT = 5055
        const val BROADCAST_TIMEOUT_MS = 1_500
        const val CONNECT_TIMEOUT_MS = 400
        const val READ_TIMEOUT_MS = 1_500
        const val SCAN_CONCURRENCY = 48
        const val DEFAULT_SEERR_NAME = "Seerr"
    }
}
