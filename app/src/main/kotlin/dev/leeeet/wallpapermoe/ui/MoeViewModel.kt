package dev.leeeet.wallpapermoe.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.leeeet.wallpapermoe.MoeApp
import dev.leeeet.wallpapermoe.R
import dev.leeeet.wallpapermoe.model.AppData
import dev.leeeet.wallpapermoe.model.Appearance
import dev.leeeet.wallpapermoe.model.Frequency
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Recipe
import dev.leeeet.wallpapermoe.model.Screen
import dev.leeeet.wallpapermoe.model.Source
import dev.leeeet.wallpapermoe.model.blobs
import dev.leeeet.wallpapermoe.model.Placement
import dev.leeeet.wallpapermoe.model.placing
import dev.leeeet.wallpapermoe.model.withActiveFeed
import dev.leeeet.wallpapermoe.model.withAppearance
import dev.leeeet.wallpapermoe.model.withFavorite
import dev.leeeet.wallpapermoe.model.withFavoriteOrder
import dev.leeeet.wallpapermoe.model.withFeed
import dev.leeeet.wallpapermoe.model.withSource
import dev.leeeet.wallpapermoe.model.withSourceOrder
import dev.leeeet.wallpapermoe.model.withoutSource
import dev.leeeet.wallpapermoe.source.Pandora
import dev.leeeet.wallpapermoe.service.MoeWallpaperService
import dev.leeeet.wallpapermoe.service.Sharing
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Storage(val shown: Long, val favorites: Long, val ready: Long)

data class Message(@StringRes val text: Int, val onResult: ((undone: Boolean) -> Unit)? = null)

class MoeViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = (application as MoeApp).graph
    private val sharing = Sharing(application, graph.blobs)
    private val activeState = MutableStateFlow(MoeWallpaperService.isActive(application))

    val data: StateFlow<AppData?> = graph.store.data
    val status = graph.changer.status
    val active: StateFlow<Boolean> = activeState
    val messages = Channel<Message>(Channel.BUFFERED)

    val storage: StateFlow<Storage?> = data.filterNotNull()
        .map { data ->
            val favorites = data.favorites.mapTo(HashSet(), Image::blob)
            val shown = (data.shown.values.map { it.image.blob } + data.history.map(Image::blob)).toSet() - favorites
            val ready = data.states.values.flatMapTo(HashSet()) { state ->
                state.saved.map { it.image.blob } + state.prepared.map(Image::blob)
            } - shown - favorites
            Storage(graph.blobs.size(shown), graph.blobs.size(favorites), graph.blobs.size(ready))
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshActive() {
        activeState.value = MoeWallpaperService.isActive(getApplication())
    }

    fun file(image: Image): File = graph.blobs.file(image.blob)

    fun changeNow(screen: Screen) = graph.changer.changeNow(screen)

    fun chooseSource(screen: Screen, sourceId: String) {
        viewModelScope.launch {
            graph.store.update { it.withActiveFeed(screen) { feed -> feed.copy(sourceId = sourceId) } }
            graph.changer.start()
        }
    }

    fun saveSource(source: Source) = edit { it.withSource(source) }

    fun place(sourceId: String, placement: Placement) {
        viewModelScope.launch {
            graph.store.update { it.placing(sourceId, placement) }
            graph.changer.start()
        }
    }

    fun deleteSource(id: String) = edit { it.withoutSource(id) }

    fun orderSources(ids: List<String>) = edit { it.withSourceOrder(ids) }

    fun setFrequency(screen: Screen, frequency: Frequency) =
        edit { it.withActiveFeed(screen) { feed -> feed.copy(frequency = frequency) } }

    fun setAppearance(screen: Screen, appearance: Appearance) = edit { it.withAppearance(screen, appearance) }

    fun setLockSeparate(separate: Boolean) = edit { it.withFeed(Screen.LOCK, if (separate) it.lock ?: it.home else null) }

    fun setUnmeteredOnly(enabled: Boolean) = edit { it.copy(unmeteredOnly = enabled) }

    fun setWallhavenKey(key: String) = edit { it.copy(wallhavenKey = key.trim()) }

    fun toggleFavorite(image: Image) {
        val data = data.value ?: return
        if (data.isFavorite(image)) removeFavorite(image) else edit { it.withFavorite(image, true) }
    }

    fun removeFavorite(image: Image) {
        val order = data.value?.favorites?.map(Image::id) ?: return
        val pins = graph.blobs.pins()
        viewModelScope.launch {
            graph.store.update(pins) { it.withFavorite(image, false) }
            messages.send(
                Message(R.string.favorite_removed) { undone ->
                    viewModelScope.launch {
                        if (undone) graph.store.update(pins) { it.withFavorite(image, true).withFavoriteOrder(order) }
                        withContext(Dispatchers.IO) { graph.blobs.release(pins, graph.store.current().blobs()) }
                    }
                },
            )
        }
    }

    fun orderFavorites(ids: List<String>) = edit { it.withFavoriteOrder(ids) }

    fun clearHistory() = edit { it.copy(history = emptyList()) }

    fun show(screen: Screen, image: Image) {
        graph.changer.show(screen, image)
        messages.trySend(Message(R.string.message_wallpaper_set))
    }

    fun notify(@StringRes text: Int) {
        messages.trySend(Message(text))
    }

    fun shareIntent(image: Image): Intent = sharing.shareIntent(image)

    fun saveToGallery(image: Image) {
        viewModelScope.launch {
            val text = try {
                if (sharing.saveToGallery(image)) R.string.gallery_save_success else R.string.gallery_save_already_saved
            } catch (e: IOException) {
                R.string.gallery_save_failure
            }
            messages.send(Message(text))
        }
    }

    fun export(target: Uri) = report(R.string.message_export_done, R.string.message_export_failed) {
        graph.backups.export(target)
    }

    fun import(source: Uri) = report(R.string.message_import_done, R.string.message_import_failed) {
        graph.backups.import(source)
        graph.changer.start()
    }

    fun folder(tree: Uri, recipe: Recipe.Folder): Recipe.Folder {
        graph.folders.keep(tree)
        return recipe.copy(treeUri = tree.toString(), label = graph.folders.label(tree))
    }

    suspend fun channels(): List<Pandora.Listing>? = load { graph.pandora.channels() }

    suspend fun categories(): List<String>? = load { graph.nekosBest.categories() }

    private fun edit(transform: (AppData) -> AppData) {
        viewModelScope.launch { graph.store.update(transform = transform) }
    }

    private fun report(@StringRes done: Int, @StringRes failed: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            val message = try {
                block()
                done
            } catch (e: IOException) {
                failed
            } catch (e: SecurityException) {
                failed
            }
            messages.send(Message(message))
        }
    }

    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
