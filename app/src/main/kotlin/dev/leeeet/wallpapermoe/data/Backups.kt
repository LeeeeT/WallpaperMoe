package dev.leeeet.wallpapermoe.data

import android.content.Context
import android.net.Uri
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Appearance
import dev.leeeet.wallpapermoe.model.Feed
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Source
import dev.leeeet.wallpapermoe.model.normalized
import java.io.FilterInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
private data class Backup(
    val version: Int = 1,
    val sources: List<Source> = emptyList(),
    val home: Feed = Feed(),
    val lock: Feed? = null,
    val homeAppearance: Appearance = Appearance(),
    val lockAppearance: Appearance = Appearance(),
    val unmeteredOnly: Boolean = false,
    val favorites: List<Image> = emptyList(),
)

class Backups(private val context: Context, private val store: Store, private val blobs: Blobs) {
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        store.pinned { pins ->
            val data = store.current()
            val favorites = data.favorites.filter { blobs.hold(pins, it.blob) }
            val backup = Backup(
                sources = data.sources,
                home = data.home,
                lock = data.lock,
                homeAppearance = data.homeAppearance,
                lockAppearance = data.lockAppearance,
                unmeteredOnly = data.unmeteredOnly,
                favorites = favorites,
            )
            val output = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("The file cannot be written")
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(moeJson.encodeToString(backup).encodeToByteArray())
                zip.closeEntry()
                for (hash in favorites.mapTo(LinkedHashSet(), Image::blob)) {
                    zip.putNextEntry(ZipEntry(BLOBS + hash))
                    blobs.file(hash).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun import(source: Uri) = withContext(Dispatchers.IO) {
        store.pinned { pins ->
            var backup: Backup? = null
            val installed = HashSet<String>()
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("The file cannot be read")
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == MANIFEST -> backup = try {
                            moeJson.decodeFromString<Backup>(zip.readBytes().decodeToString())
                        } catch (e: SerializationException) {
                            throw IOException("The backup is not valid", e)
                        }
                        entry.name.startsWith(BLOBS) -> installed += blobs.install(pins, Unclosed(zip)).hash
                    }
                }
            }
            val restored = backup?.takeIf { it.version == 1 } ?: throw IOException("The backup is not valid")
            store.update(pins) { data ->
                AppData(
                    sources = restored.sources.distinctBy(Source::id),
                    home = restored.home,
                    lock = restored.lock,
                    homeAppearance = restored.homeAppearance,
                    lockAppearance = restored.lockAppearance,
                    unmeteredOnly = restored.unmeteredOnly,
                    wallhavenKey = data.wallhavenKey,
                    installId = data.installId,
                    shown = data.shown,
                    history = data.history,
                    favorites = restored.favorites.filter { it.blob in installed }.distinctBy(Image::id),
                ).normalized()
            }
        }
    }

    private class Unclosed(zip: ZipInputStream) : FilterInputStream(zip) {
        override fun close() = Unit
    }

    private companion object {
        const val MANIFEST = "backup.json"
        const val BLOBS = "blobs/"
    }
}
