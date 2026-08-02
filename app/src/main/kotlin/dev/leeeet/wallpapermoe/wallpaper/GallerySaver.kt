package dev.leeeet.wallpapermoe.wallpaper

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

internal class GallerySaver(
    private val scratchDir: File,
    private val sink: GallerySink,
    private val client: OkHttpClient = Http.client,
) {
    init {
        scratchDir.mkdirs()
    }

    suspend fun save(wallpaper: SavedWallpaper) = withContext(Dispatchers.IO) {
        val original = wallpaper.metadata.imageUrl?.let { url ->
            try {
                downloadOriginal(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        val image = original ?: inspect(wallpaper.prepared, temporary = false)
        try {
            sink.write(
                image.file,
                image.mimeType,
                "WallpaperMoe_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${image.extension}",
            )
        } finally {
            if (image.temporary) image.file.delete()
        }
    }

    private suspend fun downloadOriginal(url: String): GalleryImage {
        val staged = File(scratchDir, "gallery-${UUID.randomUUID()}.download")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", APP_USER_AGENT)
                .header("Accept", "image/*")
                .build()
            client.await(request).use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                staged.outputStream().use { output -> response.body.byteStream().copyTo(output) }
            }
            if (staged.length() <= 0L) throw IOException("empty image response")
            return inspect(staged, temporary = true)
        } catch (e: Exception) {
            staged.delete()
            throw e
        }
    }

    private fun inspect(file: File, temporary: Boolean): GalleryImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("invalid image")
        val mimeType = bounds.outMimeType?.lowercase() ?: throw IOException("unknown image type")
        val extension = when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/avif" -> "avif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> throw IOException("unsupported image type")
        }
        return GalleryImage(file, mimeType, extension, temporary)
    }
}

internal fun interface GallerySink {
    fun write(source: File, mimeType: String, displayName: String)
}

internal class MediaStoreGallerySink(private val resolver: ContentResolver) : GallerySink {
    override fun write(source: File, mimeType: String, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Wallpaper Moe")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val item = resolver.insert(collection, values) ?: throw IOException("gallery insert failed")
        try {
            resolver.openOutputStream(item, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("gallery output unavailable")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(item, values, null, null) != 1) {
                throw IOException("gallery publish failed")
            }
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
    }
}

private data class GalleryImage(
    val file: File,
    val mimeType: String,
    val extension: String,
    val temporary: Boolean,
)
