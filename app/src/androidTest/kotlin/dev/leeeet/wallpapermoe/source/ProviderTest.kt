package dev.leeeet.wallpapermoe.source

import android.graphics.BitmapFactory
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two sources that fetch, and the download they share.
 *
 * Pandora forces its image URLs to https, so the shared [download] leg is
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
        assertTrue(sample.metadata.imageUrl!!.endsWith("/img/works.jpg"))
        val bitmap = BitmapFactory.decodeFile(produced.path)
        assertEquals(Env.WIDTH, bitmap.width)
        assertEquals(Env.HEIGHT, bitmap.height)
    }

    @Test
    fun providerIdentityStaysStableWhenTheSameWallpaperReturnsAgain() {
        val source = wallhaven("works")
        assertEquals(source.takeSample(env.ctx).id, source.takeSample(env.ctx).id)
    }

    @Test
    fun wallhavenSendsTheFiltersAsTheApiSpellsThem() {
        Wallhaven(
            query = "  forest  ",
            general = true, anime = false, people = true,
            sfw = true, sketchy = true, nsfw = false,
            aspects = setOf(WallhavenAspect.PORTRAIT),
            sorting = WallhavenSorting.TOPLIST,
            apiKey = " key ",
            store = "filters",
        ).take(env.ctx)

        val sent = env.searchQueries.single()
        assertTrue(sent, sent.contains("q=forest"))
        assertTrue(sent, sent.contains("categories=101"))
        assertTrue(sent, sent.contains("purity=110"))
        assertTrue(sent, sent.contains("ratios=portrait"))
        assertTrue(sent, sent.contains("sorting=toplist"))
        assertTrue(sent, sent.contains("apikey=key"))
    }

    @Test
    fun wallhavenLeavesOutAnEmptySearchAndAnAbsentKey() {
        wallhaven(query = "   ", store = "empty-search").take(env.ctx)

        val sent = env.searchQueries.single()
        assertTrue(sent, !sent.contains("q="))
        assertTrue(sent, !sent.contains("apikey="))
        assertTrue(sent, !sent.contains("ratios="))
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
            wallhaven("x").copy(aspects = emptySet())
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            wallhaven("x").copy(nsfw = true, apiKey = "")
        }
        assertEquals(0, env.searchQueries.size)
    }

    // --------------------------------------------------------------- download

    @Test
    fun aFailedImageEndsTheSampleAfterOneDownload() {
        val failure = wallhaven(Env.MISSING).sampleFailure(env.ctx)

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

    // ------------------------------------------------------------ nekos.best

    @Test
    fun nekosBestDiscoversOnlyStaticImageCategories() = runBlocking {
        assertEquals(
            listOf("husbando", "kitsune", "neko", "waifu"),
            env.ctx.providers.nekosBest.imageCategories(),
        )
    }

    @Test
    fun nekosBestIdentifiesTheAppExactlyAsRequired() = runBlocking {
        env.ctx.providers.nekosBest.imageCategories()

        assertEquals(APP_USER_AGENT, env.nekosBestRequests.single().getHeader("User-Agent"))
    }

    @Test
    fun nekosBestKeepsEveryAvailableAttributionField() {
        val sample = nekosBest(listOf("neko")).takeSample(env.ctx)

        assertTrue(sample.metadata.imageUrl!!.endsWith("/img/nekos-neko-1.jpg"))
        assertEquals("https://art.example/neko-1", sample.metadata.sourceUrl)
        assertEquals("Artist 1", sample.metadata.artistName)
        assertEquals("https://artist.example/1", sample.metadata.artistUrl)
        assertEquals("1", env.nekosBestRequests.single().requestUrl!!.queryParameter("amount"))
    }

    @Test
    fun nekosBestRoundRobinUsesEveryCategoryAndRemembersItsPlace() {
        nekosBest(
            listOf("neko", "waifu"),
            SelectionMode.ROUND_ROBIN,
            "nekos-round-robin",
        ).take(env.ctx)
        val restarted = nekosBest(
            listOf("neko", "waifu"),
            SelectionMode.ROUND_ROBIN,
            "nekos-round-robin",
        )
        restarted.take(env.ctx)
        restarted.take(env.ctx)

        assertEquals(
            listOf("/nekos/neko", "/nekos/waifu", "/nekos/neko"),
            env.nekosBestRequests.map { it.requestUrl!!.encodedPath },
        )
    }

    @Test
    fun nekosBestQueuesConcurrentAcquisitionsAtThePerCategoryRate() {
        env.close()
        val intervalMs = 2_000L
        env = Env(nekosBestIntervalMs = intervalMs)

        runBlocking {
            coroutineScope {
                listOf("queued-a", "queued-b").map { store ->
                    async { nekosBest(listOf("neko"), store = store).sample(env.ctx) }
                }.awaitAll()
            }
        }

        val starts = env.nekosBestRequestTimes.map { it.second }
        assertEquals(2, starts.size)
        assertTrue(
            "requests started only ${starts[1] - starts[0]} ms apart",
            starts[1] - starts[0] >= 1_900L,
        )
    }

    @Test
    fun cancellingAQueuedNekosBestAcquisitionRemovesItBeforeTheRequest() = runBlocking {
        env.close()
        env = Env(nekosBestIntervalMs = 10_000L)
        nekosBest(listOf("neko"), store = "first").sample(env.ctx)

        val queued = launch {
            nekosBest(listOf("neko"), store = "cancelled").sample(env.ctx)
        }
        delay(100)
        queued.cancelAndJoin()

        assertEquals(1, env.nekosBestRequestTimes.size)
    }

    @Test
    fun nekosBestReportsProviderFailures() {
        assertEquals(
            SampleFailure.ProviderUnavailable(SourceProvider.NEKOS_BEST),
            nekosBest(listOf(Env.FAILS)).sampleFailure(env.ctx).failure,
        )
        assertEquals(
            SampleFailure.ProviderReturnedNothing(SourceProvider.NEKOS_BEST),
            nekosBest(listOf(Env.EMPTY)).sampleFailure(env.ctx).failure,
        )
    }

    // ----------------------------------------------------------------- pandora

    @Test
    fun pandoraListsTheChannelsItCanUse() {
        val channels = runBlocking { env.ctx.providers.pandora.channels() }

        assertEquals(listOf("c1", "c2"), channels.map { it.id })
        assertEquals("Travel", channels[0].name)
        assertEquals(42, channels[0].subscribers)
        assertTrue("a channel with no id is unusable", channels.none { it.name.contains("nameless") })
    }

    @Test
    fun pandoraReturnsOneUsableImage() {
        val image = runBlocking { env.ctx.providers.pandora.image(listOf("c1")) }
        assertEquals("i1", image!!.id)
    }

    @Test
    fun pandoraBuildsUrlsForTheScreenItIsAskedAbout() {
        val image = runBlocking { env.ctx.providers.pandora.image(listOf("c1")) }

        assertEquals("https://cdn.example/webp/w1080/one.jpg", image!!.urlFor(1080))
    }

    @Test
    fun pandoraSendsNoIdentifyingContext() {
        runBlocking { env.ctx.providers.pandora.image(listOf("c1")) }

        val sent = env.server.takeRequest().requestUrl!!
        assertEquals("test-install-id", sent.queryParameter("_devid"))
        assertEquals("c1", sent.queryParameter("channel_id"))
        assertEquals("1", sent.queryParameter("page_size"))
        listOf("imei", "mac", "oaid", "androidId", "device", "model").forEach {
            assertTrue("did not expect $it", sent.queryParameter(it) == null)
        }
    }
}
