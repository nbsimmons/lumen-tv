package app.lumen.tv.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import app.lumen.tv.BuildConfig
import app.lumen.tv.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.time.Duration

data class Release(val version: String, val notes: String?, val url: String, val size: Long, val profileUrl: String? = null)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class NeedsPermission(val release: Release) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val release: Release?) : UpdateState
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

object UpdateEvents {
    val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

class UpdateRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val installer: UpdateInstaller?,
) {
    val enabled: Boolean = installer != null && BuildConfig.UPDATE_REPO.isNotBlank()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var download: Job? = null

    init {
        scope.launch { UpdateEvents.failures.collect { _state.value = UpdateState.Failed(currentRelease()) } }
    }

    fun checkInBackground() {
        if (!enabled) return
        scope.launch {
            if (!settings.updateCheckDue(CHECK_INTERVAL)) return@launch
            val release = check() ?: return@launch
            if (release.version != settings.dismissedUpdate()) _prompt.value = true
        }
    }

    fun checkNow() {
        if (!enabled || _state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        scope.launch {
            if (check() != null) _prompt.value = true
        }
    }

    fun later() {
        _prompt.value = false
        val release = currentRelease() ?: return
        if (_state.value is UpdateState.Available) scope.launch { settings.setDismissedUpdate(release.version) }
    }

    fun dismiss() {
        _prompt.value = false
        when (val current = _state.value) {
            is UpdateState.Downloading -> {
                download?.cancel()
                _state.value = UpdateState.Available(current.release)
            }
            is UpdateState.Failed, is UpdateState.NeedsPermission -> _state.value = current.releaseOrNull()?.let(UpdateState::Available) ?: UpdateState.Idle
            else -> Unit
        }
    }

    fun install() {
        val release = currentRelease() ?: return
        val installer = installer ?: return
        _prompt.value = true
        if (!installer.canInstall()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        download?.cancel()
        download = scope.launch {
            runCatching {
                val apk = fetch(release)
                check(isTrusted(apk)) { "Signature mismatch" }
                val profile = release.profileUrl?.let { url -> runCatching { fetchProfile(url, apk.parentFile!!) }.getOrNull() }
                _state.value = UpdateState.Installing(release)
                installer.install(apk, profile)
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) _state.value = UpdateState.Failed(release) }
        }
    }

    fun openPermissionSettings() {
        installer?.openPermissionSettings()
    }

    private suspend fun check(): Release? {
        _state.value = UpdateState.Checking
        val release = runCatching { latest() }.getOrElse {
            _state.value = UpdateState.Failed(null)
            return null
        }
        settings.markUpdateChecked()
        if (release == null || compare(release.version, BuildConfig.VERSION_NAME) <= 0) {
            _state.value = UpdateState.UpToDate
            return null
        }
        _state.value = UpdateState.Available(release)
        return release
    }

    private suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lumen/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            check(response.isSuccessful) { "GitHub returned ${response.code}" }
            val body = json.decodeFromString<GitHubRelease>(response.body.string())
            val asset = body.assets.firstOrNull { it.name.endsWith(APK_SUFFIX) } ?: return@withContext null
            val profile = profileSuffix()?.let { suffix -> body.assets.firstOrNull { it.name.endsWith(suffix) } }
            Release(body.tag.removePrefix("v"), body.body?.let(::plainNotes)?.takeIf { it.isNotBlank() }, asset.url, asset.size, profile?.url)
        }
    }

    private suspend fun fetch(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val target = File(dir, "lumen-${release.version}.apk")
        _state.value = UpdateState.Downloading(release, 0f)
        http.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download returned ${response.code}" }
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.size
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    var lastReported = 0f
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val progress = if (total > 0) copied.toFloat() / total else 0f
                        if (progress - lastReported >= 0.01f) {
                            lastReported = progress
                            _state.value = UpdateState.Downloading(release, progress)
                        }
                    }
                }
            }
        }
        target
    }

    private suspend fun fetchProfile(url: String, dir: File): File = withContext(Dispatchers.IO) {
        val target = File(dir, "lumen.dm")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Profile returned ${response.code}" }
            response.body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        target
    }

    private fun profileSuffix(): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> "-sideload-api31.dm"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> "-sideload-api28.dm"
        else -> null
    }

    private fun isTrusted(apk: File): Boolean {
        val manager = context.packageManager
        val archive = manager.getPackageArchiveInfo(apk.path, SIGNATURE_FLAGS) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = manager.getPackageInfo(context.packageName, SIGNATURE_FLAGS)
        val expected = fingerprints(installed)
        return expected.isNotEmpty() && fingerprints(archive) == expected
    }

    @Suppress("DEPRECATION")
    private fun fingerprints(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { signature -> digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    private fun currentRelease(): Release? = _state.value.releaseOrNull()

    private fun UpdateState.releaseOrNull(): Release? = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        else -> null
    }

    private companion object {
        const val APK_SUFFIX = "-sideload.apk"
        const val BUFFER = 64 * 1024
        val CHECK_INTERVAL: Duration = Duration.ofHours(6)

        @Suppress("DEPRECATION")
        val SIGNATURE_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

        fun plainNotes(markdown: String): String = markdown.lineSequence()
            .filterNot { it.trimStart().startsWith("SHA-256") }
            .map { line ->
                line.trimEnd()
                    .replace(Regex("^#+\\s*"), "")
                    .replace(Regex("^\\s*[-*]\\s+"), "• ")
                    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                    .replace("**", "")
                    .replace("`", "")
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        fun compare(a: String, b: String): Int {
            val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(left.size, right.size)) {
                val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
