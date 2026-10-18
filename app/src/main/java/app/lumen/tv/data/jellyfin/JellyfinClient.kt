package app.lumen.tv.data.jellyfin

import android.content.Context
import android.os.Build
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.session.JellyfinSession
import app.lumen.tv.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticateWithQuickConnect
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.PublicSystemInfo

data class ServerInfo(val url: String, val name: String, val version: String)

data class QuickConnectCode(val code: String, val secret: String)

class JellyfinClient(
    context: Context,
    private val sessions: SessionStore,
    private val scope: CoroutineScope,
    private val homeCache: HomeCache,
) {
    val sdk = createJellyfin {
        clientInfo = ClientInfo(name = "Lumen", version = BuildConfig.VERSION_NAME)
        this.context = context.applicationContext
    }

    @Volatile
    private var _api: ApiClient? = null
    val api: ApiClient get() = checkNotNull(_api) { "Not connected" }

    private val _session = MutableStateFlow<JellyfinSession?>(null)
    val session: StateFlow<JellyfinSession?> = _session.asStateFlow()

    private var pendingServer: ServerInfo? = null

    suspend fun restore(): Boolean = withContext(Dispatchers.IO) {
        val saved = sessions.jellyfin() ?: return@withContext false
        _api = newApi(saved.serverUrl, saved.accessToken)
        _session.value = saved
        scope.launch { validate(saved) }
        true
    }

    private suspend fun validate(saved: JellyfinSession) {
        val rejected = io { runCatching { api.userApi.getCurrentUser() } }
            .exceptionOrNull()
            .let { it is InvalidStatusException && it.status == 401 }
        if (rejected && _session.value == saved) {
            sessions.signOut()
            homeCache.clear()
            _session.value = null
            _api = null
        }
    }

    suspend fun connect(rawUrl: String): ServerInfo {
        var lastError: Throwable? = null
        for (candidate in candidateUrls(rawUrl)) {
            val api = newApi(candidate, null)
            val info: PublicSystemInfo = runCatching { io { api.systemApi.getPublicSystemInfo().content } }
                .onFailure { lastError = it }
                .getOrNull() ?: continue
            _api = api
            sessions.saveServerUrl(candidate)
            return ServerInfo(candidate, info.serverName?.trim().orEmpty(), info.version.orEmpty()).also { pendingServer = it }
        }
        throw lastError ?: IllegalArgumentException("Server not reachable")
    }

    suspend fun isQuickConnectEnabled(): Boolean =
        runCatching { io { api.authenticationApi.getQuickConnectEnabled().content } }.getOrDefault(false)

    suspend fun login(username: String, password: String) {
        complete(io { api.authenticationApi.authenticateUserByName(username, password).content })
    }

    suspend fun startQuickConnect(): QuickConnectCode {
        val result = io { api.authenticationApi.initiateQuickConnect().content }
        return QuickConnectCode(result.code, result.secret)
    }

    suspend fun isQuickConnectApproved(secret: String): Boolean =
        io { api.authenticationApi.getQuickConnectState(secret).content.authenticated }

    suspend fun finishQuickConnect(secret: String) {
        complete(io { api.authenticationApi.authenticateWithQuickConnect(secret).content })
    }

    suspend fun authorizeQuickConnect(code: String): Boolean = runCatching {
        io { api.authenticationApi.authorizeQuickConnect(code = code, userId = session.value?.userId).content }
    }.getOrDefault(false)

    suspend fun signOut() {
        runCatching { io { api.sessionApi.reportSessionEnded() } }
        sessions.signOut()
        homeCache.clear()
        _session.value = null
        _api = null
    }

    private suspend fun complete(result: AuthenticationResult) {
        val server = checkNotNull(pendingServer)
        val token = checkNotNull(result.accessToken)
        val user = checkNotNull(result.user)
        api.update(accessToken = token)
        val session = JellyfinSession(server.url, server.name, user.id, user.name.orEmpty(), token)
        sessions.saveJellyfin(session)
        _session.value = session
    }

    suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private suspend fun newApi(baseUrl: String, token: String?): ApiClient =
        sdk.createApi(
            baseUrl = baseUrl,
            accessToken = token,
            deviceInfo = DeviceInfo(id = sessions.deviceId(), name = Build.MODEL),
        )

    private fun candidateUrls(raw: String): List<String> {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return listOf(trimmed)
        val hasPort = Regex(":\\d+$").containsMatchIn(trimmed)
        return buildList {
            add("http://$trimmed" + if (hasPort) "" else ":8096")
            add("https://$trimmed")
            if (!hasPort) add("http://$trimmed")
        }
    }
}
