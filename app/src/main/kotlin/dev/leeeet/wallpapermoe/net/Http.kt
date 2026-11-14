package dev.leeeet.wallpapermoe.net

import dev.leeeet.wallpapermoe.BuildConfig
import dev.leeeet.wallpapermoe.data.Blob
import dev.leeeet.wallpapermoe.data.Blobs
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Http {
    val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun text(url: HttpUrl, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json")
        headers.forEach(request::header)
        client.await(request.build()).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val source = response.body.source()
            if (source.request(MAX_TEXT_BYTES + 1)) throw IOException("The response is too large")
            source.readUtf8()
        }
    }

    suspend fun download(url: HttpUrl, blobs: Blobs, pins: Blobs.Pins): Blob = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.await(request).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            blobs.install(pins, response.body.byteStream())
        }
    }

    private suspend fun OkHttpClient.await(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

            override fun onResponse(call: Call, response: Response) =
                continuation.resume(response) { _, _, _ -> response.close() }
        })
    }

    private companion object {
        const val USER_AGENT = "WallpaperMoe/${BuildConfig.VERSION_NAME} (https://github.com/LeeeeT/WallpaperMoe)"
        const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    }
}

class RequestGate(private val intervalMs: Long, private val now: () -> Long = System::nanoTime) {
    private val mutex = Mutex()
    private var nextStart = Long.MIN_VALUE

    suspend fun await() = mutex.withLock {
        val wait = (nextStart - now()) / 1_000_000
        if (nextStart != Long.MIN_VALUE && wait > 0) delay(wait)
        nextStart = now() + intervalMs * 1_000_000
    }
}
