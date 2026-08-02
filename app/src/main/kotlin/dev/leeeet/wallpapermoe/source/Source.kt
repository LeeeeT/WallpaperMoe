package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestDownloader
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A source of wallpapers.
 *
 * Two obligations: [sample] must abandon promptly on cancellation, and the file
 * it returns stays owned by the source that produced it, readable only until
 * that source's next sample.
 */
sealed interface Source {
    /** The next wallpaper, carrying only the metadata its provider actually supplied. */
    suspend fun sample(ctx: SampleContext): WallpaperSample
}

data class WallpaperSample(
    /** Stable while this sample moves through cache, history, and favorites. */
    val id: String,
    val file: File,
    val metadata: WallpaperMetadata = WallpaperMetadata(),
) {
    init { require(id.isNotBlank()) }
}

/** Optional provider metadata. No source is required to manufacture fields it does not have. */
@Serializable
data class WallpaperMetadata(
    /** Page where the original work is published. */
    val sourceUrl: String? = null,
    val artistName: String? = null,
    val artistUrl: String? = null,
) {
    init {
        require(listOfNotNull(sourceUrl, artistName, artistUrl).all { it.isNotBlank() })
    }
}

fun newWallpaperId(providerIdentity: String? = null): String = if (providerIdentity == null) {
    UUID.randomUUID().toString()
} else {
    UUID.nameUUIDFromBytes(providerIdentity.toByteArray(StandardCharsets.UTF_8)).toString()
}

class SampleContext(
    val providers: Providers,
    val storage: Storage,
    val archive: WallpaperArchive,
    /** Scoped to the service, so work left running here dies with the process. */
    val background: CoroutineScope,
)

class Providers(
    val pandora: PandoraApi,
    val wallhaven: WallhavenApi,
    val nekosBest: NekosBestDownloader,
)

enum class SourceProvider { PANDORA, WALLHAVEN, NEKOS_BEST }

/** A stable, localizable reason sampling failed. Implementation details stay in [Throwable.cause]. */
sealed interface SampleFailure {
    data class ProviderUnavailable(val provider: SourceProvider) : SampleFailure
    data class ProviderReturnedNothing(val provider: SourceProvider) : SampleFailure
    data class DownloadFailed(val provider: SourceProvider) : SampleFailure
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
    explicitNulls = false
}
