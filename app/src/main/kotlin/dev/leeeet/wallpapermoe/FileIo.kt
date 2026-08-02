package dev.leeeet.wallpapermoe

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Atomically makes this file the complete contents staged in [source]. */
internal fun File.replaceWith(source: File) {
    Files.move(source.toPath(), toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}
