package dev.leeeet.wallpapermoe.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.leeeet.wallpapermoe.Graph
import dev.leeeet.wallpapermoe.Rotator
import dev.leeeet.wallpapermoe.data.MoeSettings
import dev.leeeet.wallpapermoe.data.RotationInterval
import dev.leeeet.wallpapermoe.provider.pandora.PandoraApi
import dev.leeeet.wallpapermoe.service.Engine
import dev.leeeet.wallpapermoe.source.Source
import dev.leeeet.wallpapermoe.source.SourceId
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.SourceLibrary
import dev.leeeet.wallpapermoe.source.newSourceId
import dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class Tab { NOW, SOURCE, SETTINGS }

data class UiState(
    val settings: MoeSettings,
    val source: Source?,
    val sourceLibrary: SourceLibrary,
    val status: Rotator.Status,
    val engineBlocked: Boolean,
    val currentWallpaper: File?,
    val wallpaperVersion: Long,
    val history: List<SavedWallpaper>,
    val favorites: List<SavedWallpaper>,
    val tab: Tab,
    val sourceDraftId: SourceId?,
    val sourceDraft: SourceDraft?,
    val sourceEditorOpen: Boolean,
    val sourceDraftDirty: Boolean,
    val pendingSourceExit: PendingSourceExit?,
)

sealed interface PendingSourceExit {
    data class SelectTab(val tab: Tab) : PendingSourceExit
    data object BackEditor : PendingSourceExit
}

private data class EditorSession(
    val id: SourceId,
    val original: SourceDraft?,
    val draft: SourceDraft,
    val attachToParent: Boolean = false,
) {
    val dirty: Boolean get() = draft != original
}

private data class SourceEditing(
    val stack: List<EditorSession> = emptyList(),
    val pendingExit: PendingSourceExit? = null,
) {
    val current: EditorSession? get() = stack.lastOrNull()
    val dirty: Boolean get() = stack.any(EditorSession::dirty)
}

sealed interface RemoteListState<out T> {
    data object Idle : RemoteListState<Nothing>
    data object Loading : RemoteListState<Nothing>
    data class Ready<T>(val items: List<T>) : RemoteListState<T>
    data object Failed : RemoteListState<Nothing>
}

class MoeViewModel(app: Application) : AndroidViewModel(app) {

    private val tab = MutableStateFlow(Tab.NOW)
    private val sourceEditing = MutableStateFlow(SourceEditing())

    private val _channels = MutableStateFlow<RemoteListState<PandoraApi.Channel>>(RemoteListState.Idle)
    val channels: StateFlow<RemoteListState<PandoraApi.Channel>> = _channels

    private val _nekosBestCategories = MutableStateFlow<RemoteListState<String>>(RemoteListState.Idle)
    val nekosBestCategories: StateFlow<RemoteListState<String>> = _nekosBestCategories

