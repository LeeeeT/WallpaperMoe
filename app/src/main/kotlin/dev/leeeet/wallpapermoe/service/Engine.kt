package dev.leeeet.wallpapermoe.service

import android.content.Context
import android.content.Intent
import dev.leeeet.wallpapermoe.data.MoeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Starting and stopping [RotationService]. */
object Engine {

    fun applySettings(context: Context, settings: MoeSettings) {
        if (settings.enabled) ensure(context) else stop(context)
    }

    /**
     * Brings the engine up, or confirms it already is. Returns whether it is up.
     *
     * A background start can be refused outright when the app is off the Doze
     * allowlist, so this is called from every point the framework does permit
     * one: [dev.leeeet.wallpapermoe.MainActivity.onStart] and `BOOT_COMPLETED`.
     */
    fun ensure(context: Context): Boolean {
        if (RotationService.isRunning) {
            _blocked.value = false
            return true
        }
        return try {
            context.startForegroundService(Intent(context, RotationService::class.java))
            _blocked.value = false
            true
        } catch (_: Exception) {
            _blocked.value = true
            false
        }
    }

    /** Asks the running engine to rotate immediately, regardless of screen or interval. */
    fun rotateNow(context: Context) {
        val intent = Intent(context, RotationService::class.java).setAction(RotationService.ACTION_ROTATE_NOW)
        runCatching { context.startForegroundService(intent) }
    }

    private fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, RotationService::class.java)) }
        _blocked.value = false
    }

    private val _blocked = MutableStateFlow(false)

    /** True while the engine is wanted but the framework has refused to start it. */
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()
}
