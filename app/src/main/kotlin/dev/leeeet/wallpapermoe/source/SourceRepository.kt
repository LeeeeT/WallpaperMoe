package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.replaceWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Owns the named source library, its active source, and their on-disk document. */
class SourceRepository(
    private val file: File,
    private val storage: Storage,
) {

    enum class DeleteResult { DELETED, REFERENCED, MISSING }

    private val mutex = Mutex()
    private val _library = MutableStateFlow(SourceLibrary())
    val library: StateFlow<SourceLibrary> = _library.asStateFlow()

    private val _source = MutableStateFlow<Source?>(null)
    val source: StateFlow<Source?> = _source.asStateFlow()

    suspend fun load() = mutex.withLock {
        val loaded = withContext(Dispatchers.IO) {
            if (file.exists()) {
                documentJson.decodeFromString(SourceLibrary.serializer(), file.readText())
            } else {
                SourceLibrary()
            }
        }
        check(loaded.problem == null) { "Saved source library is invalid: ${loaded.problem}" }
        publish(loaded)
    }

    suspend fun upsert(definition: SourceDefinition): Boolean = mutex.withLock {
        val normalized = definition.copy(name = definition.name.trim())
        val next = _library.value.upserting(normalized)
        if (next.problem != null) return@withLock false
        persistAndPublish(next)
        true
    }

    suspend fun setActive(id: SourceId?) = mutex.withLock {
        val next = _library.value.copy(activeSourceId = id)
        require(next.problem == null)
        persistAndPublish(next)
    }

    suspend fun reorderSources(ids: List<SourceId>): Boolean = mutex.withLock {
        val current = _library.value
        if (ids == current.sources.map(SourceDefinition::id)) return@withLock true
        if (ids.size != current.sources.size || ids.toSet().size != ids.size) return@withLock false
        val byId = current.sources.associateBy(SourceDefinition::id)
        if (ids.any { it !in byId }) return@withLock false
        persistAndPublish(current.copy(sources = ids.map(byId::getValue)))
        true
    }

    suspend fun delete(id: SourceId): DeleteResult = mutex.withLock {
        val current = _library.value
        if (current.sources.none { it.id == id }) return@withLock DeleteResult.MISSING
        if (current.referencesTo(id).isNotEmpty()) return@withLock DeleteResult.REFERENCED
        val next = current.copy(
            sources = current.sources.filterNot { it.id == id },
            activeSourceId = current.activeSourceId.takeUnless { it == id },
        )
        persistAndPublish(next)
        DeleteResult.DELETED
    }

    private suspend fun persistAndPublish(value: SourceLibrary) {
        write(value)
        publish(value)
    }

    private suspend fun publish(value: SourceLibrary) {
        val resolved = value.resolveActiveSource()
        storage.sweep(value.sources.flatMapTo(mutableSetOf()) { it.stateStores })
        _library.value = value
        _source.value = resolved
    }

    private suspend fun write(value: SourceLibrary) = withContext(Dispatchers.IO) {
        val text = documentJson.encodeToString(SourceLibrary.serializer(), value)
        file.parentFile?.mkdirs()
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(text)
        file.replaceWith(tmp)
    }
}
