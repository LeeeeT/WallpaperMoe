package dev.leeeet.wallpapermoe.provider.nekosbest

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide entry point for Nekos.best.
 *
 * Every category has its own FIFO-like lane because the API publishes its
 * 200-per-minute limit per category. A lane spaces acquisition starts by at
 * least 300 ms and remains locked through the image download, so any number of
 * callers can enqueue work without producing a burst.
 */
class NekosBestDownloader(
    private val api: NekosBestApi = NekosBestApi(),
    private val minimumIntervalMs: Long = MINIMUM_INTERVAL_MS,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    init { require(minimumIntervalMs >= 0) }

    private class Lane {
        val mutex = Mutex()
        var nextStartAtMs = 0L
    }

    private val lanes = ConcurrentHashMap<String, Lane>()

    suspend fun imageCategories(): List<String> = api.imageCategories()

    /** Returns null when the provider has no image for this acquisition. */
    suspend fun acquire(
        category: String,
        download: suspend (NekosBestApi.Image) -> Unit,
    ): NekosBestApi.Image? {
        require(NekosBestApi.isCategoryName(category))
        val lane = lanes.getOrPut(category, ::Lane)
        return lane.mutex.withLock {
            val remaining = lane.nextStartAtMs - nowMillis()
            if (remaining > 0) delay(remaining)
            lane.nextStartAtMs = nowMillis() + minimumIntervalMs

            val image = api.image(category) ?: return@withLock null
            download(image)
            image
        }
    }

    companion object {
        /** 200 requests/minute per category, smoothed rather than burst. */
        const val MINIMUM_INTERVAL_MS = 60_000L / 200L
    }
}
