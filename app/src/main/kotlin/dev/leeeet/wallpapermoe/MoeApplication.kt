package dev.leeeet.wallpapermoe

import android.app.Application
import dev.leeeet.wallpapermoe.service.WallpaperRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MoeApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        WallpaperRuntime.refresh(this)

        scope.launch {
            graph.archive.load()
            graph.sources.load()
        }
    }
}
