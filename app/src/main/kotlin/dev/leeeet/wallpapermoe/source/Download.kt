package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.image.ImageException
import kotlinx.coroutines.CancellationException
import java.io.File

/** Wallpaper CDNs carry dead entries, so a sample tries more than one candidate. */
internal const val CANDIDATE_ATTEMPTS = 3

/** Downloads the first candidate that works, giving up at once on a transient failure. */
internal suspend fun <T> downloadOne(
    candidates: List<T>,
    url: (T) -> String,
    dir: File,
    ctx: SampleContext,
    provider: SourceProvider,
): WallpaperSample {
    var lastPermanent: ImageException? = null
    for (candidate in candidates) {
        val destination = dir.newSampleFile()
        try {
            val originalUrl = url(candidate)
            ctx.images.fetch(originalUrl, destination, ctx.widthPx, ctx.heightPx)
            dir.pruneSamples()
            return WallpaperSample(destination, originalUrl)
        } catch (e: CancellationException) {
            destination.delete()
            throw e
        } catch (e: ImageException) {
            destination.delete()
            if (!e.permanent) throw SampleException(SampleFailure.DownloadFailed(provider), e)
            lastPermanent = e
        }
    }
    throw SampleException(SampleFailure.CandidatesUnavailable(provider), lastPermanent)
}
