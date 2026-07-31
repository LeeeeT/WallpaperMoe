package dev.leeeet.wallpapermoe

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dev.leeeet.wallpapermoe.net.Http
import dev.leeeet.wallpapermoe.service.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MoeApplication : Application(), SingletonImageLoader.Factory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Shares the providers' OkHttp client, so pooling and timeouts are shared too. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
            .crossfade(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)

        scope.launch {
            Graph.archive.load()
            Graph.sources.load()
            Graph.prefs.settings
                .distinctUntilChanged()
                .collect { Engine.applySettings(this@MoeApplication, it) }
        }
    }
}
