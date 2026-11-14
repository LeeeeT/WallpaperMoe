package dev.leeeet.wallpapermoe.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.util.Log
import dev.leeeet.wallpapermoe.data.Store
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Screen
import dev.leeeet.wallpapermoe.model.due
import dev.leeeet.wallpapermoe.model.withNext
import dev.leeeet.wallpapermoe.model.showing
import dev.leeeet.wallpapermoe.source.Failure
import dev.leeeet.wallpapermoe.source.SampleException
import dev.leeeet.wallpapermoe.source.Sampler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface Status {
    data object Idle : Status
    data object Working : Status
    data class Failed(val failure: Failure) : Status
}

class Changer(
    context: Context,
    private val store: Store,
    private val sampler: Sampler,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val wakeLock = power
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WallpaperMoe:change")
        .apply { setReferenceCounted(false) }
    private val running = HashMap<Screen, Job>()
    private val refilling = Mutex()
    private val state = MutableStateFlow<Map<Screen, Status>>(emptyMap())

    val status: StateFlow<Map<Screen, Status>> = state

    fun screenOff() {
        scope.launch {
            wakeLock.acquire(WAKE_LIMIT_MS)
            try {
                val now = System.currentTimeMillis()
                val data = store.current()
                data.feeds.filter { data.due(it, now) }.map { change(it, manual = false) }.joinAll()
                refill()
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    fun start() {
        scope.launch {
            val data = store.current()
            data.feeds.filter { data.shown[it] == null && data.feed(it)?.sourceId != null }.map { change(it, manual = true) }.joinAll()
            refill()
        }
    }

    fun changeAll() {
        scope.launch {
            store.current().feeds.map { change(it, manual = true) }.joinAll()
            refill()
        }
    }

    fun changeNow(screen: Screen) {
        scope.launch {
            change(screen, manual = true).join()
            refill()
        }
    }

    fun show(screen: Screen, image: Image) {
        scope.launch { store.update { it.showing(screen, image, System.currentTimeMillis()) } }
    }

    private fun change(screen: Screen, manual: Boolean): Job = synchronized(running) {
        running[screen]?.takeIf(Job::isActive) ?: scope.launch {
            state.update { it + (screen to Status.Working) }
            val result = try {
                store.pinned { pins ->
                    val data = store.current()
                    val image = data.next[screen] ?: run {
                        val sourceId = data.feed(screen)?.sourceId ?: throw SampleException(Failure.NoSource)
                        sampler.sample(sourceId, pins, online = manual || online())
                    }
                    store.update(pins) {
                        if (manual || !power.isInteractive) it.showing(screen, image, System.currentTimeMillis()) else it.withNext(screen, image)
                    }
                }
                Status.Idle
            } catch (e: SampleException) {
                Log.w(TAG, "The wallpaper change failed for $screen", e)
                Status.Failed(e.failure)
            }
            state.update { it + (screen to result) }
        }.also { running[screen] = it }
    }

    private suspend fun refill() {
        if (!refilling.tryLock()) return
        try {
            store.current().feeds.forEach { prepareNext(it) }
            val data = store.current()
            sampler.refill(data.feeds.mapNotNull { data.feed(it)?.sourceId }, online())
        } catch (e: SampleException) {
            Log.w(TAG, "The refill failed", e)
        } finally {
            refilling.unlock()
        }
    }

    private suspend fun prepareNext(screen: Screen) {
        val data = store.current()
        if (data.next[screen] != null) return
        val sourceId = data.feed(screen)?.sourceId ?: return
        try {
            store.pinned { pins ->
                val image = sampler.sample(sourceId, pins, online = false)
                store.update(pins) { current ->
                    if (current.feed(screen)?.sourceId == sourceId && current.next[screen] == null) current.withNext(screen, image) else current
                }
            }
        } catch (e: SampleException) {
            Log.w(TAG, "The next wallpaper cannot be prepared for $screen", e)
        }
    }

    private suspend fun online(): Boolean {
        if (!store.current().unmeteredOnly) return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val TAG = "WallpaperMoe"
        const val WAKE_LIMIT_MS = 3 * 60_000L
    }
}
