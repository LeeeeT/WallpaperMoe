package dev.leeeet.wallpapermoe.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallhavenAspectTest {

    @Test
    fun everyAspectOmitsTheApiFilter() {
        assertNull(WallhavenAspect.ALL.apiRatios())
    }

    @Test
    fun individualAspectsUseWallhavensValues() {
        assertEquals("portrait", setOf(WallhavenAspect.PORTRAIT).apiRatios())
        assertEquals(
            "1x1,3x2,4x3,5x4",
            setOf(WallhavenAspect.SQUARE).apiRatios(),
        )
        assertEquals("landscape", setOf(WallhavenAspect.LANDSCAPE).apiRatios())
    }

    @Test
    fun pairsCombineTheirValuesInUiOrder() {
        assertEquals(
            "portrait,1x1,3x2,4x3,5x4",
            setOf(WallhavenAspect.PORTRAIT, WallhavenAspect.SQUARE).apiRatios(),
        )
        assertEquals(
            "portrait,landscape",
            setOf(WallhavenAspect.PORTRAIT, WallhavenAspect.LANDSCAPE).apiRatios(),
        )
        assertEquals(
            "1x1,3x2,4x3,5x4,landscape",
            setOf(WallhavenAspect.SQUARE, WallhavenAspect.LANDSCAPE).apiRatios(),
        )
    }
}
