package dev.leeeet.wallpapermoe.wallpaper

import dev.leeeet.wallpapermoe.source.WallpaperSample
import dev.leeeet.wallpapermoe.source.documentJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

data class SavedWallpaper(
    val id: String,
    val originalUrl: String,
    val prepared: File,
    val savedAt: Long,
)

/** Owns the permanent, on-device history and favorites collections. */
class WallpaperArchive(private val root: File) {

    @Serializable
    private data class Record(
        val id: String,
        val originalUrl: String,
        val fileName: String,
        val savedAt: Long,
    )

    @Serializable
    private data class Document(
        val history: List<Record> = emptyList(),
        val favorites: List<Record> = emptyList(),
    )

    private val mutex = Mutex()
    private val documentFile = File(root, "archive.json")
    private val historyDir = File(root, "history")
    private val favoritesDir = File(root, "favorites")

    private val _history = MutableStateFlow<List<SavedWallpaper>>(emptyList())
    val history: StateFlow<List<SavedWallpaper>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow<List<SavedWallpaper>>(emptyList())
    val favorites: StateFlow<List<SavedWallpaper>> = _favorites.asStateFlow()

    suspend fun load() = mutex.withLock {
        withContext(Dispatchers.IO) {
            root.mkdirs()
            historyDir.mkdirs()
            favoritesDir.mkdirs()
            val decoded = runCatching {
                documentJson.decodeFromString<Document>(documentFile.readText())
            }.getOrNull() ?: Document()
            val clean = Document(
                history = decoded.history.liveIn(historyDir).take(HISTORY_LIMIT),
                favorites = decoded.favorites.liveIn(favoritesDir),
            )
            sweep(historyDir, clean.history)
            sweep(favoritesDir, clean.favorites)
            write(clean)
            publish(clean)
        }
    }

    suspend fun rememberApplied(sample: WallpaperSample) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            val duplicate = current.history.firstOrNull { it.originalUrl == sample.originalUrl }
            val record = copyInto(sample, historyDir, duplicate?.id)
            val nextHistory = (listOf(record) + current.history.filterNot {
                it.originalUrl == sample.originalUrl
            }).take(HISTORY_LIMIT)
            val removed = current.history.filter { old -> nextHistory.none { it.id == old.id } }
            removed.forEach { File(historyDir, it.fileName).delete() }
            val next = current.copy(history = nextHistory)
            write(next)
            publish(next)
        }
    }

    suspend fun addFavorite(wallpaper: SavedWallpaper) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            if (current.favorites.any { it.originalUrl == wallpaper.originalUrl }) return@withContext
            val source = current.history.firstOrNull {
                it.originalUrl == wallpaper.originalUrl
            }?.toSaved(historyDir) ?: wallpaper
            val record = copyInto(
                WallpaperSample(source.prepared, source.originalUrl),
                favoritesDir,
            )
            val next = current.copy(favorites = listOf(record) + current.favorites)
            write(next)
            publish(next)
        }
    }

    suspend fun removeFavorite(originalUrl: String) = removeFavorites(setOf(originalUrl))

    suspend fun removeFavorites(originalUrls: Set<String>) = mutex.withLock {
        if (originalUrls.isEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            val removed = current.favorites.filter { it.originalUrl in originalUrls }
            if (removed.isEmpty()) return@withContext
            removed.forEach { File(favoritesDir, it.fileName).delete() }
            val next = current.copy(
                favorites = current.favorites.filterNot { it.originalUrl in originalUrls },
            )
            write(next)
            publish(next)
        }
    }

    private fun copyInto(sample: WallpaperSample, directory: File, reuseId: String? = null): Record {
        val id = reuseId ?: UUID.randomUUID().toString()
        val fileName = "$id.wp"
        val target = File(directory, fileName)
        val staged = File(directory, "$fileName.tmp")
        sample.prepared.copyTo(staged, overwrite = true)
        target.delete()
        check(staged.renameTo(target))
        return Record(id, sample.originalUrl, fileName, System.currentTimeMillis())
    }

    private fun currentDocument() = Document(
        history = _history.value.map { it.toRecord() },
        favorites = _favorites.value.map { it.toRecord() },
    )

    private fun publish(document: Document) {
        _history.value = document.history.map { it.toSaved(historyDir) }
        _favorites.value = document.favorites.map { it.toSaved(favoritesDir) }
    }

    private fun write(document: Document) {
        root.mkdirs()
        val staged = File(root, "archive.json.tmp")
        staged.writeText(documentJson.encodeToString(Document.serializer(), document))
        check(staged.renameTo(documentFile))
    }

    private fun List<Record>.liveIn(directory: File): List<Record> = filter {
        it.originalUrl.isNotBlank() && File(directory, it.fileName).let { file ->
            file.isFile && file.length() > 0L
        }
    }.distinctBy(Record::originalUrl)

    private fun sweep(directory: File, live: List<Record>) {
        val names = live.mapTo(mutableSetOf(), Record::fileName)
        directory.listFiles().orEmpty().filter { it.name !in names }.forEach { it.deleteRecursively() }
    }

    private fun Record.toSaved(directory: File) = SavedWallpaper(
        id = id,
        originalUrl = originalUrl,
        prepared = File(directory, fileName),
        savedAt = savedAt,
    )

    private fun SavedWallpaper.toRecord() = Record(id, originalUrl, prepared.name, savedAt)

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
