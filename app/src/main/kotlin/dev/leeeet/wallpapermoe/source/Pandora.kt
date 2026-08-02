package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException

/** Xiaomi's Pandora service, restricted to a set of its channels. */
data class Pandora(
    val channelIds: List<String>,
    val store: String,
) : Source {

    init {
        require(channelIds.isNotEmpty())
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val image = try {
            ctx.providers.pandora.image(channelIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SampleException(SampleFailure.ProviderUnavailable(SourceProvider.PANDORA), e)
        }
        if (image == null) {
            throw SampleException(SampleFailure.ProviderReturnedNothing(SourceProvider.PANDORA))
        }

        return download(
            candidate = image,
            identity = { "pandora:${it.id}" },
            imageUrl = { it.urlFor(ctx.widthPx) },
            metadata = { WallpaperMetadata(imageUrl = it.urlFor(ctx.widthPx)) },
            dir = ctx.storage.dir(store),
            ctx = ctx,
            provider = SourceProvider.PANDORA,
        )
    }

}
