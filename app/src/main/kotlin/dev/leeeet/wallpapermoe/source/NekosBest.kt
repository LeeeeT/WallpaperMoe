package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Static artwork from one or more dynamically discovered nekos.best categories. */
data class NekosBest(
    val categories: List<String>,
    val mode: SelectionMode,
    val store: String,
) : Source {

    init {
        require(categories.isNotEmpty())
        require(categories.all(NekosBestApi::isCategoryName))
        require(categories.distinct().size == categories.size)
    }

    @Serializable
    private data class State(val next: Int)

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val category = categories[categoryIndex(ctx)]
        val dir = ctx.storage.dir(store)
        val destination = dir.newSampleFile()
        val image = try {
            ctx.providers.nekosBest.acquire(category) { selected ->
                try {
                    downloadFile(selected.url, destination)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw SampleException(SampleFailure.DownloadFailed(SourceProvider.NEKOS_BEST), e)
                }
            }
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: SampleException) {
            destination.delete()
            throw e
        } catch (e: Exception) {
            destination.delete()
            throw SampleException(SampleFailure.ProviderUnavailable(SourceProvider.NEKOS_BEST), e)
        }
        if (image == null) {
            destination.delete()
            throw SampleException(SampleFailure.ProviderReturnedNothing(SourceProvider.NEKOS_BEST))
        }

        dir.pruneSamples()
        return WallpaperSample(
            id = wallpaperId("nekos-best:${image.url}"),
            file = destination,
            metadata = WallpaperMetadata(
                sourceUrl = image.sourceUrl,
                artistName = image.artistName,
                artistUrl = image.artistUrl,
            ),
        )
    }

    private fun categoryIndex(ctx: SampleContext): Int = when (mode) {
        SelectionMode.RANDOM -> Random.nextInt(categories.size)
        SelectionMode.ROUND_ROBIN -> {
            val dir = ctx.storage.dir(store)
            val next = dir.loadState<State>()?.next ?: 0
            val selected = ((next % categories.size) + categories.size) % categories.size
            dir.saveState(State(selected + 1))
            selected
        }
    }
}
