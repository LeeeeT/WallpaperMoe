package dev.leeeet.wallpapermoe

import dev.leeeet.wallpapermoe.data.Prefs
import dev.leeeet.wallpapermoe.source.Providers
import dev.leeeet.wallpapermoe.source.SampleContext
import dev.leeeet.wallpapermoe.source.SampleException
import dev.leeeet.wallpapermoe.source.SampleFailure
import dev.leeeet.wallpapermoe.source.Source
import dev.leeeet.wallpapermoe.source.SourceRepository
import dev.leeeet.wallpapermoe.source.Storage
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/** One rotation: ask the source for an image, put it on the screen, record that it happened. */
class Rotator(
    private val prefs: Prefs,
    private val repository: SourceRepository,
    private val providers: Providers,
    private val storage: Storage,
    private val archive: WallpaperArchive,
    private val currentFile: File,
    private val onWallpaperChanged: () -> Unit,
) {

    enum class Trigger { SCREEN_OFF, MANUAL }

    sealed interface Status {
        data object Idle : Status

        data object Working : Status
        data class Failed(val reason: Failure) : Status
    }

    sealed interface Failure {
        data class Sampling(val reason: SampleFailure) : Failure
        data object Unexpected : Failure
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    /**
     * One rotation at a time: a manual "Change now" while a lock rotation is
     * sampling would race it through the same resolved source graph, and a cache eviction
     * on one side can delete the file the other is still streaming.
     */
    private val running = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var screenOffJob: Job? = null

    fun onScreenOff() {
        if (screenOffJob?.isActive == true) return
        screenOffJob = scope.launch { rotate(Trigger.SCREEN_OFF) }
    }

    fun onScreenOn() {
        screenOffJob?.cancel()
        screenOffJob = null
    }

    fun rotateNow() {
        scope.launch { rotate(Trigger.MANUAL) }
    }

    /** A rotation already under way wins. */
    private suspend fun rotate(trigger: Trigger) {
        if (!running.tryLock()) return
        try {
            doRotate(trigger)
        } finally {
            running.unlock()
        }
    }

    private suspend fun doRotate(trigger: Trigger) {
        val interval = prefs.interval.first()
        if (trigger == Trigger.SCREEN_OFF && interval.minutes > 0) {
            val since = System.currentTimeMillis() - prefs.lastRotationAt.first()
            if (since < interval.minutes * 60_000L) return
        }

        val source = repository.source.value ?: return
        val ctx = SampleContext(
            providers = providers,
            storage = storage,
            archive = archive,
            background = scope,
        )

        _status.value = Status.Working
        val sampled = try {
            source.sample(ctx)
        } catch (e: CancellationException) {
            // Expected: the screen came back.
            _status.value = Status.Idle
            throw e
        } catch (e: SampleException) {
            _status.value = Status.Failed(Failure.Sampling(e.failure))
            return
        } catch (_: Exception) {
            _status.value = Status.Failed(Failure.Unexpected)
            return
        }

        try {
            rememberCurrent(sampled.file)
            onWallpaperChanged()
            prefs.markRotated(System.currentTimeMillis())
            archive.rememberApplied(sampled)
            _status.value = Status.Idle
        } catch (e: CancellationException) {
            _status.value = Status.Idle
            throw e
        } catch (_: Exception) {
            _status.value = Status.Failed(Failure.Unexpected)
        }
    }

    /** A copy, because the source that produced the file may overwrite it on its next sample. */
    private suspend fun rememberCurrent(applied: File) = withContext(Dispatchers.IO) {
        val tmp = File(currentFile.absolutePath + ".tmp")
        applied.copyTo(tmp, overwrite = true)
        currentFile.replaceWith(tmp)
    }
}
