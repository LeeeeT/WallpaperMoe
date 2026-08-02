package dev.leeeet.wallpapermoe.wallpaper

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.source.WallpaperMetadata
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class GallerySaverTest {
    private lateinit var root: File
    private lateinit var server: MockWebServer
    private val writes = mutableListOf<Write>()

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "gallery-saver-${System.nanoTime()}",
        ).apply { mkdirs() }
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        root.deleteRecursively()
    }

    @Test
    fun originalImageIsPreferredAndUsesTheAppIdentity() = runBlocking {
        val original = jpeg(0xFFAA3366.toInt())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(okio.Buffer().write(original)),
        )
        val prepared = File(root, "prepared.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }

        saver().save(wallpaper(prepared, server.url("/original.jpg").toString()))

        assertArrayEquals(original, writes.single().bytes)
        assertEquals("image/jpeg", writes.single().mimeType)
        assertTrue(writes.single().displayName.endsWith(".jpg"))
        assertEquals(APP_USER_AGENT, server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun unavailableOriginalFallsBackToThePreparedImage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val preparedBytes = jpeg(0xFF3366AA.toInt())
        val prepared = File(root, "prepared.wp").apply { writeBytes(preparedBytes) }

        saver().save(wallpaper(prepared, server.url("/missing.jpg").toString()))

        assertArrayEquals(preparedBytes, writes.single().bytes)
    }

    @Test
    fun everyTapCreatesANewGalleryItem() = runBlocking {
        val prepared = File(root, "prepared.wp").apply { writeBytes(jpeg(0xFF3366AA.toInt())) }
        val saver = saver()

        saver.save(wallpaper(prepared))
        saver.save(wallpaper(prepared))

        assertEquals(2, writes.size)
        assertNotEquals(writes[0].displayName, writes[1].displayName)
    }

    private fun saver() = GallerySaver(
        scratchDir = File(root, "scratch"),
        sink = GallerySink { source, mimeType, displayName ->
            writes += Write(source.readBytes(), mimeType, displayName)
        },
        client = OkHttpClient(),
    )

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
