package dev.leeeet.wallpapermoe.service

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import dev.leeeet.wallpapermoe.model.Appearance
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Frame(val bitmap: Bitmap, val swatch: Bitmap)

suspend fun decodeFrame(file: File, width: Int, height: Int): Frame = withContext(Dispatchers.Default) {
    val source = ImageDecoder.createSource(file)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.cover(info.size.width, info.size.height, width, height)
        decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
    }
    val scale = SWATCH_SIZE.toFloat() / max(width, height)
    val swatch = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.cover(info.size.width, info.size.height, max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    Frame(bitmap, swatch)
}

private fun ImageDecoder.cover(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int) {
    val scale = min(1f, max(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight))
    val scaledWidth = max(1, ceil(sourceWidth * scale).toInt())
    val scaledHeight = max(1, ceil(sourceHeight * scale).toInt())
    setTargetSize(scaledWidth, scaledHeight)
    crop = centerCrop(scaledWidth, scaledHeight, width, height)
}

fun centerCrop(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): Rect {
    val wide = sourceWidth.toLong() * height > width.toLong() * sourceHeight
    return if (wide) {
        val cropWidth = (sourceHeight.toLong() * width / height).toInt().coerceIn(1, sourceWidth)
        val left = (sourceWidth - cropWidth) / 2
        Rect(left, 0, left + cropWidth, sourceHeight)
    } else {
        val cropHeight = (sourceWidth.toLong() * height / width).toInt().coerceIn(1, sourceHeight)
        val top = (sourceHeight - cropHeight) / 2
        Rect(0, top, sourceWidth, top + cropHeight)
    }
}

fun Appearance.colorFilter(): ColorMatrixColorFilter? {
    if (brightness == 1f && contrast == 1f) return null
    val scale = brightness * contrast
    val offset = brightness * 127.5f * (1f - contrast)
    return ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, offset,
                0f, scale, 0f, 0f, offset,
                0f, 0f, scale, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

fun Appearance.renderEffect(width: Int): RenderEffect? {
    val colors = colorFilter()?.let(RenderEffect::createColorFilterEffect)
    val radius = blur * width * BLUR_FRACTION
    return when {
        radius < 0.5f -> colors
        colors == null -> RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        else -> RenderEffect.createBlurEffect(radius, radius, colors, Shader.TileMode.CLAMP)
    }
}

fun Appearance.mix(other: Appearance, fraction: Float) = Appearance(
    brightness + (other.brightness - brightness) * fraction,
    contrast + (other.contrast - contrast) * fraction,
    blur + (other.blur - blur) * fraction,
)

suspend fun Frame.colors(appearance: Appearance): WallpaperColors = withContext(Dispatchers.Default) {
    val filter = appearance.colorFilter() ?: return@withContext WallpaperColors.fromBitmap(swatch)
    val adjusted = createBitmap(swatch.width, swatch.height)
    Canvas(adjusted).drawBitmap(swatch, 0f, 0f, Paint().apply { colorFilter = filter })
    WallpaperColors.fromBitmap(adjusted).also { adjusted.recycle() }
}

private const val SWATCH_SIZE = 128
private const val BLUR_FRACTION = 0.03f
