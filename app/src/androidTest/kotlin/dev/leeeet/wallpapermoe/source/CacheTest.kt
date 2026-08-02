package dev.leeeet.wallpapermoe.source

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

class CacheTest {

    private lateinit var env: Env

    @Before fun setUp() { env = Env() }

    @After fun tearDown() { env.close() }

    private fun files(cache: Cache) = env.storage.dir(cache.store)
        .listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("c-") }

    private fun entries(cache: Cache) = env.cacheEntries(cache.store)

    /** Rewrites the store's state as if the last addition were long ago. */
    private fun makeAdditionDue(cache: Cache) {
        val file = env.stateFile(cache.store)
        file.writeText(file.readText().replace(Regex("\"lastAddedAt\":\\d+"), "\"lastAddedAt\":0"))
    }

    @Test
    fun fillsOneImagePerSample() {
        val cache = cache(source = wallhaven("one"), capacity = 5, refreshHours = 0)
        repeat(3) { cache.take(env.ctx) }
        assertEquals(3, entries(cache).size)
        assertEquals(3, env.searches("one"))
    }

    @Test
    fun neverRefreshFillsThenOnlyServesFromDisk() {
        val cache = cache(source = wallhaven("one"), capacity = 2, refreshHours = 0)
        cache.take(env.ctx)
        cache.take(env.ctx)
        assertEquals(2, env.searches("one"))

        val served = cache.take(env.ctx)
        assertEquals("a full cache must not go back to its source", 2, env.searches("one"))
        assertTrue(served.exists())
        assertTrue(served.parentFile!!.name in entries(cache))
    }

    @Test
    fun worksThroughWhatItHoldsRatherThanRepeatingOne() {
        val cache = cache(source = wallhaven("one"), capacity = 2, refreshHours = 0)
        cache.take(env.ctx)
        cache.take(env.ctx)
        val served = (1..4).map { cache.take(env.ctx).parentFile!!.name }
        assertEquals(2, served.toSet().size)
        assertEquals(served[0], served[2])
        assertEquals(served[1], served[3])
    }

    @Test
    fun dropsTheOldestWhenAnAdditionWouldPassCapacity() {
        val cache = cache(source = wallhaven("one"), capacity = 2, refreshHours = 1)
        cache.take(env.ctx)
        cache.take(env.ctx)
        val oldest = File(env.storage.dir(cache.store), entries(cache).first())
        assertTrue(oldest.exists())

        makeAdditionDue(cache)
        cache.take(env.ctx)

        assertEquals(2, entries(cache).size)
        assertFalse("the evicted image should be off the disk too", oldest.exists())
        assertEquals(2, files(cache).size)
    }

    /** The contents live in the store folder, so a fresh instance serves what the last one kept. */
    @Test
    fun servesWhatItHoldsWhenItsSourceCannotBeReached() {
        val filled = cache(source = wallhaven("one"), capacity = 3, store = "shared")
        filled.take(env.ctx)

        val offline = cache(source = wallhaven(Env.FAILS), capacity = 3, store = "shared")
        val served = offline.take(env.ctx)
        assertEquals("shared", served.store)
        assertTrue(served.exists())
    }

    @Test
    fun keepsIdentityAndMetadataWithTheDownloadedImage() {
        val filled = cache(source = wallhaven("one"), capacity = 1, store = "with-metadata")
        val original = filled.takeSample(env.ctx)

        val offline = cache(source = wallhaven(Env.FAILS), capacity = 1, store = "with-metadata")
        val restored = offline.takeSample(env.ctx)
        assertEquals(original.id, restored.id)
        assertEquals(original.metadata, restored.metadata)
    }

    @Test
    fun failsWhenItHoldsNothingAndCannotFill() {
        val cache = cache(source = wallhaven(Env.FAILS))
        assertEquals(
            SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN),
            cache.sampleFailure(env.ctx).failure,
        )
    }

    @Test
    fun rejectsStateThatReferencesAMissingEntry() {
        val cache = cache(source = wallhaven("one"), capacity = 4, refreshHours = 0)
        cache.take(env.ctx)
        cache.take(env.ctx)
        File(env.storage.dir(cache.store), entries(cache).first()).deleteRecursively()

        assertThrows(IllegalStateException::class.java) { cache.take(env.ctx) }
    }

    @Test
    fun keepsTheProviderDimensions() {
        val cache = cache(source = wallhaven("one"), capacity = 2, refreshHours = 0)
        val bitmap = android.graphics.BitmapFactory.decodeFile(cache.take(env.ctx).path)
        assertEquals(640, bitmap.width)
        assertEquals(1280, bitmap.height)
    }
}
