package dev.leeeet.wallpapermoe.service

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.SurfaceHolder
import android.view.animation.PathInterpolator
import dev.leeeet.wallpapermoe.MoeApp
import dev.leeeet.wallpapermoe.model.Appearance
import dev.leeeet.wallpapermoe.model.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MoeWallpaperService : WallpaperService() {
    private val graph get() = (application as MoeApp).graph
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val displays by lazy { getSystemService(DisplayManager::class.java) }
    private val engines = ArrayList<MoeEngine>()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) engines.forEach(MoeEngine::refresh)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    engines.forEach { it.lock(true, animate = false) }
                    if (engines.any { !it.isPreview }) graph.changer.screenOff()
                }
                Intent.ACTION_SCREEN_ON -> engines.forEach {
                    it.lock(keyguard.isKeyguardLocked, animate = false)
                    it.refresh()
                }
                Intent.ACTION_USER_PRESENT -> engines.forEach { it.lock(false, animate = true) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, filter)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            displays.registerDisplayListener(mainExecutor, DisplayManager.EVENT_TYPE_DISPLAY_STATE, displayListener)
        } else {
            displays.registerDisplayListener(displayListener, Handler(mainLooper))
        }
    }

    override fun onDestroy() {
        displays.unregisterDisplayListener(displayListener)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = MoeEngine()

    private fun displayDark(): Boolean = displays.getDisplay(Display.DEFAULT_DISPLAY)?.state in DARK_STATES

    private data class Scene(val blob: String?, val appearance: Appearance)

    private data class Input(
        val home: String?,
        val lock: String?,
        val homeAppearance: Appearance,
        val lockAppearance: Appearance,
    )

    private inner class MoeEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val choreographer = Choreographer.getInstance()
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val back = RenderNode("back")
        private val front = RenderNode("front")
        private val bounds = Rect()
        private val frames = HashMap<String, Frame>()
        private val decoding = HashMap<String, Job>()
        private val failed = HashSet<String>()
        private val tick = Choreographer.FrameCallback { draw() }
        private val watch = Choreographer.FrameCallback { lock(keyguard.isKeyguardLocked, animate = true) }

        private var input: Input? = null
        private var latest: Input? = null
        private var upcoming: Set<String> = emptySet()
        private var automatic = false
        private var locked = keyguard.isKeyguardLocked
        private var ready = false
        private var from: Scene? = null
        private var to: Scene? = null
        private var startedAt = 0L
        private var duration = 0L
        private var colors: WallpaperColors? = null
        private var colorScene: Scene? = null

        private val screen: Screen
            get() = when {
                wallpaperFlags and WallpaperManager.FLAG_SYSTEM == 0 -> Screen.LOCK
                wallpaperFlags and WallpaperManager.FLAG_LOCK == 0 -> Screen.HOME
                locked && !isPreview -> Screen.LOCK
                else -> Screen.HOME
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            engines += this
            if (!isPreview) graph.changer.start()
            scope.launch {
                graph.store.data.filterNotNull()
                    .map { data ->
                        Input(
                            data.shown(Screen.HOME)?.image?.blob,
                            data.shown(Screen.LOCK)?.image?.blob,
                            data.homeAppearance,
                            data.lockAppearance,
                        )
                    }
                    .distinctUntilChanged()
                    .collect {
                        latest = it
                        automatic = !power.isInteractive
                        refresh(animate = true)
                    }
            }
            scope.launch {
                graph.store.data.filterNotNull()
                    .map { data -> data.next.values.mapTo(HashSet()) { it.blob } }
                    .distinctUntilChanged()
                    .collect {
                        upcoming = it
                        if (ready) it.forEach(::decode)
                    }
            }
        }

        override fun onDestroy() {
            engines -= this
            choreographer.removeFrameCallback(tick)
            choreographer.removeFrameCallback(watch)
            scope.cancel()
            frames.values.forEach(::recycle)
            frames.clear()
            back.discardDisplayList()
            front.discardDisplayList()
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (width != bounds.width() || height != bounds.height()) {
                bounds.set(0, 0, width, height)
                decoding.values.forEach(Job::cancel)
                decoding.clear()
                back.discardDisplayList()
                front.discardDisplayList()
                frames.values.forEach(::recycle)
                frames.clear()
                failed.clear()
                to = null
                from = null
            }
            ready = true
            refresh()
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = draw()

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            ready = false
            choreographer.removeFrameCallback(tick)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                lock(keyguard.isKeyguardLocked, animate = false)
            } else {
                choreographer.removeFrameCallback(watch)
                refresh()
            }
        }

        override fun onWallpaperFlagsChanged(which: Int) = retarget(animate = false)

        override fun onComputeColors(): WallpaperColors? = colors

        fun lock(locked: Boolean, animate: Boolean) {
            choreographer.removeFrameCallback(watch)
            if (locked && isVisible && power.isInteractive) choreographer.postFrameCallback(watch)
            if (this.locked == locked) return
            this.locked = locked
            retarget(animate)
        }

        fun refresh(animate: Boolean = false) {
            adopt()
            retarget(animate)
        }

        private fun adopt() {
            if (ready) upcoming.forEach(::decode)
            val next = latest ?: return
            if (next == input || !ready) return
            val blobs = setOfNotNull(next.home, next.lock)
            blobs.forEach(::decode)
            if (input != null && automatic) {
                if (isVisible && !displayDark()) return
                if (!blobs.all { it in frames || it in failed }) return
            }
            input = next
        }

        private fun retarget(animate: Boolean) {
            val input = input ?: return
            if (!ready) return
            for (blob in setOfNotNull(input.home, input.lock)) decode(blob)
            updateColors(input)
            val target = when (screen) {
                Screen.HOME -> Scene(input.home, input.homeAppearance)
                Screen.LOCK -> Scene(input.lock, input.lockAppearance)
            }
            if (target.blob != null && target.blob !in frames && target.blob !in failed) return
            if (target == to) return
            val current = to
            from = current
            to = target
            val swap = current != null && current.blob != target.blob
            duration = when {
                current == null || !isVisible || !power.isInteractive -> 0
                swap || animate -> TRANSITION_MS
                else -> 0
            }
            startedAt = System.nanoTime()
            draw()
        }

        private fun decode(blob: String) {
            if (blob in frames || blob in failed || decoding[blob]?.isActive == true) return
            val width = bounds.width()
            val height = bounds.height()
            decoding[blob] = scope.launch {
                val frame = try {
                    decodeFrame(graph.blobs.file(blob), width, height)
                } catch (e: java.io.IOException) {
                    Log.w(TAG, "The wallpaper image cannot be decoded", e)
                    failed += blob
                    decoding -= blob
                    refresh()
                    return@launch
                }
                frames[blob] = frame
                decoding -= blob
                refresh(animate = true)
            }
        }

        private fun updateColors(input: Input) {
            val scene = if (wallpaperFlags and WallpaperManager.FLAG_SYSTEM == 0) {
                Scene(input.lock, input.lockAppearance)
            } else {
                Scene(input.home, input.homeAppearance)
            }
            val key = scene.copy(appearance = scene.appearance.copy(blur = 0f))
            val frame = frames[scene.blob] ?: return
            if (key == colorScene || !displayDark()) return
            colorScene = key
            scope.launch {
                val computed = frame.colors(key.appearance)
                if (colorScene != key) return@launch
                if (!displayDark()) {
                    colorScene = null
                    return@launch
                }
                colors = computed
                notifyColorsChanged()
            }
        }

        private fun draw() {
            choreographer.removeFrameCallback(tick)
            val target = to ?: return
            if (!ready) return
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000
            val fraction = if (duration <= 0) 1f else (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val canvas = try {
                surfaceHolder.lockHardwareCanvas()
            } catch (e: IllegalStateException) {
                null
            } ?: return
            try {
                render(canvas, target, EASING.getInterpolation(fraction))
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
            if (fraction < 1f) {
                choreographer.postFrameCallback(tick)
            } else {
                from = target
                prune()
            }
        }

        private fun render(canvas: Canvas, target: Scene, fraction: Float) {
            canvas.drawColor(PLACEHOLDER)
            val start = from
            when {
                start == null || fraction >= 1f || start == target -> layer(canvas, front, target.blob, target.appearance, 1f)
                start.blob == target.blob ->
                    layer(canvas, front, target.blob, start.appearance.mix(target.appearance, fraction), 1f)
                else -> {
                    layer(canvas, back, start.blob, start.appearance, 1f)
                    layer(canvas, front, target.blob, target.appearance, fraction)
                }
            }
        }

        private fun layer(canvas: Canvas, node: RenderNode, blob: String?, appearance: Appearance, alpha: Float) {
            val frame = frames[blob] ?: return
            node.setPosition(bounds)
            node.beginRecording().drawBitmap(frame.bitmap, null, bounds, paint)
            node.endRecording()
            node.setRenderEffect(appearance.renderEffect(bounds.width()))
            node.alpha = alpha
            canvas.drawRenderNode(node)
        }

        private fun prune() {
            val input = input ?: return
            val needed = setOfNotNull(input.home, input.lock, latest?.home, latest?.lock, to?.blob) + upcoming
            val unused = frames.keys - needed
            if (unused.isEmpty()) return
            back.discardDisplayList()
            unused.forEach { frames.remove(it)?.let(::recycle) }
        }

        private fun recycle(frame: Frame) {
            frame.bitmap.recycle()
        }
    }

    companion object {
        private const val TAG = "WallpaperMoe"
        private const val TRANSITION_MS = 450L
        private val PLACEHOLDER = Color.rgb(16, 16, 20)
        private val EASING = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val DARK_STATES = setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND, Display.STATE_ON_SUSPEND)

        fun component(context: Context) = ComponentName(context, MoeWallpaperService::class.java)

        fun isActive(context: Context): Boolean {
            val manager = WallpaperManager.getInstance(context)
            val component = component(context)
            return manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == component ||
                manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component == component
        }
    }
}
