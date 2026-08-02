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
        assertEquals("landscape", setOf(WallhavenAspect.LANDSCAPE).apiRatios())
    }
}
