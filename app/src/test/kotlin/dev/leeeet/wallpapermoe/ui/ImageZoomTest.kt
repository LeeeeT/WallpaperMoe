package dev.leeeet.wallpapermoe.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageZoomTest {

    @Test
    fun pinchZoomHasNoApplicationLevelMaximum() {
        val transformed = ImageTransform().withGesture(
            centroid = Offset(500f, 1000f),
            pan = Offset.Zero,
            zoom = 100f,
            viewport = IntSize(1000, 2000),
            image = Size(1000f, 2000f),
        )

        assertEquals(100f, transformed.scale)
        assertEquals(Offset.Zero, transformed.offset)
    }

    @Test
    fun unzoomedImageAlwaysReturnsToCenter() {
        assertEquals(
            Offset.Zero,
            clampZoomOffset(Offset(200f, -300f), 1f, IntSize(1000, 2000), Size(1000f, 1000f)),
        )
    }

    @Test
    fun panningStopsAtTheFittedImageEdges() {
        assertEquals(
            Offset(500f, 0f),
            clampZoomOffset(Offset(900f, 900f), 2f, IntSize(1000, 2000), Size(1000f, 1000f)),
        )
        assertEquals(
            Offset(-750f, -1500f),
            clampZoomOffset(Offset(-900f, -2000f), 2.5f, IntSize(1000, 2000), Size(1000f, 2000f)),
        )
    }
}
