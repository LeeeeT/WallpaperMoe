package dev.leeeet.wallpapermoe.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class Blob(val hash: String, val bytes: Long, val mime: String, val width: Int, val height: Int)

class Blobs(private val root: File) {
    class Pins internal constructor() {
        internal val held = ArrayList<String>()
    }

    private val temp = File(root, "tmp")
    private val pinned = HashMap<String, Int>()

    init {
        temp.mkdirs()
    }

    fun file(hash: String): File = File(root, hash)

    fun pins(): Pins = Pins()

    fun hold(pins: Pins, hash: String): Boolean = synchronized(pinned) {
        if (!file(hash).isFile) return false
        pin(pins, hash)
        true
    }

    fun adopt(pins: Pins, hashes: Collection<String>) = synchronized(pinned) {
        hashes.forEach { pin(pins, it) }
    }

    suspend fun install(pins: Pins, input: InputStream): Blob = withContext(Dispatchers.IO) {
        val file = File.createTempFile("blob", null, temp)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    bytes += read
                    if (bytes > MAX_BYTES) throw IOException("The image is too large")
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val mime = bounds.outMimeType
            if (mime == null || bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The file is not an image")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            synchronized(pinned) {
                pin(pins, hash)
                if (!file(hash).isFile && !file.renameTo(file(hash))) throw IOException("The image cannot be stored")
            }
            Blob(hash, bytes, mime, bounds.outWidth, bounds.outHeight)
        } finally {
            file.delete()
        }
    }

    fun release(pins: Pins, referenced: Set<String>) = synchronized(pinned) {
        val released = pins.held.toList()
        pins.held.clear()
        for (hash in released) {
            val count = pinned.getValue(hash) - 1
            if (count == 0) pinned.remove(hash) else pinned[hash] = count
        }
        released.distinct().filter { it !in referenced && it !in pinned }.forEach { file(it).delete() }
    }

    fun discard(hashes: Collection<String>) = synchronized(pinned) {
        hashes.filter { it !in pinned }.forEach { file(it).delete() }
    }

    fun sweep(referenced: Set<String>) = synchronized(pinned) {
        temp.listFiles()?.forEach(File::delete)
        root.listFiles()?.filter { it.isFile && it.name !in referenced && it.name !in pinned }?.forEach(File::delete)
    }

    fun size(hashes: Collection<String>): Long = hashes.sumOf { file(it).length() }

    private fun pin(pins: Pins, hash: String) {
        pins.held += hash
        pinned[hash] = (pinned[hash] ?: 0) + 1
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
