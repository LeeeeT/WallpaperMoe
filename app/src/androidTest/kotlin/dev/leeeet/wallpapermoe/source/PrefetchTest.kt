package dev.leeeet.wallpapermoe.source

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrefetchTest {

    private lateinit var env: Env

    @Before fun setUp() { env = Env() }

    @After fun tearDown() { env.close() }

    /** The buffer is the `r-` entries in the store folder; there is no other record of it. */
    private fun buffered(store: String) = env.storage.dir(store).listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("r-") }

    @Test
    fun paysForTheFirstSampleInlineThenGetsAhead() {
        val prefetch = prefetch(source = wallhaven("one"), depth = 2, store = "p")
        val first = prefetch.take(env.ctx)
        assertEquals("p", first.store)
        assertTrue(first.exists())

        awaitUntil("the buffer to fill") { buffered("p").size == 2 }
    }

    /** The buffer lives on disk, so a fresh instance serves it without touching its source. */
    @Test
    fun servesFromTheBufferWithoutTouchingItsSource() {
        val warm = prefetch(source = wallhaven("one"), depth = 2, store = "p")
        warm.take(env.ctx)
        awaitUntil("the buffer to fill") { buffered("p").size == 2 }

        val offline = prefetch(source = wallhaven(Env.FAILS), depth = 2, store = "p")
        val served = offline.take(env.ctx)

        assertEquals("p", served.store)
        assertTrue(served.exists())
        assertTrue(served.length() > 0)
        assertEquals("one image should have come off the front", 1, buffered("p").size)
    }

    @Test
    fun bufferedSamplesKeepTheirOriginalUrl() {
        val warm = prefetch(source = wallhaven("one"), depth = 1, store = "with-url")
        warm.take(env.ctx)
        awaitUntil("the URL-bearing buffer to fill") { buffered("with-url").size == 1 }

        val offline = prefetch(source = wallhaven(Env.FAILS), depth = 1, store = "with-url")
        assertTrue(offline.takeSample(env.ctx).originalUrl.endsWith("/img/one.jpg"))
    }

    @Test
    fun doesNotGetFurtherAheadThanItsDepth() {
        val prefetch = prefetch(source = wallhaven("one"), depth = 3, store = "p")
        prefetch.take(env.ctx)
        awaitUntil("the buffer to fill") { buffered("p").size == 3 }

        Thread.sleep(500)
        assertEquals(3, buffered("p").size)
    }

    @Test
    fun aFailedRefillStopsRatherThanSpinning() {
        val prefetch = prefetch(source = wallhaven(Env.FAILS), depth = 3, store = "p")
        prefetch.sampleFailure(env.ctx)

        Thread.sleep(1_000)
        val settled = env.searches(Env.FAILS)
        Thread.sleep(1_000)
        assertEquals("the background refill kept going", settled, env.searches(Env.FAILS))
        assertTrue("expected a handful of attempts, got $settled", settled <= 3)
    }

    @Test
    fun anEmptiedBufferSendsItBackToItsSource() {
        val warm = prefetch(source = wallhaven("one"), depth = 2, store = "p")
        warm.take(env.ctx)
        awaitUntil("the buffer to fill") { buffered("p").size == 2 }

        buffered("p").forEach { it.deleteRecursively() }

        val cold = prefetch(source = wallhaven("two"), depth = 2, store = "p")
        val served = cold.take(env.ctx)

        assertEquals("p", served.store)
        assertTrue(served.length() > 0)
        assertTrue("an empty buffer should send it to its source", env.searches("two") >= 1)
    }
}
