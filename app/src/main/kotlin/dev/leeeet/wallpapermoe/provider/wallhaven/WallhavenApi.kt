package dev.leeeet.wallpapermoe.provider.wallhaven

import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.getString
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** wallhaven.cc's public search API. Anonymous except for NSFW, and limited to 45 requests a minute. */
class WallhavenApi(private val baseUrl: String = "https://wallhaven.cc/api/v1") {

    data class Image(val id: String, val url: String)

    data class Query(
        val text: String = "",
        val general: Boolean = true,
        val anime: Boolean = false,
        val people: Boolean = false,
        val sfw: Boolean = true,
        val sketchy: Boolean = false,
        val nsfw: Boolean = false,
        val sorting: String = "random",
        val apiKey: String = "",
    )

    suspend fun image(q: Query): Image? {
        val params = buildMap {
            if (q.text.isNotBlank()) put("q", q.text.trim())
            put("categories", flags(q.general, q.anime, q.people))
            put("purity", flags(q.sfw, q.sketchy, q.nsfw))
            put("sorting", q.sorting)
            if (q.apiKey.isNotBlank()) put("apikey", q.apiKey.trim())
        }
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val body = Http.client.getString("$baseUrl/search?$query", APP_USER_AGENT)
        return Http.json.decodeFromString<SearchResponse>(body).data.firstNotNullOfOrNull {
            val id = it.id?.takeIf(String::isNotBlank) ?: return@firstNotNullOfOrNull null
            val path = it.path?.takeIf(String::isNotBlank) ?: return@firstNotNullOfOrNull null
            Image(id, path)
        }
    }

    /** Category and purity filters go on the wire as positional bitstrings. */
    private fun flags(a: Boolean, b: Boolean, c: Boolean): String =
        "${a.bit()}${b.bit()}${c.bit()}"

    private fun Boolean.bit() = if (this) "1" else "0"
}

@Serializable
private data class SearchResponse(val data: List<SearchItem> = emptyList())

@Serializable
private data class SearchItem(
    val id: String? = null,
    /** Direct URL to the full-resolution file. */
    val path: String? = null,
)
