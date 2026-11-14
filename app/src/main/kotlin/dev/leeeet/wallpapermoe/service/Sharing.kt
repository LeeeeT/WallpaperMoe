package dev.leeeet.wallpapermoe.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.leeeet.wallpapermoe.data.Blobs
import dev.leeeet.wallpapermoe.model.Image
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Sharing(private val context: Context, private val blobs: Blobs) {
    fun shareIntent(image: Image): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", blobs.file(image.blob), image.fileName())
        val send = Intent(Intent.ACTION_SEND)
            .setType(image.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    suspend fun saveToGallery(image: Image): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val name = image.fileName()
        val saved = resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(name, "$GALLERY_PATH/"),
            null,
        )?.use { it.count > 0 } ?: false
        if (saved) return@withContext false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, image.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The gallery refused the image")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("The gallery refused the image")
            output.use { blobs.file(image.blob).inputStream().use { input -> input.copyTo(it) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        true
    }

    private companion object {
        val GALLERY_PATH = "${Environment.DIRECTORY_PICTURES}/Wallpaper Moe"
    }

    private fun Image.fileName(): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "img"
        return "wallpaper-moe-${blob.take(12)}.$extension"
    }
}
