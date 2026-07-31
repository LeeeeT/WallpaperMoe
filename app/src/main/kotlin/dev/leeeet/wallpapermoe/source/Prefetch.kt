package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Samples ahead in the background, so the lock path is served from the buffer.
 *
 * The buffer is the `r-` files in this source's folder; there is no other
 * record of it. Consuming one is renaming it out of the buffer — the rename is
 * atomic, so concurrent consumers can race for the same head and exactly one
 * of them gets it.
 */
data class Prefetch(
    val source: Source,
    val depth: Int,
    val store: String,
) : Source {

    init {
        require(depth > 0)
    }

    private val filling = Mutex()

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val dir = ctx.storage.dir(store)

        // Out of the buffer into the handed-out set, so pruneSamples governs
        // its lifetime.
        val handed = takeBuffered(dir)
        if (handed != null) {
            dir.pruneSamples()
            refillInBackground(ctx, dir)
            return handed
        }

        val produced = try {
            source.sample(ctx)
        } finally {
            refillInBackground(ctx, dir)
        }
        val adopted = adopt(produced, dir, "s")
        dir.pruneSamples()
        return adopted
    }

    private fun takeBuffered(dir: java.io.File): WallpaperSample? {
        for (buffered in buffered(dir)) {
            val target = java.io.File(dir, "s-${System.currentTimeMillis()}-${System.nanoTime()}")
            if (buffered.renameTo(target)) return target.storedSample()
        }
        return null
    }

    /** Oldest first: names carry the wall clock, so the order survives a reboot. */
    private fun buffered(dir: java.io.File): List<java.io.File> = dir.listFiles().orEmpty()
        .filter { it.name.startsWith("r-") && it.storedSample() != null }
        .sortedBy { it.name }

    /** `tryLock`: one refill runs at a time, and the rest return at once. */
    private fun refillInBackground(ctx: SampleContext, dir: java.io.File) {
        ctx.background.launch {
            if (!filling.tryLock()) return@launch
            try {
                // Half-copied buffer files a crash left behind. Only the one
                // running refill creates these, so they are safe to clear here.
                dir.listFiles().orEmpty()
                    .filter { it.name.startsWith("r-") && it.name.endsWith(".part") }
                    .forEach { it.deleteRecursively() }
                while (buffered(dir).size < depth) {
                    adopt(source.sample(ctx), dir, "r")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stop at the first failure; the next rotation retries.
            } finally {
                filling.unlock()
            }
        }
    }
}
