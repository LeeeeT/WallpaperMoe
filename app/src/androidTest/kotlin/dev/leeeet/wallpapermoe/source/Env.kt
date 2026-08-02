package dev.leeeet.wallpapermoe.source

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import dev.leeeet.wallpapermoe.image.ImagePipeline
import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestApi
import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestDownloader
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.provider.wallhaven.WallhavenApi
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

/**
 * One test's environment: a server answering as both providers, a storage root
 * under the app's cache, and a [SampleContext] wired to them.
 *
 * [Source] is sealed, so the sources under test are the real ones — a failing
 * source is a real [Wallhaven] the server refuses to answer.
 */
class Env(nekosBestIntervalMs: Long = 0L) {

    private val root = File(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "test-${System.nanoTime()}",
    )

    private val routes = Routes()
    val server = MockWebServer()

    val storage = Storage(File(root, "sources"))
    val images = ImagePipeline(File(root, "scratch"))
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val archive = WallpaperArchive(File(root, "archive"))

    val ctx: SampleContext

    init {
        server.dispatcher = routes
        server.start()
        routes.base = server.url("/").toString().trimEnd('/')

        ctx = SampleContext(
            widthPx = WIDTH,
            heightPx = HEIGHT,
            images = images,
            providers = Providers(
                nekosBest = NekosBestDownloader(
                    api = NekosBestApi("${routes.base}/nekos"),
                    minimumIntervalMs = nekosBestIntervalMs,
                ),
                pandora = PandoraApi(
                    installId = { "test-install-id" },
                    screenWidthPx = WIDTH,
                    locale = "en_US",
                    appVersionName = "test",
                    appVersionCode = 1,
                    baseUrl = routes.base,
                ),
                wallhaven = WallhavenApi("${routes.base}/wh"),
            ),
            storage = storage,
            archive = archive,
            background = background,
        )
        runBlocking { archive.load() }
    }

    /** The state file a source keeps in its store folder. */
    fun stateFile(store: String): File = File(storage.dir(store), "state.json")

    /** A field of the store's state, read the way the source would. */
    fun stateField(store: String, field: String) =
        documentJson.parseToJsonElement(stateFile(store).readText()).jsonObject[field]

    fun cacheEntries(store: String): List<String> =
        stateField(store, "entries")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

    fun searches(query: String): Int = routes.searches[query]?.get() ?: 0

    /** Every query the server received, in order, as sent on the wire. */
    val searchQueries: List<String> get() = routes.queries.toList()

    val downloads: Int get() = routes.downloads.get()

    val nekosBestRequests: List<RecordedRequest> get() = routes.nekosBestRequests.toList()
    val nekosBestRequestTimes: List<Pair<String, Long>>
        get() = routes.nekosBestRequestTimes.toList()

    fun close() {
        server.shutdown()
        background.cancel()
        root.deleteRecursively()
    }

    /** The [Wallhaven.query] selects the response, so sources with different ones can be composed. */
    private class Routes : Dispatcher() {
        lateinit var base: String
        val searches = ConcurrentHashMap<String, AtomicInteger>()
        val queries = CopyOnWriteArrayList<String>()
        val downloads = AtomicInteger()
        val nekosBestRequests = CopyOnWriteArrayList<RecordedRequest>()
        val nekosBestRequestTimes = CopyOnWriteArrayList<Pair<String, Long>>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
            val path = url.encodedPath
            return when {
                path == "/nekos/endpoints" -> {
                    nekosBestRequests += request
                    json(NEKOS_ENDPOINTS)
                }
                path.startsWith("/nekos/") -> {
                    nekosBestRequests += request
                    nekosBestRequestTimes += path to SystemClock.elapsedRealtime()
                    nekos(path.substringAfterLast('/'), url.queryParameter("amount")?.toIntOrNull() ?: 1)
                }
                path.endsWith("/search") -> {
                    queries += url.encodedQuery.orEmpty()
                    search(url.queryParameter("q").orEmpty())
                }
                path.startsWith("/img/") -> image(path)
                path.endsWith("/lock_view") -> json(LOCK_VIEW)
                path.endsWith("/index") -> json(CHANNEL_INDEX)
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun search(query: String): MockResponse {
            searches.getOrPut(query) { AtomicInteger() }.incrementAndGet()
            return when (query) {
                FAILS -> MockResponse().setResponseCode(500)
                EMPTY -> json("""{"data":[]}""")
                MISSING -> json(results(listOf("missing")))
                else -> json(results(listOf(query.ifEmpty { "anything" })))
            }
        }

        private fun image(path: String): MockResponse {
            downloads.incrementAndGet()
            return when {
                path.contains("missing") -> MockResponse().setResponseCode(404)
                else -> MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody(Buffer().write(jpeg()))
            }
        }

        private fun nekos(category: String, amount: Int): MockResponse = when (category) {
            FAILS -> MockResponse().setResponseCode(500)
            EMPTY -> json("""{"results":[]}""")
            else -> json(
                (1..amount).joinToString(",", prefix = """{"results":[""", postfix = "]}") { index ->
                    """{"url":"$base/img/nekos-$category-$index.jpg","source_url":"https://art.example/$category-$index","artist_name":"Artist $index","artist_href":"https://artist.example/$index"}"""
                },
            )
        }

        private fun results(names: List<String>) = names
            .mapIndexed { i, name -> """{"id":"id-$i","path":"$base/img/$name.jpg"}""" }
            .joinToString(",", prefix = """{"data":[""", postfix = "]}")

        private fun json(body: String) = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400

        /** One downloadable image, which is also the default for any other query. */
        const val WORKS = "works"

        /** The search itself returns HTTP 500. */
        const val FAILS = "fails"

        /** The search succeeds with an empty result set. */
        const val EMPTY = "empty"

        /** The search succeeds, but its one image is unavailable. */
        const val MISSING = "missing"

        /** A real JPEG, so the decode under test is real. */
        fun jpeg(width: Int = 640, height: Int = 1280): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF3366AA.toInt())
            return ByteArrayOutputStream().also {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                bitmap.recycle()
            }.toByteArray()
        }

