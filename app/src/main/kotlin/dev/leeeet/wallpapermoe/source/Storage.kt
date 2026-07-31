package dev.leeeet.wallpapermoe.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

class Storage(private val root: File) {

    suspend fun prepare() = withContext(Dispatchers.IO) {
        val schema = File(root, SCHEMA_FILE)
        if (schema.readTextOrNull() == STORAGE_SCHEMA) return@withContext
        root.listFiles().orEmpty().forEach { it.deleteRecursively() }
        root.mkdirs()
        schema.writeText(STORAGE_SCHEMA)
    }

    fun dir(name: String): File = File(root, name).apply { mkdirs() }

    /** Deletes state for removed definitions and disabled runtime stages. */
    suspend fun sweep(liveStores: Set<String>) = withContext(Dispatchers.IO) {
        val liveByDefinition = liveStores.groupBy(
            keySelector = { it.substringBefore('/') },
            valueTransform = { it.substringAfter('/') },
        ).mapValues { (_, roles) -> roles.toSet() }

        root.listFiles()?.forEach { definitionDir ->
            if (definitionDir.name == SCHEMA_FILE) return@forEach
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

    private fun File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private companion object {
        const val SCHEMA_FILE = ".schema"
        const val STORAGE_SCHEMA = "2"
    }
}

internal const val STATE_FILE = "state.json"

/**
 * Runtime memory a source keeps in its folder, beside the files it indexes.
 * Unreadable state reads as nothing, so a source starts over rather than
 * refusing to sample.
 */
internal inline fun <reified S> File.loadState(): S? = runCatching {
    File(this, STATE_FILE).takeIf { it.exists() }
        ?.let { documentJson.decodeFromString<S>(it.readText()) }
}.getOrNull()

/** Staged through a rename, so the state file only ever appears complete. */
internal inline fun <reified S> File.saveState(state: S) {
    runCatching {
        val tmp = File(this, "$STATE_FILE.tmp")
        tmp.writeText(documentJson.encodeToString(state))
        tmp.renameTo(File(this, STATE_FILE))
    }
}

/**
 * A fresh name per sample: a background refill and a foreground rotation can be
 * inside the same source at once, and a fixed path would have the second writer
 * pull the file out from under the first reader.
 */
internal fun File.newSampleFile(): File = File(this, "s-${System.nanoTime()}.wp")

internal fun File.pruneSamples(keep: Int = KEEP_SAMPLES) {
    val entries = listFiles()?.filter { it.name.startsWith("s-") } ?: return
    if (entries.size <= keep) return
    entries.sortedByDescending { it.lastModified() }.drop(keep).forEach { it.deleteRecursively() }
}

private const val KEEP_SAMPLES = 2

/**
 * Copies, because the source that produced the file will reuse the original.
 * Staged through a `.part` name so the target only ever appears complete —
 * Prefetch reads its folder as its buffer. Wall-clock names keep buffer order
 * across a reboot; the nano suffix keeps them unique.
 */
@Serializable
private data class StoredSample(val originalUrl: String)

private const val STORED_IMAGE = "image.wp"
private const val STORED_SAMPLE = "sample.json"

internal fun File.storedSample(): WallpaperSample? = runCatching {
    if (!isDirectory) return@runCatching null
    val image = File(this, STORED_IMAGE)
    if (!image.isFile || image.length() == 0L) return@runCatching null
    val metadata = documentJson.decodeFromString<StoredSample>(File(this, STORED_SAMPLE).readText())
    WallpaperSample(image, metadata.originalUrl)
}.getOrNull()

internal suspend fun adopt(produced: WallpaperSample, dir: File, prefix: String): WallpaperSample =
    withContext(Dispatchers.IO) {
        val target = File(dir, "$prefix-${System.currentTimeMillis()}-${System.nanoTime()}")
        val staged = File(dir, "${target.name}.part").apply { mkdirs() }
        produced.prepared.copyTo(File(staged, STORED_IMAGE), overwrite = true)
        File(staged, STORED_SAMPLE).writeText(
            documentJson.encodeToString(StoredSample(produced.originalUrl)),
        )
        check(staged.renameTo(target))
        checkNotNull(target.storedSample())
    }

internal fun File.liveNames(names: List<String>): List<String> =
    names.filter { File(this, it).storedSample() != null }
