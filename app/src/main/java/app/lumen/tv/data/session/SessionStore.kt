package app.lumen.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.UUID

data class JellyfinSession(
    val serverUrl: String,
    val serverName: String,
    val userId: UUID,
    val userName: String,
    val accessToken: String,
)

data class SeerrSession(val url: String, val cookie: String)

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class SessionStore(context: Context, private val cipher: SecureCipher) {
    private val store = context.sessionDataStore

    suspend fun deviceId(): String {
        store.data.first()[DEVICE_ID]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit { it[DEVICE_ID] = id }
        return id
    }

    suspend fun lastServerUrl(): String? = store.data.first()[SERVER_URL]

    suspend fun saveServerUrl(url: String) {
        store.edit { it[SERVER_URL] = url }
    }

    suspend fun jellyfin(): JellyfinSession? {
        val prefs = store.data.first()
        val token = prefs[ACCESS_TOKEN]?.let(cipher::decrypt) ?: return null
        val userId = prefs[USER_ID]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return JellyfinSession(
            serverUrl = prefs[SERVER_URL] ?: return null,
            serverName = prefs[SERVER_NAME].orEmpty(),
            userId = userId,
            userName = prefs[USER_NAME].orEmpty(),
            accessToken = token,
        )
    }

    suspend fun saveJellyfin(session: JellyfinSession) {
        store.edit {
            it[SERVER_URL] = session.serverUrl
            it[SERVER_NAME] = session.serverName
            it[USER_ID] = session.userId.toString()
            it[USER_NAME] = session.userName
            it[ACCESS_TOKEN] = cipher.encrypt(session.accessToken)
        }
    }

    suspend fun seerr(): SeerrSession? {
        val prefs = store.data.first()
        val url = prefs[SEERR_URL] ?: return null
        val cookie = prefs[SEERR_COOKIE]?.let(cipher::decrypt) ?: return null
        return SeerrSession(url, cookie)
    }

    suspend fun lastSeerrUrl(): String? = store.data.first()[SEERR_URL]

    suspend fun saveSeerr(session: SeerrSession) {
        store.edit {
            it[SEERR_URL] = session.url
            it[SEERR_COOKIE] = cipher.encrypt(session.cookie)
        }
    }

    suspend fun clearSeerr() {
        store.edit { it.remove(SEERR_COOKIE) }
    }

    suspend fun signOut() {
        store.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(USER_ID)
            it.remove(USER_NAME)
            it.remove(SEERR_COOKIE)
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val SEERR_URL = stringPreferencesKey("seerr_url")
        val SEERR_COOKIE = stringPreferencesKey("seerr_cookie")
    }
}
