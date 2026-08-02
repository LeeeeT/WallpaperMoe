package dev.leeeet.wallpapermoe.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Provider-compliant identity with a durable project contact URL. */
const val APP_USER_AGENT = "WallpaperMoe (https://github.com/LeeeeT/WallpaperMoe)"

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
    }
}

/**
 * Suspending OkHttp call. Cancellation cancels the request, and a response that
 * wins the race against it is closed.
 */
suspend fun OkHttpClient.await(request: Request): Response = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) =
            cont.resume(response) { _, _, _ -> response.close() }
    })
}

suspend fun OkHttpClient.getString(url: String, userAgent: String): String =
    withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        await(req).use { resp ->
            if (!resp.isSuccessful) throw IOException()
            resp.body.string()
        }
    }
