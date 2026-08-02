package dev.leeeet.wallpapermoe.service

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

object WallpaperRuntime {
    private val componentClass = MoeWallpaperService::class.java
    private val renderers = CopyOnWriteArraySet<() -> Unit>()
    private val _active = MutableStateFlow(false)

    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun activationIntent(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, componentClass),
        )

    fun refresh(context: Context) {
        _active.value = isActive(context)
    }

    fun isActive(context: Context): Boolean {
        val component = ComponentName(context, componentClass)
        val manager = WallpaperManager.getInstance(context)
        return manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == component ||
            manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == component
    }

    fun register(renderer: () -> Unit) {
        renderers += renderer
    }

    fun unregister(renderer: () -> Unit) {
        renderers -= renderer
    }

    fun redraw() {
        renderers.forEach { it() }
    }
}
