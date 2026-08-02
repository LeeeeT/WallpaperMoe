package dev.leeeet.wallpapermoe.wallpaper

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

enum class GallerySaveResult {
    Saved,
    AlreadySaved,
    Failed,
}

internal class GallerySaver(
    private val sink: GallerySink,
) {
    suspend fun save(wallpaper: SavedWallpaper): GallerySaveResult = withContext(Dispatchers.IO) {
        val image = inspect(wallpaper.file)
        val displayName = "WallpaperMoe_${wallpaper.file.sha256()}.${image.extension}"
        sink.write(wallpaper.file, image.mimeType, displayName)
    }

    private fun inspect(file: File): GalleryImage {
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
        return GalleryImage(mimeType, extension)
    }
}

internal fun interface GallerySink {
    fun write(source: File, mimeType: String, displayName: String): GallerySaveResult
}

internal class MediaStoreGallerySink(private val resolver: ContentResolver) : GallerySink {
    override fun write(source: File, mimeType: String, displayName: String): GallerySaveResult {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val exists = resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf(displayName, ALBUM_PATH),
            null,
        )?.use { it.moveToFirst() } == true
        if (exists) return GallerySaveResult.AlreadySaved

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
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
            return GallerySaveResult.Saved
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
    }

    private companion object {
        val ALBUM_PATH = "${Environment.DIRECTORY_PICTURES}/Wallpaper Moe/"
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val hex = CharArray(64)
    digest.digest().forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        hex[index * 2] = HEX[value ushr 4]
        hex[index * 2 + 1] = HEX[value and 0x0f]
    }
    return String(hex)
}

private data class GalleryImage(val mimeType: String, val extension: String)

private const val HEX = "0123456789abcdef"
