package dev.leeeet.wallpapermoe.provider.pandora

import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.net.APP_USER_AGENT
import dev.leeeet.wallpapermoe.net.getString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Xiaomi "Pandora" wallpaper service — the same backend Mi Wallpaper Carousel
 * (`com.mfashiongallery.emag`) uses.
 */
class PandoraApi(
    private val installId: suspend () -> String,
    private val screenWidthPx: Int,
    private val locale: String,
    private val appVersionName: String,
    private val appVersionCode: Int,
    private val baseUrl: String = "https://w.pandora.xiaomi.com",
) {

    data class Channel(
        val id: String,
        val name: String,
        val subscribers: Int?,
    )

    data class Image(val id: String, val url: String)

    suspend fun channels(): List<Channel> {
        val body = get(PATH_INDEX, emptyMap())
        val parsed = Http.json.decodeFromString<IndexResponse>(body)
        return parsed.items.mapNotNull { item ->
            val id = item.tagId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Channel(
                id = id,
                name = name,
                subscribers = item.subscribedNum,
            )
        }
    }

    suspend fun image(channelIds: List<String>): Image? {
        if (channelIds.isEmpty()) return null
        val body = get(
            PATH_LOCK_VIEW,
            mapOf(
                "page_size" to "1",
                "channel_id" to channelIds.joinToString(","),
            ),
        )
        val parsed = Http.json.decodeFromString<LockViewResponse>(body)
        return parsed.items.firstNotNullOfOrNull { item ->
            if (item.itemType != TYPE_IMAGE) {
                return@firstNotNullOfOrNull null
            }
            val id = item.id?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
            item.images.firstNotNullOfOrNull { it.clUrl?.toImage(id) }
        }
    }

    private suspend fun get(path: String, params: Map<String, String>): String {
        val query = (params + commonParams()).entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return Http.client.getString("$baseUrl$path?$query", APP_USER_AGENT)
    }

    private suspend fun commonParams(): Map<String, String> = mapOf(
        "_vcode" to appVersionCode.toString(),
        "_vname" to appVersionName,
        "_fver" to FEATURE_VER,
        "_locale" to locale,
        "_res" to screenWidthPx.toString(),
        "_devid" to installId(),
        "_nonce" to Random.nextInt().toString(),
        "_ts" to (System.currentTimeMillis() / 1000).toString(),
    )

    /** The API returns plaintext CDN roots, so the selected rendition is forced to TLS. */
    private fun ClUrl.toImage(id: String): Image? {
        val root = urlRoot?.takeIf { it.isNotBlank() }?.toHttps()
        val loc = locator?.takeIf { it.isNotBlank() }
        if (root == null || loc == null) return null
        return Image(id, "$root$FORMAT/w$screenWidthPx/$loc")
    }

    private fun String.toHttps() =
        if (startsWith("http://")) "https://" + substring("http://".length) else this

    private companion object {
        const val PATH_INDEX = "/api/a1/gallery/index"
        const val PATH_LOCK_VIEW = "/api/a1/lock/lock_view"

        const val FEATURE_VER = "20160831"
        const val TYPE_IMAGE = "image"
        const val FORMAT = "webp"
    }
}

// Wire types: upstream's field names, and only the fields the app reads.

@Serializable
private data class ClUrl(
    val locator: String? = null,
    @SerialName("url_root") val urlRoot: String? = null,
)

@Serializable
private data class IndexResponse(val items: List<IndexItem>)

@Serializable
private data class IndexItem(
    val tagId: String? = null,
    val name: String? = null,
    @SerialName("subscribed_num") val subscribedNum: Int? = null,
)

@Serializable
private data class LockViewResponse(val items: List<LockViewItem>)

@Serializable
private data class LockViewItem(
    val id: String? = null,
    @SerialName("item_type") val itemType: String? = null,
    val images: List<LockViewImage> = emptyList(),
)

@Serializable
private data class LockViewImage(
    @SerialName("cl_url") val clUrl: ClUrl? = null,
)
