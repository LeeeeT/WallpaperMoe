package dev.leeeet.wallpapermoe.source

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Samples the permanent favorites collection without taking ownership of its files. */
data class Favorites(
    val mode: SelectionMode,
    val store: String,
) : Source {

    @Serializable
    private data class State(val next: Int)

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val favorites = ctx.archive.favorites.value
        if (favorites.isEmpty()) throw SampleException(SampleFailure.FavoritesEmpty)
        val index = when (mode) {
            SelectionMode.RANDOM -> Random.nextInt(favorites.size)
            SelectionMode.ROUND_ROBIN -> {
                val dir = ctx.storage.dir(store)
                val next = dir.loadState<State>()?.next ?: 0
                val selected = ((next % favorites.size) + favorites.size) % favorites.size
                dir.saveState(State(selected + 1))
                selected
            }
        }
        val selected = favorites[index].let { WallpaperSample(it.id, it.prepared, it.metadata) }
        val adopted = adopt(selected, ctx.storage.dir(store), "s")
        ctx.storage.dir(store).pruneSamples()
        return adopted
    }
}
