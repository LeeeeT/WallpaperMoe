package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.CancellationException
import java.io.File

/** Downloads the one image selected for this sample. */
internal suspend fun <T> download(
    candidate: T,
    identity: (T) -> String,
    imageUrl: (T) -> String,
    metadata: (T) -> WallpaperMetadata,
    dir: File,
    ctx: SampleContext,
    provider: SourceProvider,
): WallpaperSample {
    val destination = dir.newSampleFile()
    try {
        ctx.images.fetch(imageUrl(candidate), destination, ctx.widthPx, ctx.heightPx)
    } catch (e: CancellationException) {
        destination.delete()
        throw e
    } catch (e: Exception) {
        destination.delete()
        throw SampleException(SampleFailure.DownloadFailed(provider), e)
    }
    dir.pruneSamples()
    return WallpaperSample(newWallpaperId(identity(candidate)), destination, metadata(candidate))
}
