package dev.leeeet.wallpapermoe.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderingTest {

    @Test
    fun movingAnItemPreservesEverythingElseInOrder() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").moved(0, 2))
        assertEquals(listOf("a", "d", "b", "c"), listOf("a", "b", "c", "d").moved(3, 1))
    }

    @Test
    fun duplicateValuesRemainIntentional() {
        assertEquals(listOf("a", "b", "a"), listOf("a", "a", "b").moved(1, 2))
    }
}
