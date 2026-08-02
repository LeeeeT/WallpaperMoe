package dev.leeeet.wallpapermoe.wallpaper

import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.disk.DiskCache
import dev.leeeet.wallpapermoe.source.WallpaperMetadata
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
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
    private lateinit var imageLoader: ImageLoader
    private val writes = mutableListOf<Write>()
    private val names = mutableSetOf<String>()

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "gallery-saver-${System.nanoTime()}",
        ).apply { mkdirs() }
        imageLoader = ImageLoader.Builder(InstrumentationRegistry.getInstrumentation().targetContext)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(root, "coil").absolutePath.toPath())
                    .maxSizeBytes(16L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        root.deleteRecursively()
    }

    @Test
    fun cachedOriginalIsSavedWithoutAnotherDownload() = runBlocking {
        val original = jpeg(0xFFAA3366.toInt())
        val prepared = File(root, "prepared.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }
        val wallpaper = wallpaper(prepared, "https://example.com/original.jpg")
        cache(requireNotNull(wallpaper.originalImageCacheKey()), original)

        val result = saver().save(wallpaper)

        assertEquals(GallerySaveResult.Saved, result)
        assertArrayEquals(original, writes.single().bytes)
        assertEquals("image/jpeg", writes.single().mimeType)
        assertTrue(writes.single().displayName.endsWith(".jpg"))
    }

    @Test
    fun missingCachedOriginalFailsInsteadOfDownloadingAgain() = runBlocking {
        val prepared = File(root, "prepared.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }

        val result = runCatching {
            saver().save(wallpaper(prepared, "https://example.com/original.jpg"))
        }

        assertTrue(result.isFailure)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun localWallpaperUsesItsPreparedImage() = runBlocking {
        val preparedBytes = jpeg(0xFF3366AA.toInt())
        val prepared = File(root, "prepared.wp").apply { writeBytes(preparedBytes) }

        val result = saver().save(wallpaper(prepared))

        assertEquals(GallerySaveResult.Saved, result)
        assertArrayEquals(preparedBytes, writes.single().bytes)
    }

    @Test
    fun repeatedSaveIsIdempotent() = runBlocking {
        val prepared = File(root, "prepared.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }
        val saver = saver()

        val first = saver.save(wallpaper(prepared))
        val second = saver.save(wallpaper(prepared))

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
        imageLoader = imageLoader,
        sink = GallerySink { source, mimeType, displayName ->
            if (!names.add(displayName)) {
                GallerySaveResult.AlreadySaved
            } else {
                writes += Write(source.readBytes(), mimeType, displayName)
                GallerySaveResult.Saved
            }
        },
    )

    private fun cache(key: String, bytes: ByteArray) {
        val editor = requireNotNull(imageLoader.diskCache).openEditor(key)
        requireNotNull(editor).run {
            data.toFile().writeBytes(bytes)
            commit()
        }
    }

    private fun wallpaper(prepared: File, imageUrl: String? = null) = SavedWallpaper(
        id = "wallpaper",
        metadata = WallpaperMetadata(imageUrl = imageUrl),
        prepared = prepared,
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
