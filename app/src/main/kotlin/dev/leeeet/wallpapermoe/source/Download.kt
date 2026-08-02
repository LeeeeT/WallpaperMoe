package dev.leeeet.wallpapermoe.source

import android.graphics.BitmapFactory
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.await
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloads the one image selected for this sample. */
internal suspend fun <T> download(
    candidate: T,
    identity: (T) -> String,
    url: (T) -> String,
    metadata: (T) -> WallpaperMetadata,
    dir: File,
    provider: SourceProvider,
): WallpaperSample {
    val destination = dir.newSampleFile()
    try {
        downloadFile(url(candidate), destination)
    } catch (e: CancellationException) {
        destination.delete()
        throw e
    } catch (e: Exception) {
        destination.delete()
        throw SampleException(SampleFailure.DownloadFailed(provider), e)
    }
    dir.pruneSamples()
    return WallpaperSample(wallpaperId(identity(candidate)), destination, metadata(candidate))
}

internal suspend fun downloadFile(url: String, destination: File) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", APP_USER_AGENT)
        .build()
    Http.client.await(request).use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> response.body.byteStream().copyTo(output) }
    }
    if (destination.length() == 0L) throw IOException("empty image response")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(destination.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("invalid image response")
}