    val state: StateFlow<UiState> = combine(
        combine(
            Graph.prefs.settings,
            Graph.sources.source,
            Graph.sources.library,
            Graph.rotator.status,
            Engine.blocked,
        ) { settings, source, library, status, blocked ->
            CoreState(settings, source, library, status, blocked)
        },
        combine(Graph.prefs.lastRotationAt, tab, sourceEditing) { rotation, selectedTab, editing ->
            Triple(rotation, selectedTab, editing)
        },
        Graph.archive.history,
        Graph.archive.favorites,
    ) { core, navigation, history, favorites ->
        val (lastRotation, currentTab, editing) = navigation
        UiState(
            settings = core.settings,
            source = core.source,
            sourceLibrary = core.library,
            status = core.status,
            engineBlocked = core.engineBlocked,
            currentWallpaper = Graph.currentWallpaper.takeIf { it.exists() && it.length() > 0 },
            wallpaperVersion = lastRotation,
            history = history,
            favorites = favorites,
            tab = currentTab,
            sourceDraftId = editing.current?.id,
            sourceDraft = editing.current?.draft,
            sourceEditorOpen = editing.stack.isNotEmpty(),
            sourceDraftDirty = editing.dirty,
            pendingSourceExit = editing.pendingExit,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(
            settings = MoeSettings(true, true, RotationInterval.EVERY_LOCK, ""),
            source = null,
            sourceLibrary = SourceLibrary(),
            status = Rotator.Status.Idle,
            engineBlocked = false,
            currentWallpaper = null,
            wallpaperVersion = 0,
            history = emptyList(),
            favorites = emptyList(),
            tab = Tab.NOW,
            sourceDraftId = null,
            sourceDraft = null,
            sourceEditorOpen = false,
            sourceDraftDirty = false,
            pendingSourceExit = null,
        ),
    )

    fun selectTab(value: Tab) {
        if (value == tab.value) return
        if (tab.value == Tab.SOURCE && sourceEditing.value.dirty) {
            sourceEditing.value = sourceEditing.value.copy(
                pendingExit = PendingSourceExit.SelectTab(value),
            )
            return
        }
        sourceEditing.value = SourceEditing()
        tab.value = value
    }

    fun createSource(kind: SourceKind, attachToCurrent: Boolean = false) {
        val editing = sourceEditing.value
        val draft = newSourceDraft(kind, Graph.sources.library.value)
        sourceEditing.value = editing.copy(
            stack = editing.stack + EditorSession(newSourceId(), null, draft, attachToCurrent),
        )
    }

    fun editSource(id: SourceId) {
        val definition = Graph.sources.library.value.sources.firstOrNull { it.id == id } ?: return
        val draft = definition.toDraft()
        val editing = sourceEditing.value
        sourceEditing.value = editing.copy(
            stack = editing.stack + EditorSession(definition.id, draft, draft),
        )
    }

    fun updateSourceDraft(value: SourceDraft) {
        val editing = sourceEditing.value
        if (editing.stack.isEmpty()) return
        sourceEditing.value = editing.copy(
            stack = editing.stack.dropLast(1) + editing.stack.last().copy(draft = value),
        )
    }

    fun saveSource() {
        val editing = sourceEditing.value
        val session = editing.current ?: return
        val library = Graph.sources.library.value
        if (session.draft.validationProblem(session.id, library) != null) return
        viewModelScope.launch {
            if (!Graph.sources.upsert(session.draft.toDefinition(session.id))) return@launch

            val stack = sourceEditing.value.stack
            if (stack.lastOrNull()?.id != session.id) return@launch
            val remaining = stack.dropLast(1).toMutableList()
            if (remaining.isNotEmpty() && session.attachToParent) {
                val parent = remaining.last()
                remaining[remaining.lastIndex] = parent.copy(
                    draft = parent.draft.attach(session.id),
                )
            }
            sourceEditing.value = SourceEditing(stack = remaining)
        }
    }

    private fun SourceDraft.attach(id: SourceId): SourceDraft = copy(
        recipe = when (val value = recipe) {
            is SourceRecipeDraft.KeepReady -> value.copy(sourceId = id)
            is SourceRecipeDraft.Combine -> value.copy(sourceIds = value.sourceIds + id)
            else -> value
        },
    )

    fun discardSourceChanges() {
        val editing = sourceEditing.value
        if (editing.stack.isEmpty()) return
        sourceEditing.value = editing.copy(stack = editing.stack.dropLast(1))
    }

    fun requestEditorBack() {
        val editing = sourceEditing.value
        val current = editing.current ?: return
        sourceEditing.value = if (current.dirty) {
            editing.copy(pendingExit = PendingSourceExit.BackEditor)
        } else {
            editing.copy(stack = editing.stack.dropLast(1))
        }
    }

    fun cancelSourceExit() {
        sourceEditing.value = sourceEditing.value.copy(pendingExit = null)
    }

    fun discardAndExit() {
        val editing = sourceEditing.value
        when (val destination = editing.pendingExit ?: return) {
            is PendingSourceExit.SelectTab -> {
                sourceEditing.value = SourceEditing()
                tab.value = destination.tab
            }
            PendingSourceExit.BackEditor -> {
                sourceEditing.value = editing.copy(
                    stack = editing.stack.dropLast(1),
                    pendingExit = null,
                )
            }
        }
    }

    fun setActiveSource(id: SourceId) = viewModelScope.launch { Graph.sources.setActive(id) }

    fun reorderSources(ids: List<SourceId>) = viewModelScope.launch {
        Graph.sources.reorderSources(ids)
    }

    fun deleteSource(id: SourceId) = viewModelScope.launch { Graph.sources.delete(id) }

    fun loadChannels(force: Boolean = false) = viewModelScope.launch {
        if (!force && _channels.value is RemoteListState.Ready) return@launch
        _channels.value = RemoteListState.Loading
        _channels.value = try {
            RemoteListState.Ready(Graph.providers.pandora.channels())
        } catch (_: Exception) {
            RemoteListState.Failed
        }
    }

    fun loadNekosBestCategories(force: Boolean = false) = viewModelScope.launch {
        if (!force && _nekosBestCategories.value is RemoteListState.Ready) return@launch
        _nekosBestCategories.value = RemoteListState.Loading
        _nekosBestCategories.value = try {
            RemoteListState.Ready(Graph.providers.nekosBest.imageCategories())
        } catch (_: Exception) {
            RemoteListState.Failed
        }
    }

    fun setApplyToLock(v: Boolean) = viewModelScope.launch { Graph.prefs.setApplyToLock(v) }
    fun setApplyToHome(v: Boolean) = viewModelScope.launch { Graph.prefs.setApplyToHome(v) }
    fun setInterval(v: RotationInterval) = viewModelScope.launch { Graph.prefs.setInterval(v) }

    fun rotateNow() = viewModelScope.launch {
        if (Graph.prefs.settings.first().enabled) Engine.rotateNow(getApplication())
    }

    fun addFavorite(wallpaper: SavedWallpaper) = viewModelScope.launch {
        runCatching { Graph.archive.addFavorite(wallpaper) }
    }

    fun removeFavorite(id: String) = viewModelScope.launch {
        runCatching { Graph.archive.removeFavorite(id) }
    }

    fun reorderFavorites(ids: List<String>) = viewModelScope.launch {
        runCatching { Graph.archive.reorderFavorites(ids) }
    }

    fun saveToGallery(wallpaper: SavedWallpaper, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val success = try {
            Graph.gallerySaver.save(wallpaper)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        onResult(success)
    }

    fun removeFavorites(ids: Set<String>) = viewModelScope.launch {
        runCatching { Graph.archive.removeFavorites(ids) }
    }

    private data class CoreState(
        val settings: MoeSettings,
        val source: Source?,
        val library: SourceLibrary,
        val status: Rotator.Status,
        val engineBlocked: Boolean,
    )
}
