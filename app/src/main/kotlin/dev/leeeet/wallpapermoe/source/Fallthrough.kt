package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException

/** Takes from the first of the sources it holds that gives an image. */
data class Fallthrough(
    val sources: List<Source>,
) : Source {

    init {
        require(sources.size >= 2)
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        var first: Exception? = null
        for (source in sources) {
            try {
                return source.sample(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (first == null) first = e
            }
        }
        if (first is SampleException) throw first
        throw SampleException(SampleFailure.Unexpected, first)
    }
}
