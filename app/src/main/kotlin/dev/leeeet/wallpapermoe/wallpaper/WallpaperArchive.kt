package dev.leeeet.wallpapermoe.wallpaper

import dev.leeeet.wallpapermoe.replaceWith
import dev.leeeet.wallpapermoe.source.WallpaperSample
import dev.leeeet.wallpapermoe.source.WallpaperMetadata
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
    val metadata: WallpaperMetadata,
    val file: File,
    val savedAt: Long,
)

/** Owns the permanent, on-device history and favorites collections. */
class WallpaperArchive(private val root: File) {

    @Serializable
    private data class Record(
        val id: String,
        val metadata: WallpaperMetadata,
        val fileName: String,
        val savedAt: Long,
    )

    @Serializable
    private data class Document(
        val history: List<Record>,
        val favorites: List<Record>,
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
            val document = if (documentFile.exists()) {
                documentJson.decodeFromString<Document>(documentFile.readText())
            } else {
                Document(emptyList(), emptyList())
            }
            document.validate()
            sweep(historyDir, document.history)
            sweep(favoritesDir, document.favorites)
            publish(document)
        }
    }

    suspend fun rememberApplied(sample: WallpaperSample) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            val record = copyInto(sample, historyDir)
            val nextHistory = (listOf(record) + current.history.filterNot {
                it.id == sample.id
            }).take(HISTORY_LIMIT)
            val next = current.copy(history = nextHistory)
            write(next)
            publish(next)
            sweep(historyDir, nextHistory)
        }
    }

    suspend fun addFavorite(wallpaper: SavedWallpaper) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            if (current.favorites.any { it.id == wallpaper.id }) return@withContext
            val record = copyInto(
                WallpaperSample(wallpaper.id, wallpaper.file, wallpaper.metadata),
                favoritesDir,
            )
            val next = current.copy(favorites = listOf(record) + current.favorites)
            write(next)
            publish(next)
            sweep(favoritesDir, next.favorites)
        }
    }

    suspend fun reorderFavorites(ids: List<String>): Boolean = mutex.withLock {
        val current = currentDocument()
        if (ids == current.favorites.map(Record::id)) return@withLock true
        if (ids.size != current.favorites.size || ids.toSet().size != ids.size) return@withLock false
        val byId = current.favorites.associateBy(Record::id)
        if (ids.any { it !in byId }) return@withLock false
        withContext(Dispatchers.IO) {
            val next = current.copy(favorites = ids.map(byId::getValue))
            write(next)
            publish(next)
        }
        true
    }

    suspend fun removeFavorites(ids: Set<String>) = mutex.withLock {
        if (ids.isEmpty()) return@withLock
        withContext(Dispatchers.IO) {
            val current = currentDocument()
            if (current.favorites.none { it.id in ids }) return@withContext
            val next = current.copy(
                favorites = current.favorites.filterNot { it.id in ids },
            )
            write(next)
            publish(next)
            sweep(favoritesDir, next.favorites)
        }
    }

    private fun copyInto(sample: WallpaperSample, directory: File): Record {
        val fileName = "${UUID.randomUUID()}.wp"
        val target = File(directory, fileName)
        val staged = File(directory, "$fileName.tmp")
        sample.file.copyTo(staged, overwrite = true)
        target.replaceWith(staged)
        return Record(sample.id, sample.metadata, fileName, System.currentTimeMillis())
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
        document.validate()
        root.mkdirs()
        val staged = File(root, "archive.json.tmp")
        staged.writeText(documentJson.encodeToString(Document.serializer(), document))
        documentFile.replaceWith(staged)
    }

    private fun Document.validate() {
        check(history.size <= HISTORY_LIMIT)
        validate(history, historyDir)
        validate(favorites, favoritesDir)
    }

    private fun validate(records: List<Record>, directory: File) {
        check(records.distinctBy(Record::id).size == records.size)
        check(records.distinctBy(Record::fileName).size == records.size)
        records.forEach {
            check(it.id.isNotBlank())
            check(it.fileName.isNotBlank() && File(it.fileName).name == it.fileName)
            check(File(directory, it.fileName).let { file ->
                file.isFile && file.length() > 0L
            })
        }
    }

    private fun sweep(directory: File, live: List<Record>) {
        val names = live.mapTo(mutableSetOf(), Record::fileName)
        directory.listFiles().orEmpty().filter { it.name !in names }.forEach { it.deleteRecursively() }
    }

    private fun Record.toSaved(directory: File) = SavedWallpaper(
        id = id,
        metadata = metadata,
        file = File(directory, fileName),
        savedAt = savedAt,
    )

    private fun SavedWallpaper.toRecord() = Record(id, metadata, file.name, savedAt)

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
