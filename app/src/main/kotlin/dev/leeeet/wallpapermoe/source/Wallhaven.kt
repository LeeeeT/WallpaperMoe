package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
enum class WallhavenAspect(val apiValues: List<String>) {
    PORTRAIT(listOf("portrait")),
    SQUARE(listOf("1x1", "3x2", "4x3", "5x4")),
    LANDSCAPE(listOf("landscape"));

    companion object {
        val ALL: Set<WallhavenAspect> = entries.toSet()
    }
}

@Serializable
enum class WallhavenSorting(val apiValue: String) {
    RANDOM("random"),
    DATE_ADDED("date_added"),
    TOPLIST("toplist"),
    VIEWS("views"),
    FAVORITES("favorites"),
}

/** A wallhaven.cc search. An API key is only needed for NSFW results. */
data class Wallhaven(
    val query: String,
    val general: Boolean,
    val anime: Boolean,
    val people: Boolean,
    val sfw: Boolean,
    val sketchy: Boolean,
    val nsfw: Boolean,
    val aspects: Set<WallhavenAspect>,
    val sorting: WallhavenSorting,
    val apiKey: String,
    val store: String,
) : Source {

    init {
        require(general || anime || people)
        require(sfw || sketchy || nsfw)
        require(aspects.isNotEmpty())
        require(!nsfw || apiKey.isNotBlank())
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val image = try {
            ctx.providers.wallhaven.image(
                WallhavenApi.Query(
                    text = query,
                    general = general,
                    anime = anime,
                    people = people,
                    sfw = sfw,
                    sketchy = sketchy,
                    nsfw = nsfw,
                    ratios = aspects.apiRatios(),
                    sorting = sorting.apiValue,
                    apiKey = apiKey,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SampleException(SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN), e)
        }
        if (image == null) {
            throw SampleException(SampleFailure.ProviderReturnedNothing(SourceProvider.WALLHAVEN))
        }

        return download(
            candidate = image,
            identity = { "wallhaven:${it.id}" },
            imageUrl = { it.url },
            metadata = { WallpaperMetadata(imageUrl = it.url) },
            dir = ctx.storage.dir(store),
            ctx = ctx,
            provider = SourceProvider.WALLHAVEN,
        )
    }
}

internal fun Set<WallhavenAspect>.apiRatios(): String? =
    if (this == WallhavenAspect.ALL) null
    else WallhavenAspect.entries
        .filter { it in this }
        .flatMap(WallhavenAspect::apiValues)
        .joinToString(",")
