package dev.leeeet.wallpapermoe.source

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class FavoritesMode { RANDOM, ROUND_ROBIN }

/** Samples the permanent favorites collection without taking ownership of its files. */
data class Favorites(
    val mode: FavoritesMode,
    val store: String,
) : Source {

    @Serializable
    private data class State(val next: Int = 0)

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val favorites = ctx.archive.favorites.value
        if (favorites.isEmpty()) throw SampleException(SampleFailure.FavoritesEmpty)
        val index = when (mode) {
            FavoritesMode.RANDOM -> Random.nextInt(favorites.size)
            FavoritesMode.ROUND_ROBIN -> {
                val dir = ctx.storage.dir(store)
                val next = dir.loadState<State>()?.next ?: 0
                val selected = ((next % favorites.size) + favorites.size) % favorites.size
                dir.saveState(State(selected + 1))
                selected
            }
        }
        val selected = favorites[index].let { WallpaperSample(it.prepared, it.originalUrl) }
        val adopted = adopt(selected, ctx.storage.dir(store), "s")
        ctx.storage.dir(store).pruneSamples()
        return adopted
    }
}
