package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.replaceWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

class Storage(private val root: File) {
    fun dir(name: String): File = File(root, name).apply { mkdirs() }

    /** Deletes state for removed definitions and disabled runtime stages. */
    suspend fun sweep(liveStores: Set<String>) = withContext(Dispatchers.IO) {
        val liveByDefinition = liveStores.groupBy(
            keySelector = { it.substringBefore('/') },
            valueTransform = { it.substringAfter('/') },
        ).mapValues { (_, roles) -> roles.toSet() }

        root.listFiles()?.forEach { definitionDir ->
            val liveRoles = liveByDefinition[definitionDir.name]
            if (!definitionDir.isDirectory || liveRoles == null) {
                definitionDir.deleteRecursively()
            } else {
                definitionDir.listFiles()?.forEach { child ->
                    if (!child.isDirectory || child.name !in liveRoles) child.deleteRecursively()
                }
                if (definitionDir.listFiles().isNullOrEmpty()) definitionDir.delete()
            }
        }
        Unit
    }
}

internal const val STATE_FILE = "state.json"

/** Runtime memory a source keeps in its folder, beside the files it indexes. */
internal inline fun <reified S> File.loadState(): S? {
    val state = File(this, STATE_FILE)
    return if (state.exists()) documentJson.decodeFromString<S>(state.readText()) else null
}

/** Staged through a rename, so the state file only ever appears complete. */
internal inline fun <reified S> File.saveState(state: S) {
    val tmp = File(this, "$STATE_FILE.tmp")
    tmp.writeText(documentJson.encodeToString(state))
    File(this, STATE_FILE).replaceWith(tmp)
}

/**
 * A fresh name per sample: a background refill and a foreground rotation can be
 * inside the same source at once, and a fixed path would have the second writer
 * pull the file out from under the first reader.
 */
internal fun File.newSampleFile(): File = File(this, "s-${UUID.randomUUID()}.wp")

internal fun File.pruneSamples(keep: Int = KEEP_SAMPLES) {
    val entries = listFiles()?.filter { it.name.startsWith("s-") } ?: return
    if (entries.size <= keep) return
    entries.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.deleteRecursively() }
}

private const val KEEP_SAMPLES = 2

/** One adopted sample's record, beside the image it describes. */
@Serializable
private data class StoredSample(
    val id: String,
    val metadata: WallpaperMetadata,
)

private const val STORED_IMAGE = "image.wp"
private const val STORED_SAMPLE = "sample.json"

internal fun File.readStoredSample(): WallpaperSample {
    check(isDirectory)
    val image = File(this, STORED_IMAGE)
    check(image.isFile && image.length() > 0L)
    val metadata = documentJson.decodeFromString<StoredSample>(File(this, STORED_SAMPLE).readText())
    return WallpaperSample(metadata.id, image, metadata.metadata)
}

/**
 * Copies, because the source that produced the file will reuse the original.
 * Staged through a `.part` name so the target only ever appears complete —
 * Prefetch reads its folder as its buffer. Wall-clock names keep buffer order
 * across a reboot; the nano suffix keeps them unique.
 */
internal suspend fun adopt(produced: WallpaperSample, dir: File, prefix: String): WallpaperSample =
    withContext(Dispatchers.IO) {
        val target = File(dir, "$prefix-${System.currentTimeMillis()}-${System.nanoTime()}")
        val staged = File(dir, ".${target.name}.part").apply { mkdirs() }
        produced.file.copyTo(File(staged, STORED_IMAGE), overwrite = true)
        File(staged, STORED_SAMPLE).writeText(
            documentJson.encodeToString(StoredSample(produced.id, produced.metadata)),
        )
        check(staged.renameTo(target))
        target.readStoredSample()
    }
