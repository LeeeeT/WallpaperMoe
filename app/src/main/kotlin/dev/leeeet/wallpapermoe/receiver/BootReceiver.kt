package dev.leeeet.wallpapermoe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.leeeet.wallpapermoe.Graph
import dev.leeeet.wallpapermoe.service.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Brings the engine back after a reboot. `BOOT_COMPLETED` is one of the few
 * contexts permitted to start a foreground service from the background.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Graph.init(app)
                Engine.applySettings(app, Graph.prefs.current())
            } catch (e: Exception) {
                // The app starts the engine itself the next time it is opened.
            } finally {
                pending.finish()
            }
        }
    }
}
