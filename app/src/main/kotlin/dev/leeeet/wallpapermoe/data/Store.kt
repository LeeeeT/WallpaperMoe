package dev.leeeet.wallpapermoe.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.blobs
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

val moeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class Store(file: File, initial: AppData, private val blobs: Blobs, scope: CoroutineScope) {
    private val dataStore = DataStoreFactory.create(
        serializer = AppDataSerializer(initial),
        corruptionHandler = ReplaceFileCorruptionHandler { initial },
        scope = scope,
        produceFile = { file },
    )

    val data: StateFlow<AppData?> = dataStore.data.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch(Dispatchers.IO) { blobs.sweep(current().blobs()) }
    }

    suspend fun current(): AppData = dataStore.data.first()

    suspend fun update(pins: Blobs.Pins? = null, transform: (AppData) -> AppData): AppData =
        transact(pins) { data -> transform(data).let { it to it } }

    suspend fun <T> transact(pins: Blobs.Pins? = null, block: (AppData) -> Pair<AppData, T>): T {
        var result: Result<T>? = null
        var dropped = emptySet<String>()
        dataStore.updateData { old ->
            val (new, value) = block(old)
            result = Result.success(value)
            dropped = old.blobs() - new.blobs()
            new
        }
        if (pins == null) blobs.discard(dropped) else blobs.adopt(pins, dropped)
        return checkNotNull(result).getOrThrow()
    }

    suspend fun <T> pinned(block: suspend (Blobs.Pins) -> T): T {
        val pins = blobs.pins()
        try {
            return block(pins)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { blobs.release(pins, current().blobs()) }
        }
    }
}

private class AppDataSerializer(override val defaultValue: AppData) : Serializer<AppData> {

    override suspend fun readFrom(input: InputStream): AppData = try {
        moeJson.decodeFromString(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("The state file is not valid", e)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) =
        output.write(moeJson.encodeToString(t).encodeToByteArray())
}
