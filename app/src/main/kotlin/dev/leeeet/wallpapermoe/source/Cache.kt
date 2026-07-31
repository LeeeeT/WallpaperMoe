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
        val entries: List<String> = emptyList(),
        val cursor: Int = 0,
        val lastAddedAt: Long = 0L,
    )

    private val mutex = Mutex()

    override suspend fun sample(ctx: SampleContext): WallpaperSample = mutex.withLock {
        val dir = ctx.storage.dir(store)
        val state = dir.loadState<State>() ?: State()
        val live = dir.liveNames(state.entries)
        val now = System.currentTimeMillis()
        val due = refreshHours > 0 && now - state.lastAddedAt >= refreshHours * 3_600_000L

        if (live.size < capacity || due) {
            try {
                return@withLock take(source, ctx, dir, state, live, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SampleException) {
                // Fatal to an empty store; a store with images serves those.
                if (live.isEmpty()) throw e
            } catch (e: Exception) {
                if (live.isEmpty()) throw SampleException(SampleFailure.Unexpected, e)
            }
        }

        if (live.isEmpty()) throw SampleException(SampleFailure.CacheEmpty)
        serve(dir, state, live)
    }

    private suspend fun take(
        source: Source,
        ctx: SampleContext,
        dir: File,
        state: State,
        live: List<String>,
        now: Long,
    ): WallpaperSample {
        val adopted = adopt(source.sample(ctx), dir, "c")
        val entryName = requireNotNull(adopted.prepared.parentFile).name
        val entries = (live + entryName).let { all ->
            val overflow = all.size - capacity
            if (overflow <= 0) return@let all
            all.take(overflow).forEach { java.io.File(dir, it).deleteRecursively() }
            all.drop(overflow)
        }
        dir.saveState(state.copy(entries = entries, lastAddedAt = now))
        return adopted
    }

    private fun serve(dir: java.io.File, state: State, live: List<String>): WallpaperSample {
        val index = ((state.cursor % live.size) + live.size) % live.size
        dir.saveState(state.copy(entries = live, cursor = state.cursor + 1))
        return checkNotNull(java.io.File(dir, live[index]).storedSample())
    }
}
