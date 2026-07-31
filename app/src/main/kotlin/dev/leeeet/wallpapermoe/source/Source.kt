package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.image.ImagePipeline
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A source of wallpapers.
 *
 * Two obligations: [sample] must abandon promptly on cancellation, and the file
 * it returns stays owned by the source that produced it, readable only until
 * that source's next sample.
 */
sealed interface Source {

    /** The next wallpaper and the remote original it was prepared from. */
    suspend fun sample(ctx: SampleContext): WallpaperSample
}

data class WallpaperSample(
    val prepared: File,
    val originalUrl: String,
) {
    init { require(originalUrl.isNotBlank()) }
}

class SampleContext(
    val widthPx: Int,
    val heightPx: Int,
    val images: ImagePipeline,
    val providers: Providers,
    val storage: Storage,
    val archive: WallpaperArchive,
    /** Scoped to the service, so work left running here dies with the process. */
    val background: CoroutineScope,
)

class Providers(
    val pandora: PandoraApi,
    val wallhaven: WallhavenApi,
)

enum class SourceProvider { PANDORA, WALLHAVEN }

/** A stable, localizable reason sampling failed. Implementation details stay in [Throwable.cause]. */
sealed interface SampleFailure {
    data class ProviderUnavailable(val provider: SourceProvider) : SampleFailure
    data class ProviderReturnedNothing(val provider: SourceProvider) : SampleFailure
    data class DownloadFailed(val provider: SourceProvider) : SampleFailure
    data class CandidatesUnavailable(val provider: SourceProvider) : SampleFailure
    data object CacheEmpty : SampleFailure
    data object FavoritesEmpty : SampleFailure
    data object Unexpected : SampleFailure
}

class SampleException(
    val failure: SampleFailure,
    cause: Throwable? = null,
) : Exception(cause)

/** Shared JSON rules for source definitions and their small runtime state files. */
val documentJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
