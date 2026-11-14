package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.BuildConfig
import dev.leeeet.wallpapermoe.model.Candidate
import dev.leeeet.wallpapermoe.model.Channel
import dev.leeeet.wallpapermoe.model.Recipe
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.RequestGate
import kotlin.random.Random
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Wallhaven(private val http: Http) {
    data class Page(val candidates: List<Candidate>, val lastPage: Int)

    private val gate = RequestGate(60_000L / 45)

    suspend fun search(recipe: Recipe.Wallhaven, key: String, page: Int): Page {
        val authenticated = recipe.nsfw && key.isNotBlank()
        if (!recipe.sfw && !recipe.sketchy && !authenticated) return Page(emptyList(), 1)
        val url = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder().apply {
            if (recipe.query.isNotBlank()) addQueryParameter("q", recipe.query.trim())
            addQueryParameter("categories", bits(recipe.general, recipe.anime, recipe.people))
            addQueryParameter("purity", bits(recipe.sfw, recipe.sketchy, authenticated))
            if (recipe.portrait != recipe.landscape) {
                addQueryParameter("ratios", if (recipe.portrait) "portrait" else "landscape")
            }
            addQueryParameter("sorting", recipe.sorting.apiValue)
            addQueryParameter("page", page.toString())
        }.build()
        gate.await()
        val body = http.text(url, if (authenticated) mapOf("X-API-Key" to key.trim()) else emptyMap())
        val response = http.json.decodeFromString<SearchResponse>(body)
        val candidates = response.data.mapNotNull { item ->
            val id = item.id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = item.path?.toHttpUrlOrNull() ?: return@mapNotNull null
            Candidate(id, path.toString(), item.url?.toHttpUrlOrNull()?.toString())
        }
        return Page(candidates, response.meta?.lastPage ?: 1)
    }

    private fun bits(vararg flags: Boolean) = flags.joinToString("") { if (it) "1" else "0" }

    @Serializable
    private data class SearchResponse(val data: List<Item> = emptyList(), val meta: Meta? = null)

    @Serializable
    private data class Item(val id: String? = null, val url: String? = null, val path: String? = null)

    @Serializable
    private data class Meta(@SerialName("last_page") val lastPage: Int = 1)
}

class NekosBest(private val http: Http) {
    data class Art(val url: String, val pageUrl: String?, val artist: String?, val artistUrl: String?)

    private val gate = RequestGate(60_000L / 200)

    @Volatile
    private var categories: List<String>? = null

    suspend fun categories(): List<String> = categories ?: run {
        val body = http.text("$BASE/endpoints".toHttpUrl())
        http.json.parseToJsonElement(body).jsonObject
            .filter { (name, value) ->
                NAME.matches(name) && value.jsonObject["format"]?.jsonPrimitive?.content.equals("png", ignoreCase = true)
            }
            .keys.sorted()
            .also { categories = it }
    }

    suspend fun art(category: String): Art? {
        require(NAME.matches(category))
        gate.await()
        val body = http.text("$BASE/$category?amount=1".toHttpUrl())
        return http.json.decodeFromString<Response>(body).results.firstNotNullOfOrNull { item ->
            val url = item.url.webUrl() ?: return@firstNotNullOfOrNull null
            Art(url, item.sourceUrl.webUrl(), item.artistName?.trim()?.takeIf(String::isNotEmpty), item.artistUrl.webUrl())
        }
    }

    private fun String?.webUrl(): String? = this?.trim()?.toHttpUrlOrNull()?.toString()

    @Serializable
    private data class Response(val results: List<Item> = emptyList())

    @Serializable
    private data class Item(
        val url: String? = null,
        @SerialName("source_url") val sourceUrl: String? = null,
        @SerialName("artist_name") val artistName: String? = null,
        @SerialName("artist_href") val artistUrl: String? = null,
    )

    private companion object {
        const val BASE = "https://nekos.best/api/v2"
        val NAME = Regex("[a-z0-9_-]+")
    }
}

class Pandora(
    private val http: Http,
    private val installId: suspend () -> String,
    private val screenWidth: () -> Int,
    private val locale: () -> String,
) {
    data class Listing(val id: String, val name: String, val subscribers: Int?)

    @Volatile
    private var channels: List<Listing>? = null

    suspend fun channels(): List<Listing> = channels ?: run {
        val body = get("/api/a1/gallery/index", emptyMap(), screenWidth())
        http.json.decodeFromString<Index>(body).items.mapNotNull { item ->
            val id = item.tagId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val name = item.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            Listing(id, name, item.subscribers)
        }.also { channels = it }
    }

    suspend fun image(channels: List<Channel>): Candidate? {
        val width = screenWidth()
        val parameters = mapOf("page_size" to "1", "channel_id" to channels.joinToString(",", transform = Channel::id))
        val body = get("/api/a1/lock/lock_view", parameters, width)
        return http.json.decodeFromString<LockView>(body).items.firstNotNullOfOrNull { item ->
            val id = item.id?.takeIf(String::isNotBlank) ?: return@firstNotNullOfOrNull null
            if (item.type != "image") return@firstNotNullOfOrNull null
            item.images.firstNotNullOfOrNull { it.location?.url(width) }?.let { Candidate(id, it) }
        }
    }

    private suspend fun get(path: String, parameters: Map<String, String>, width: Int): String {
        val url = "https://w.pandora.xiaomi.com$path".toHttpUrl().newBuilder().apply {
            parameters.forEach(::addQueryParameter)
            addQueryParameter("_vcode", BuildConfig.VERSION_CODE.toString())
            addQueryParameter("_vname", BuildConfig.VERSION_NAME)
            addQueryParameter("_fver", "20160831")
            addQueryParameter("_locale", locale())
            addQueryParameter("_res", width.toString())
            addQueryParameter("_devid", installId())
            addQueryParameter("_nonce", Random.nextInt().toString())
            addQueryParameter("_ts", (System.currentTimeMillis() / 1000).toString())
        }.build()
        return http.text(url)
    }

    private fun Location.url(width: Int): String? {
        val root = root?.takeIf(String::isNotBlank)?.replaceFirst("http://", "https://") ?: return null
        val locator = locator?.takeIf(String::isNotBlank) ?: return null
        return "${root}webp/w$width/$locator".toHttpUrlOrNull()?.toString()
    }

    @Serializable
    private data class Index(val items: List<IndexItem> = emptyList())

    @Serializable
    private data class IndexItem(
        val tagId: String? = null,
        val name: String? = null,
        @SerialName("subscribed_num") val subscribers: Int? = null,
    )

    @Serializable
    private data class LockView(val items: List<LockItem> = emptyList())

    @Serializable
    private data class LockItem(
        val id: String? = null,
        @SerialName("item_type") val type: String? = null,
        val images: List<LockImage> = emptyList(),
    )

    @Serializable
    private data class LockImage(@SerialName("cl_url") val location: Location? = null)

    @Serializable
    private data class Location(val locator: String? = null, @SerialName("url_root") val root: String? = null)
}
