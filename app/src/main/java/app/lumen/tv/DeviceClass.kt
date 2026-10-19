package app.lumen.tv

import android.app.ActivityManager
import android.content.Context

object DeviceClass {
    private const val LOW_MEMORY_BYTES = 1_600L * 1024 * 1024

    fun isLowMemory(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (manager.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return info.totalMem in 1 until LOW_MEMORY_BYTES
    }
}
