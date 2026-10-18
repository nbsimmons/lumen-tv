package app.lumen.tv.data.update

import java.io.File

interface UpdateInstaller {
    fun canInstall(): Boolean
    fun openPermissionSettings()
    suspend fun install(apk: File, profile: File?)
}
