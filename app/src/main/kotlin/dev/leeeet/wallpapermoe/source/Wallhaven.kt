package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

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
    val sorting: WallhavenSorting,
    val apiKey: String,
    val store: String,
) : Source {

    init {
        require(general || anime || people)
        require(sfw || sketchy || nsfw)
        require(!nsfw || apiKey.isNotBlank())
    }

    override suspend fun sample(ctx: SampleContext): WallpaperSample {
        val results = try {
            ctx.providers.wallhaven.search(
                WallhavenApi.Query(
                    text = query,
                    general = general,
                    anime = anime,
                    people = people,
                    sfw = sfw,
                    sketchy = sketchy,
                    nsfw = nsfw,
                    sorting = sorting.apiValue,
                    apiKey = apiKey,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SampleException(SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN), e)
        }
        if (results.isEmpty()) {
            throw SampleException(SampleFailure.ProviderReturnedNothing(SourceProvider.WALLHAVEN))
        }

        return downloadOne(
            candidates = results.shuffled().take(CANDIDATE_ATTEMPTS),
            url = { it.url },
            dir = ctx.storage.dir(store),
            ctx = ctx,
            provider = SourceProvider.WALLHAVEN,
        )
    }
}
