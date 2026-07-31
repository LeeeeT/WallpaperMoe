package dev.leeeet.wallpapermoe.source

import kotlinx.serialization.Serializable

/** Takes from each of the sources it holds in turn. */
data class RoundRobin(
    val sources: List<Source>,
    val store: String,
) : Source {

    init {
        require(sources.isNotEmpty())
    }

    /** Where the next take lands, kept in this source's folder. */
    @Serializable
    private data class State(val next: Int = 0)

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val dir = ctx.storage.dir(store)
        val next = dir.loadState<State>()?.next ?: 0
        val index = ((next % sources.size) + sources.size) % sources.size

        // Advanced before sampling, so the next lock lands on the next source
        // whatever this one does.
        dir.saveState(State(next = index + 1))

        return sources[index].sample(ctx)
    }
}
