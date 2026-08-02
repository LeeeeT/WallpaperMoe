package dev.leeeet.wallpapermoe.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a URL into a file the system can be handed directly.
 *
 * Decode, scale, centre-crop and re-encode all happen here, at sample time, so
 * applying the result is a stream copy. The crop is the app's own because
 * WallpaperManager's crop hints land one image differently on lock and home.
 */
class ImagePipeline(private val scratchDir: File) {

    init {
        scratchDir.mkdirs()
    }

    /**
     * Downloads [url] and writes a display-ready image to [destination], staged
     * through a temporary file, so [destination] only ever appears complete.
     */
    suspend fun fetch(
        url: String,
        destination: File,
        widthPx: Int,
        heightPx: Int,
    ) = withContext(Dispatchers.IO) {
        val staged = File(scratchDir, "dl-${System.nanoTime()}.part")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", APP_USER_AGENT)
                .build()
            Http.client.await(request).use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                staged.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            }
            if (staged.length() <= 0) throw IOException("empty image response")

            if (!render(staged, destination, widthPx, heightPx)) {
                throw IOException("image could not be rendered")
            }
        } finally {
            staged.delete()
        }
    }

    private fun render(source: File, destination: File, widthPx: Int, heightPx: Int): Boolean {
        val bitmap = decodeScaled(source, widthPx, heightPx) ?: return false
        val staged = File(destination.absolutePath + ".rendering")
        return try {
            destination.parentFile?.mkdirs()
            staged.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
            }
            staged.renameTo(destination)
        } catch (e: Exception) {
            staged.delete()
            false
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /** Decodes via `inSampleSize`, so the bitmap arrives near the target size. */
    private fun decodeScaled(file: File, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        if (decoded.width == targetW && decoded.height == targetH) return decoded

        val scale = max(targetW.toFloat() / decoded.width, targetH.toFloat() / decoded.height)
        val scaledW = (decoded.width * scale).roundToInt()
        val scaledH = (decoded.height * scale).roundToInt()
        val left = (scaledW - targetW) / 2
        val top = (scaledH - targetH) / 2

        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(decoded, null, Rect(-left, -top, scaledW - left, scaledH - top), null)
        decoded.recycle()
        return out
    }

    private fun sampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var sample = 1
        while (srcW / (sample * 2) >= dstW && srcH / (sample * 2) >= dstH) sample *= 2
        return sample
    }

    private companion object {
        const val QUALITY = 90
    }
}
