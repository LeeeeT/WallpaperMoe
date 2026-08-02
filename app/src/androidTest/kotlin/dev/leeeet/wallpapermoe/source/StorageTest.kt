package dev.leeeet.wallpapermoe.source

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Stands in for the little state blobs sources keep in their folders. */
@Serializable
private data class ProbeState(val next: Int = 0, val names: List<String> = emptyList())

class StorageTest {

    private lateinit var root: File
    private lateinit var storage: Storage

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "storage-${System.nanoTime()}",
        )
        storage = Storage(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(dir: File, name: String, bytes: Int = 8) =
        File(dir, name).apply { writeBytes(ByteArray(bytes)) }

    private fun stored(dir: File, name: String, bytes: Int = 8): File {
        val source = write(storage.dir("input-${System.nanoTime()}"), "image.wp", bytes.coerceAtLeast(1))
        val adopted = runBlocking {
            adopt(
                WallpaperSample(name, source, WallpaperMetadata(imageUrl = "https://example.com/$name")),
                dir,
                "x",
            )
        }
        val entry = requireNotNull(adopted.prepared.parentFile)
        val named = File(dir, name)
        check(entry.renameTo(named))
        if (bytes == 0) File(named, "image.wp").writeBytes(ByteArray(0))
        return named
    }

    @Test
    fun handsOutAFolderPerDefinitionAndRuntimeStage() {
        val dir = storage.dir("abc/cache")
        assertTrue(dir.isDirectory)
        assertEquals(dir, storage.dir("abc/cache"))
    }

    @Test
    fun sweepKeepsLiveRuntimeStagesAndDropsTheRest() {
        write(storage.dir("kept/cache"), "a.wp")
        write(storage.dir("also-kept/prefetch"), "b.wp")
        write(storage.dir("orphan/cache"), "c.wp")

        runBlocking { storage.sweep(setOf("kept/cache", "also-kept/prefetch")) }

        assertTrue(File(root, "kept/cache").exists())
        assertTrue(File(root, "also-kept/prefetch").exists())
        assertFalse(File(root, "orphan").exists())
    }

    @Test
    fun sweepDropsAStageAsSoonAsItsOptionIsDisabled() {
        write(storage.dir("ready/cache"), "cached.wp")
        write(storage.dir("ready/prefetch"), "prepared.wp")

        runBlocking { storage.sweep(setOf("ready/prefetch")) }

        assertFalse(File(root, "ready/cache").exists())
        assertTrue(File(root, "ready/prefetch").exists())
    }

    @Test
    fun sweepWithNothingLeftTakesEverything() {
        write(storage.dir("one/cache"), "a.wp")
        write(storage.dir("two/prefetch"), "b.wp")

        runBlocking { storage.sweep(emptySet()) }

        assertEquals(emptyList<File>(), root.listFiles().orEmpty().toList())
    }

    @Test
    fun prepareDiscardsTheOldFileOnlySchemaOnce() = runBlocking {
        val old = write(storage.dir("old/cache"), "cached.wp")
        storage.prepare()
        assertFalse(old.exists())

        val current = write(storage.dir("current/cache"), "kept.wp")
        storage.prepare()
        assertTrue(current.exists())
    }

    @Test
    fun stateRoundTripsThroughTheFolder() {
        val dir = storage.dir("s")
        assertNull("no state yet reads as nothing", dir.loadState<ProbeState>())

        dir.saveState(ProbeState(next = 3, names = listOf("a.wp")))
        assertEquals(ProbeState(next = 3, names = listOf("a.wp")), dir.loadState<ProbeState>())
    }

    @Test
    fun corruptStateReadsAsNothing() {
        val dir = storage.dir("s")
        File(dir, "state.json").writeText("{ this is not json")
        assertNull(dir.loadState<ProbeState>())
    }

    @Test
    fun eachStoreNameIsItsOwn() {
        assertEquals(200, (1..200).map { newStoreName() }.toSet().size)
    }

    @Test
    fun sampleFilesDoNotCollide() {
        val dir = storage.dir("s")
        assertEquals(500, (1..500).map { dir.newSampleFile().name }.toSet().size)
    }

    @Test
    fun pruningKeepsTheNewestSamplesAndNothingElseIsTouched() {
        val dir = storage.dir("s")
        val samples = (1..5).map { i ->
            write(dir, "s-$i.wp").apply { setLastModified(1_000_000L + i * 1_000) }
        }
        val kept = write(dir, "c-cached.wp")

        dir.pruneSamples(keep = 2)

        assertTrue(samples[4].exists())
        assertTrue(samples[3].exists())
        assertFalse(samples[2].exists())
        assertFalse(samples[1].exists())
        assertFalse(samples[0].exists())
        assertTrue("only s- files are samples", kept.exists())
    }

    @Test
    fun pruningLeavesTooFewAlone() {
        val dir = storage.dir("s")
        write(dir, "s-1.wp")
        dir.pruneSamples(keep = 2)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun adoptingLeavesTheOriginalWhereItWas() {
        val from = storage.dir("from")
        val to = storage.dir("to")
        val produced = write(from, "s-1.wp", bytes = 16)

        val adopted = runBlocking {
            adopt(
                WallpaperSample(
                    "original",
                    produced,
                    WallpaperMetadata(imageUrl = "https://example.com/original.jpg"),
                ),
                to,
                "c",
            )
        }

        assertTrue(produced.exists())
        assertEquals(to, adopted.prepared.parentFile?.parentFile)
        assertTrue(adopted.prepared.parentFile!!.name.startsWith("c-"))
        assertEquals(16, adopted.prepared.length())
        assertEquals("original", adopted.id)
        assertEquals("https://example.com/original.jpg", adopted.metadata.imageUrl)
    }

    @Test
    fun liveNamesDropsWhatIsMissingOrEmpty() {
        val dir = storage.dir("s")
        stored(dir, "there")
        stored(dir, "empty", bytes = 0)

        assertEquals(
            listOf("there"),
            dir.liveNames(listOf("there", "empty", "never-existed")),
        )
    }

    @Test
    fun liveNamesKeepsTheOrderItWasGiven() {
        val dir = storage.dir("s")
        listOf("c", "a", "b").forEach { stored(dir, it) }
        assertEquals(listOf("c", "a", "b"), dir.liveNames(listOf("c", "a", "b")))
    }
}
