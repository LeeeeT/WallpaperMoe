package dev.leeeet.wallpapermoe.source

import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two sources that fetch, and the download they share.
 *
 * Pandora forces its image URLs to https, so the shared [downloadOne] leg is
 * covered through the Wallhaven cases, which exercise it in full.
 */
class ProviderTest {

    private lateinit var env: Env

    @Before fun setUp() { env = Env() }

    @After fun tearDown() { env.close() }

    // -------------------------------------------------------------- wallhaven

    @Test
    fun wallhavenProducesAnImageShapedForTheScreen() {
        val sample = wallhaven("works").takeSample(env.ctx)
        val produced = sample.prepared

        assertEquals("works", produced.store)
        assertTrue(sample.originalUrl.endsWith("/img/works.jpg"))
        val bitmap = BitmapFactory.decodeFile(produced.path)
        assertEquals(Env.WIDTH, bitmap.width)
        assertEquals(Env.HEIGHT, bitmap.height)
    }

    @Test
    fun wallhavenSendsTheFiltersAsTheApiSpellsThem() {
        Wallhaven(
            query = "  forest  ",
            general = true, anime = false, people = true,
            sfw = true, sketchy = true, nsfw = false,
            sorting = WallhavenSorting.TOPLIST,
            apiKey = " key ",
            store = "filters",
        ).take(env.ctx)

        val sent = env.searchQueries.single()
        assertTrue(sent, sent.contains("q=forest"))
        assertTrue(sent, sent.contains("categories=101"))
        assertTrue(sent, sent.contains("purity=110"))
        assertTrue(sent, sent.contains("sorting=toplist"))
        assertTrue(sent, sent.contains("apikey=key"))
    }

    @Test
    fun wallhavenLeavesOutAnEmptySearchAndAnAbsentKey() {
        wallhaven(query = "   ", store = "empty-search").take(env.ctx)

        val sent = env.searchQueries.single()
        assertTrue(sent, !sent.contains("q="))
        assertTrue(sent, !sent.contains("apikey="))
    }

    @Test
    fun wallhavenSaysSoWhenTheSearchCannotBeReached() {
        val failure = wallhaven(Env.FAILS).sampleFailure(env.ctx)
        assertEquals(SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN), failure.failure)
    }

    @Test
    fun wallhavenSaysSoWhenNothingMatched() {
        assertEquals(
            SampleFailure.ProviderReturnedNothing(SourceProvider.WALLHAVEN),
            wallhaven(Env.EMPTY).sampleFailure(env.ctx).failure,
        )
    }

    @Test
    fun wallhavenCannotRepresentAnImpossibleSearch() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            wallhaven("x").copy(sfw = false, sketchy = false, nsfw = false)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            wallhaven("x").copy(general = false, anime = false, people = false)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            wallhaven("x").copy(nsfw = true, apiKey = "")
        }
        assertEquals(0, env.searchQueries.size)
    }

    // --------------------------------------------------------------- download

    @Test
    fun aCandidateThatIsGoneIsSkippedAndTheRestAreTried() {
        val failure = wallhaven(Env.GONE).sampleFailure(env.ctx)

        assertEquals(CANDIDATE_ATTEMPTS, env.downloads)
        assertEquals(
            SampleFailure.CandidatesUnavailable(SourceProvider.WALLHAVEN),
            failure.failure,
        )
    }

    @Test
    fun aCandidateThatMightWorkLaterEndsTheAttempt() {
        val failure = wallhaven(Env.FLAKY).sampleFailure(env.ctx)

        assertEquals(1, env.downloads)
        assertEquals(SampleFailure.DownloadFailed(SourceProvider.WALLHAVEN), failure.failure)
    }

    @Test
    fun onlyTheKeptSamplesStayOnDisk() {
        val source = wallhaven("works")
        repeat(5) { source.take(env.ctx) }

        val left = env.storage.dir("works").listFiles().orEmpty()
        assertEquals("older samples should be pruned", 2, left.size)
    }

    // ----------------------------------------------------------------- pandora

    @Test
    fun pandoraListsTheChannelsItCanUse() {
        val channels = runBlocking { env.ctx.providers.pandora.channels() }

        assertEquals(listOf("c1", "c2"), channels.map { it.id })
        assertEquals("Travel", channels[0].name)
        assertEquals(42, channels[0].subscribers)
        assertTrue(channels[0].recommended)
        assertTrue("a channel with no id is unusable", channels.none { it.name.contains("nameless") })
    }

    @Test
    fun pandoraTakesOnlyTheImagesFromTheFeed() {
        val images = runBlocking { env.ctx.providers.pandora.images(listOf("c1"), 60) }
        assertEquals(listOf("i1", "i3"), images.map { it.id })
    }

    @Test
    fun pandoraBuildsUrlsForTheScreenItIsAskedAbout() {
        val images = runBlocking { env.ctx.providers.pandora.images(listOf("c1"), 60) }

        assertEquals("https://cdn.example/webp/w1080/one.jpg", images[0].urlFor(1080))
        assertEquals("https://cdn.example/three.jpg", images[1].urlFor(1080))
    }

    @Test
    fun pandoraSendsNoIdentifyingContext() {
        runBlocking { env.ctx.providers.pandora.images(listOf("c1"), 60) }

        val sent = env.server.takeRequest().requestUrl!!
        assertEquals("test-install-id", sent.queryParameter("_devid"))
        assertEquals("c1", sent.queryParameter("channel_id"))
        listOf("imei", "mac", "oaid", "androidId", "device", "model").forEach {
            assertTrue("did not expect $it", sent.queryParameter(it) == null)
        }
    }
}
