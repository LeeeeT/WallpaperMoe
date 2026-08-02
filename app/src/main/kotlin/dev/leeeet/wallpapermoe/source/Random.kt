package dev.leeeet.wallpapermoe.source

import kotlin.random.Random as Rng

/** Picks one of the sources it holds at random. */
data class Random(
    val sources: List<Source>,
) : Source {

    init {
        require(sources.size >= 2)
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        return sources[Rng.nextInt(sources.size)].sample(ctx)
    }
}
