package dev.leeeet.wallpapermoe.source

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
        storage.prepare()
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching SourceLibrary()
                documentJson.decodeFromString(SourceLibrary.serializer(), file.readText())
            }.getOrNull()
        }
        // Documents from earlier authoring schemas and malformed/incomplete documents are
        // intentionally discarded: this project has no configuration compatibility contract.
        val accepted = loaded?.takeIf { it.problem == null } ?: SourceLibrary()
        if (file.exists()) write(accepted)
        publish(accepted)
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
        if (next.problem != null) return@withLock
        persistAndPublish(next)
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
        _library.value = value
        _source.value = value.resolveActiveSource()
        storage.sweep(value.sources.flatMapTo(mutableSetOf()) { it.stateStores })
    }

    private suspend fun write(value: SourceLibrary) = withContext(Dispatchers.IO) {
        val text = documentJson.encodeToString(SourceLibrary.serializer(), value)
        file.parentFile?.mkdirs()
        val tmp = File(file.absolutePath + ".tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file))
    }
}
