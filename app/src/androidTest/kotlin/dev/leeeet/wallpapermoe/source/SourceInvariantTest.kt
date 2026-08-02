package dev.leeeet.wallpapermoe.source

import org.junit.Assert.assertThrows
import org.junit.Test

class SourceInvariantTest {

    @Test
    fun runtimeCompositionsCannotBeEmpty() {
        assertThrows(IllegalArgumentException::class.java) { RoundRobin(emptyList(), "r") }
        assertThrows(IllegalArgumentException::class.java) { Random(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Fallthrough(emptyList()) }
    }

    @Test
    fun runtimeSourceSettingsCannotBeStructurallyInvalid() {
        val source = wallhaven()
        assertThrows(IllegalArgumentException::class.java) { Pandora(emptyList(), emptyList(), "p") }
        assertThrows(IllegalArgumentException::class.java) { Cache(source, 0, 24, "c") }
        assertThrows(IllegalArgumentException::class.java) { Cache(source, 20, -1, "c") }
        assertThrows(IllegalArgumentException::class.java) { Prefetch(source, 0, "p") }
        assertThrows(IllegalArgumentException::class.java) {
            NekosBest(emptyList(), SelectionMode.RANDOM, "n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NekosBest(listOf("neko", "neko"), SelectionMode.RANDOM, "n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NekosBest(listOf("../neko"), SelectionMode.RANDOM, "n")
        }
    }
}
