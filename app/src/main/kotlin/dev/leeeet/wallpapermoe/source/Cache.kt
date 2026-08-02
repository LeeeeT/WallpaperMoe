package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File

/** Keeps images the source it holds produced, and serves them back when it fails. */
data class Cache(
    val source: Source,
    val capacity: Int,
    val refreshHours: Int,
    val store: String,
) : Source {

    init {
        require(capacity > 0)
        require(refreshHours >= 0)
    }

    /** What the cache remembers between samples, kept in its folder. Entries are oldest first. */
    @Serializable
    private data class State(
        val entries: List<String>,
        val cursor: Int,
        val lastAddedAt: Long,
    ) {
        init {
            require(entries.distinct().size == entries.size)
            require(entries.all { it.isNotBlank() && java.io.File(it).name == it })
            require(lastAddedAt >= 0)
        }
    }

    private val mutex = Mutex()

    override suspend fun sample(ctx: SampleContext): WallpaperSample = mutex.withLock {
        val dir = ctx.storage.dir(store)
        val state = dir.loadState<State>() ?: State(emptyList(), 0, 0L)
        state.entries.forEach { java.io.File(dir, it).readStoredSample() }
        sweepOrphans(dir, state.entries)
        val now = System.currentTimeMillis()
        val due = refreshHours > 0 && now - state.lastAddedAt >= refreshHours * 3_600_000L

        if (state.entries.size < capacity || due) {
            val produced = try {
                source.sample(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SampleException) {
                if (state.entries.isEmpty()) throw e
                null
            } catch (e: Exception) {
                if (state.entries.isEmpty()) throw SampleException(SampleFailure.Unexpected, e)
                null
            }
            if (produced != null) return@withLock store(produced, dir, state, now)
        }

        if (state.entries.isEmpty()) throw SampleException(SampleFailure.CacheEmpty)
        serve(dir, state)
    }

    private suspend fun store(
        produced: WallpaperSample,
        dir: File,
        state: State,
        now: Long,
    ): WallpaperSample {
        val adopted = adopt(produced, dir, "c")
        val entryName = requireNotNull(adopted.file.parentFile).name
        val all = state.entries + entryName
        val overflow = (all.size - capacity).coerceAtLeast(0)
        val removed = all.take(overflow)
        val entries = all.drop(overflow)
        dir.saveState(state.copy(entries = entries, lastAddedAt = now))
        removed.forEach { java.io.File(dir, it).deleteRecursively() }
        return adopted
    }

    private fun serve(dir: java.io.File, state: State): WallpaperSample {
        val index = ((state.cursor % state.entries.size) + state.entries.size) % state.entries.size
        dir.saveState(state.copy(cursor = state.cursor + 1))
        return java.io.File(dir, state.entries[index]).readStoredSample()
    }

    private fun sweepOrphans(dir: File, entries: List<String>) {
        val live = entries.toSet()
        dir.listFiles().orEmpty()
            .filter { (it.name.startsWith("c-") && it.name !in live) || it.name.startsWith(".c-") }
            .forEach { it.deleteRecursively() }
    }
}
