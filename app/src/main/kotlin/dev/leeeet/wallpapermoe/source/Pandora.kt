package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException

/**
 * Xiaomi's Pandora service, restricted to a set of its channels.
 *
 * [channelLabels] is denormalised alongside the ids so the editor can name what
 * was picked without a round trip.
 */
data class Pandora(
    val channelIds: List<String>,
    val channelLabels: List<String>,
    val store: String,
) : Source {

    init {
        require(channelIds.isNotEmpty())
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val images = try {
            ctx.providers.pandora.images(channelIds, FEED_SIZE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SampleException(SampleFailure.ProviderUnavailable(SourceProvider.PANDORA), e)
        }
        if (images.isEmpty()) {
            throw SampleException(SampleFailure.ProviderReturnedNothing(SourceProvider.PANDORA))
        }

        return downloadOne(
            candidates = images.shuffled().take(CANDIDATE_ATTEMPTS),
            url = { it.urlFor(ctx.widthPx) },
            dir = ctx.storage.dir(store),
            ctx = ctx,
            provider = SourceProvider.PANDORA,
        )
    }

    private companion object {
        const val FEED_SIZE = 60
    }
}
