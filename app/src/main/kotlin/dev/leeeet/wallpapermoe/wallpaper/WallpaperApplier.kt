package dev.leeeet.wallpapermoe.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ApplyTargets(val lock: Boolean, val home: Boolean) {
    val any: Boolean get() = lock || home
}

sealed interface ApplyResult {
    data object Success : ApplyResult

    /** The screen came back before the change was applied. */
    data object Skipped : ApplyResult
    data class Failed(val reason: ApplyFailure) : ApplyResult
}

enum class ApplyFailure { NO_TARGET, IMAGE_MISSING, SYSTEM_REJECTED }

/** Streams already-rendered files into the system wallpaper slots. */
class WallpaperApplier(private val context: Context) {

    private val wm: WallpaperManager get() = WallpaperManager.getInstance(context)
    private val power: PowerManager get() = context.getSystemService(PowerManager::class.java)

    fun targetSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * [onlyWhileScreenOff] is checked immediately before the call that makes
     * the change visible — the last instant at which it can still be abandoned.
     */
    suspend fun apply(
        prepared: File,
        targets: ApplyTargets,
        onlyWhileScreenOff: Boolean,
    ): ApplyResult = withContext(Dispatchers.IO) {
        if (!targets.any) return@withContext ApplyResult.Failed(ApplyFailure.NO_TARGET)
        if (!prepared.exists() || prepared.length() == 0L) {
            return@withContext ApplyResult.Failed(ApplyFailure.IMAGE_MISSING)
        }

        var which = 0
        if (targets.lock) which = which or WallpaperManager.FLAG_LOCK
        if (targets.home) which = which or WallpaperManager.FLAG_SYSTEM

        try {
            if (onlyWhileScreenOff && power.isInteractive) return@withContext ApplyResult.Skipped
            prepared.inputStream().use { wm.setStream(it, null, false, which) }
            ApplyResult.Success
        } catch (_: Exception) {
            ApplyResult.Failed(ApplyFailure.SYSTEM_REJECTED)
        }
    }
}
