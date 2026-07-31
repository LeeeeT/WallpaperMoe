package dev.leeeet.wallpapermoe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import dev.leeeet.wallpapermoe.Graph
import dev.leeeet.wallpapermoe.MainActivity
import dev.leeeet.wallpapermoe.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * The engine. `ACTION_SCREEN_OFF` starts a sample and `ACTION_SCREEN_ON` cancels
 * it; both are registration-only broadcasts, so they are registered here.
 *
 * A foreground service because a sample may take as long as it likes, and that is
 * the one host on Android for work outliving the app's foreground. The
 * notification is what keeps the process alive. See the README.
 */
class RotationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The only job the screen coming back is allowed to cancel; a manual rotation runs outside it. */
    private var lockJob: Job? = null

    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Graph.init(applicationContext)
        goForeground()
        register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        register()
        if (intent?.action == ACTION_ROTATE_NOW) rotateNow()
        return START_STICKY
    }

    private fun register() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff()
                    Intent.ACTION_SCREEN_ON -> onScreenOn()
                }
            }
        }
        // NOT_EXPORTED still receives these: the framework waives the export
        // check for system_server, which is where both broadcasts come from.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED)
        receiver = r
    }

    private fun onScreenOff() {
        // Still working on the last one; restarting would throw away whatever
        // it has already downloaded.
        if (lockJob?.isActive == true) return
        lockJob = scope.launch {
            Graph.rotator.rotate(
                respectInterval = true,
                onlyWhileScreenOff = true,
                background = scope,
            )
        }
    }

    private fun onScreenOn() {
        val job = lockJob ?: return
        if (job.isActive) {
            job.cancel()
        }
        lockJob = null
    }

    private fun rotateNow() {
        scope.launch {
            Graph.rotator.rotate(
                respectInterval = false,
                onlyWhileScreenOff = false,
                background = scope,
            )
        }
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        scope.coroutineContext.cancelChildren()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        // Set once for the life of the channel: deleting one a foreground
        // service is using throws SecurityException.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_rotation),
            NotificationManager.IMPORTANCE_NONE,
        ).apply {
            description = getString(R.string.channel_rotation_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moe)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setContentIntent(tap)
            .build()

        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        private const val CHANNEL_ID = "moe_rotation"
        private const val NOTIF_ID = 4711

        const val ACTION_ROTATE_NOW = "dev.leeeet.wallpapermoe.ROTATE_NOW"

        /** Process-local, like the receivers it describes: a killed process resets it. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
