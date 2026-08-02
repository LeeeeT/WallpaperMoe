package dev.leeeet.wallpapermoe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperCropTest {
    @Test
    fun matchingRatioKeepsWholeImage() {
        assertEquals(ImageCrop(0, 0, 1080, 1920), centerCrop(1080, 1920, 1080, 1920))
    }

    @Test
    fun wideImageOnlyCropsSidesAndKeepsThemEqual() {
        val crop = centerCrop(3840, 2160, 1080, 2424)

        assertEquals(0, crop.top)
        assertEquals(2160, crop.bottom)
        assertEquals(crop.left, 3840 - crop.right)
    }

    @Test
    fun tallImageOnlyCropsTopAndBottomAndKeepsThemEqual() {
        val crop = centerCrop(1080, 3840, 1080, 2424)

        assertEquals(0, crop.left)
        assertEquals(1080, crop.right)
        assertEquals(crop.top, 3840 - crop.bottom)
    }
}
