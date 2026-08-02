package dev.leeeet.wallpapermoe.provider.nekosbest

import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.getString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Client for the maintained v2 API. It deliberately exposes only static-image categories. */
class NekosBestApi(private val baseUrl: String = "https://nekos.best/api/v2") {

    data class Image(
        val url: String,
        val sourceUrl: String?,
        val artistName: String?,
        val artistUrl: String?,
    )

    /** Discovers categories instead of freezing the provider's evolving catalog in the app. */
    suspend fun imageCategories(): List<String> {
        val body = Http.client.getString("${baseUrl.trimEnd('/')}/endpoints", APP_USER_AGENT)
        return Http.json.parseToJsonElement(body).jsonObject.mapNotNull { (name, value) ->
            val format = value.jsonObject["format"]?.jsonPrimitive?.content
            name.takeIf { format.equals("png", ignoreCase = true) && isCategoryName(it) }
        }.sorted()
    }

    suspend fun image(category: String): Image? {
        require(isCategoryName(category))
        val url = "${baseUrl.trimEnd('/')}/$category?amount=1"
        val body = Http.client.getString(url, APP_USER_AGENT)
        return Http.json.decodeFromString<ImageResponse>(body).results.firstNotNullOfOrNull { item ->
            val imageUrl = item.url.clean() ?: return@firstNotNullOfOrNull null
            Image(
                url = imageUrl,
                sourceUrl = item.sourceUrl.clean(),
                artistName = item.artistName.clean(),
                artistUrl = item.artistUrl.clean(),
            )
        }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    companion object {
        private val CATEGORY = Regex("[a-z0-9_-]+")

        fun isCategoryName(value: String): Boolean = CATEGORY.matches(value)
    }
}

@Serializable
private data class ImageResponse(val results: List<ImageItem> = emptyList())

@Serializable
private data class ImageItem(
    val url: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artist_href") val artistUrl: String? = null,
)