        const val LOCK_VIEW = """
            {"items":[
              {"id":"i1","item_type":"image","images":[
                {"cl_url":{"locator":"one.jpg","url_root":"https://cdn.example/"}}]},
              {"id":"i2","item_type":"video","images":[
                {"cl_url":{"locator":"two.jpg","url_root":"https://cdn.example/"}}]},
              {"id":"i3","item_type":"image","images":[
                {"cl_url":{"url_full":"http://cdn.example/three.jpg"}}]}
            ]}
        """

        const val CHANNEL_INDEX = """
            {"items":[
              {"tagId":"c1","name":"Travel","def_sub":1,"subscribed_num":42,
               "cl_url":{"locator":"cover.jpg","url_root":"https://cdn.example/"}},
              {"tagId":"c2","name":"Life"},
              {"name":"nameless, so unusable"}
            ]}
        """

        const val NEKOS_ENDPOINTS = """
            {
              "waifu":{"format":"png"},
              "hug":{"format":"gif"},
              "husbando":{"format":"png"},
              "neko":{"format":"png"},
              "kitsune":{"format":"png"}
            }
        """
    }
}

fun wallhaven(
    query: String = "",
    store: String = query,
    aspects: Set<WallhavenAspect> = WallhavenAspect.ALL,
) = Wallhaven(
    query = query,
    general = true,
    anime = false,
    people = false,
    sfw = true,
    sketchy = false,
    nsfw = false,
    aspects = aspects,
    sorting = WallhavenSorting.RANDOM,
    apiKey = "",
    store = store,
)

fun newStoreName(): String = UUID.randomUUID().toString()

fun pandora(
    channelIds: List<String>,
    store: String = newStoreName(),
) = Pandora(channelIds, store)

fun nekosBest(
    categories: List<String>,
    mode: SelectionMode = SelectionMode.RANDOM,
    store: String = newStoreName(),
) = NekosBest(categories, mode, store)

fun roundRobin(sources: List<Source>, store: String = newStoreName()) = RoundRobin(sources, store)

fun cache(
    source: Source,
    capacity: Int = 20,
    refreshHours: Int = 24,
    store: String = newStoreName(),
) = Cache(source, capacity, refreshHours, store)

fun prefetch(source: Source, depth: Int = 3, store: String = newStoreName()) =
    Prefetch(source, depth, store)

fun Source.sampleFailure(ctx: SampleContext): SampleException {
    val thrown = runBlocking {
        try {
            sample(ctx)
            null
        } catch (e: SampleException) {
            e
        }
    }
    return thrown ?: throw AssertionError("expected this sample to fail, but it produced an image")
}

fun Source.take(ctx: SampleContext): File = runBlocking { sample(ctx).prepared }

fun Source.takeSample(ctx: SampleContext): WallpaperSample = runBlocking { sample(ctx) }

/** Waits for work started in [SampleContext.background] to land. */
fun awaitUntil(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(25)
    }
    throw AssertionError("$what did not happen within ${timeoutMs}ms")
}

/** The store a sampled file came out of, which is how a test tells sources apart. */
val File.store: String get() = parentFile!!.let { parent ->
    if (parent.name.startsWith("c-") || parent.name.startsWith("s-") || parent.name.startsWith("r-")) {
        parent.parentFile!!.name
    } else {
        parent.name
    }
}
