package dev.leeeet.wallpapermoe.wallpaper

import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.leeeet.wallpapermoe.source.WallpaperMetadata
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class GallerySaverTest {
    private lateinit var root: File
    private val writes = mutableListOf<Write>()
    private val names = mutableSetOf<String>()

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "gallery-saver-${System.nanoTime()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun savesTheDownloadedFileUnchanged() = runBlocking {
        val downloadedBytes = jpeg(0xFF3366AA.toInt())
        val downloaded = File(root, "downloaded.wp").apply { writeBytes(downloadedBytes) }

        val result = saver().save(wallpaper(downloaded))

        assertEquals(GallerySaveResult.Saved, result)
        assertArrayEquals(downloadedBytes, writes.single().bytes)
        assertEquals("image/jpeg", writes.single().mimeType)
        assertTrue(writes.single().displayName.endsWith(".jpg"))
    }

    @Test
    fun repeatedSaveIsIdempotent() = runBlocking {
        val file = File(root, "downloaded.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }
        val saver = saver()

        val first = saver.save(wallpaper(file))
        val second = saver.save(wallpaper(file))

        assertEquals(GallerySaveResult.Saved, first)
        assertEquals(GallerySaveResult.AlreadySaved, second)
        assertEquals(1, writes.size)
    }

    @Test
    fun mediaStoreSinkKeepsOnePublishedItemAndAllowsSavingAfterDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val displayName = "WallpaperMoe_test_${System.nanoTime()}.jpg"
        val albumPath = "${Environment.DIRECTORY_PICTURES}/Wallpaper Moe/"
        val source = File(root, "media-store.jpg").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }
        val sink = MediaStoreGallerySink(resolver)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(displayName, albumPath)

        try {
            assertEquals(GallerySaveResult.Saved, sink.write(source, "image/jpeg", displayName))
            assertEquals(GallerySaveResult.AlreadySaved, sink.write(source, "image/jpeg", displayName))
            assertEquals(
                1,
                resolver.query(
                    collection,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    selectionArgs,
                    null,
                )?.use { it.count },
            )

            assertEquals(1, resolver.delete(collection, selection, selectionArgs))
            assertEquals(GallerySaveResult.Saved, sink.write(source, "image/jpeg", displayName))
        } finally {
            resolver.delete(collection, selection, selectionArgs)
        }
    }

    private fun saver() = GallerySaver(
        sink = GallerySink { source, mimeType, displayName ->
            if (!names.add(displayName)) {
                GallerySaveResult.AlreadySaved
            } else {
                writes += Write(source.readBytes(), mimeType, displayName)
                GallerySaveResult.Saved
            }
        },
    )

    private fun wallpaper(file: File) = SavedWallpaper(
        id = "wallpaper",
        metadata = WallpaperMetadata(),
        file = file,
        savedAt = 0L,
    )

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().also { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
        }.toByteArray()
    }

    private data class Write(
        val bytes: ByteArray,
        val mimeType: String,
        val displayName: String,
    )
}
