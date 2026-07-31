package dev.leeeet.wallpapermoe.wallpaper

import androidx.test.platform.app.InstrumentationRegistry
import dev.leeeet.wallpapermoe.source.WallpaperSample
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class WallpaperArchiveTest {

    private lateinit var root: File
    private lateinit var archive: WallpaperArchive

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "archive-${System.nanoTime()}",
        )
        archive = WallpaperArchive(root)
        runBlocking { archive.load() }
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun sample(number: Int): WallpaperSample {
        val file = File(root, "incoming-$number.wp").apply { writeText("image-$number") }
        return WallpaperSample(file, "https://example.com/$number.jpg")
    }

    @Test
    fun historyKeepsTheNewestTwentyAndDeduplicatesByUrl() = runBlocking {
        (1..21).forEach { archive.rememberApplied(sample(it)) }
        assertEquals((21 downTo 2).map { "https://example.com/$it.jpg" }, archive.history.value.map { it.originalUrl })

        archive.rememberApplied(sample(7))
        assertEquals("https://example.com/7.jpg", archive.history.value.first().originalUrl)
        assertEquals(20, archive.history.value.size)
    }

    @Test
    fun favoriteOwnsItsCopyAndSurvivesReloading() = runBlocking {
        archive.rememberApplied(sample(1))
        val historyItem = archive.history.value.single()
        archive.addFavorite(historyItem)
        historyItem.prepared.delete()

        val reloaded = WallpaperArchive(root)
        reloaded.load()
        val favorite = reloaded.favorites.value.single()
        assertTrue(favorite.prepared.exists())
        assertEquals("image-1", favorite.prepared.readText())

        reloaded.removeFavorite(favorite.originalUrl)
        assertFalse(favorite.prepared.exists())
        assertTrue(reloaded.favorites.value.isEmpty())
    }

    @Test
    fun pendingRemovalsCanBeCommittedTogether() = runBlocking {
        (1..3).forEach {
            archive.rememberApplied(sample(it))
            archive.addFavorite(archive.history.value.first())
        }
        archive.removeFavorites(
            setOf("https://example.com/1.jpg", "https://example.com/3.jpg"),
        )

        assertEquals(
            listOf("https://example.com/2.jpg"),
            archive.favorites.value.map { it.originalUrl },
        )
    }
}
