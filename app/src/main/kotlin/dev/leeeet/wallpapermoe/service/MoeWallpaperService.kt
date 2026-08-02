package dev.leeeet.wallpapermoe.service

import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import dev.leeeet.wallpapermoe.MoeApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoeWallpaperService : WallpaperService() {
    private val graph get() = (application as MoeApplication).graph
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> if (WallpaperRuntime.isActive(context)) {
                    graph.rotator.onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> graph.rotator.onScreenOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            RECEIVER_NOT_EXPORTED,
        )
        WallpaperRuntime.refresh(this)
    }

    override fun onDestroy() {
        graph.rotator.onScreenOn()
        unregisterReceiver(screenReceiver)
        WallpaperRuntime.refresh(this)
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = MoeEngine()

    private inner class MoeEngine : Engine() {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var bitmap: Bitmap? = null
        private var needsDecode = true
        private var decodeJob: Job? = null
        private var visible = false
        private var surfaceReady = false
        private val redraw: () -> Unit = {
            mainHandler.post {
                needsDecode = true
                loadAndDraw()
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            WallpaperRuntime.register(redraw)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) loadAndDraw()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            loadAndDraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceReady = true
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
        }

        override fun onComputeColors(): WallpaperColors? = bitmap?.let(WallpaperColors::fromBitmap)

        override fun onDestroy() {
            WallpaperRuntime.unregister(redraw)
            decodeJob?.cancel()
            scope.cancel()
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }

        private fun loadAndDraw() {
            if (!visible || !surfaceReady) return
            val file = graph.currentWallpaper
            if (!file.isFile || file.length() == 0L) {
                bitmap?.recycle()
                bitmap = null
                needsDecode = false
                draw()
                return
            }
            if (bitmap != null && !needsDecode) {
                draw()
                return
            }

            decodeJob?.cancel()
            decodeJob = scope.launch {
                val decoded = try {
                    withContext(Dispatchers.IO) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    bitmap?.recycle()
                    bitmap = null
                    needsDecode = false
                    draw()
                    return@launch
                }
                bitmap?.recycle()
                bitmap = decoded
                needsDecode = false
                draw()
                notifyColorsChanged()
            }
        }

        private fun draw() {
            if (!visible || !surfaceReady) return
            val canvas = runCatching { surfaceHolder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                drawWallpaper(canvas)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawWallpaper(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            val image = bitmap ?: return
            if (canvas.width <= 0 || canvas.height <= 0) return
            val crop = centerCrop(image.width, image.height, canvas.width, canvas.height)
            canvas.drawBitmap(
                image,
                Rect(crop.left, crop.top, crop.right, crop.bottom),
                Rect(0, 0, canvas.width, canvas.height),
                paint,
            )
        }
    }
}
