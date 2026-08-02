package dev.leeeet.wallpapermoe.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

internal data class ImageTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

internal fun ImageTransform.withGesture(
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    viewport: IntSize,
    image: Size,
): ImageTransform {
    if (!image.hasArea()) return this
    val nextScale = (scale * zoom).coerceAtLeast(1f)
    val scaleChange = nextScale / scale
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val nextOffset = offset * scaleChange +
        (centroid - center) * (1f - scaleChange) +
        pan
    return ImageTransform(
        scale = nextScale,
        offset = clampZoomOffset(nextOffset, nextScale, viewport, image),
    )
}

internal fun clampZoomOffset(
    offset: Offset,
    scale: Float,
    viewport: IntSize,
    image: Size,
): Offset {
    if (scale <= 1f || viewport.width <= 0 || viewport.height <= 0 || !image.hasArea()) {
        return Offset.Zero
    }
    val fit = min(viewport.width / image.width, viewport.height / image.height)
    val maxX = ((image.width * fit * scale - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((image.height * fit * scale - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

internal fun Size.hasArea(): Boolean =
    width.isFinite() && height.isFinite() && width > 0f && height > 0f
