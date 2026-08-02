package dev.leeeet.wallpapermoe

import dev.leeeet.wallpapermoe.data.Prefs
import dev.leeeet.wallpapermoe.image.ImagePipeline
import dev.leeeet.wallpapermoe.source.Providers
import dev.leeeet.wallpapermoe.source.SampleContext
import dev.leeeet.wallpapermoe.source.SampleException
import dev.leeeet.wallpapermoe.source.SampleFailure
import dev.leeeet.wallpapermoe.source.Source
import dev.leeeet.wallpapermoe.source.SourceRepository
import dev.leeeet.wallpapermoe.source.Storage
import dev.leeeet.wallpapermoe.wallpaper.ApplyResult
import dev.leeeet.wallpapermoe.wallpaper.ApplyFailure
import dev.leeeet.wallpapermoe.wallpaper.ApplyTargets
import dev.leeeet.wallpapermoe.wallpaper.WallpaperApplier
import dev.leeeet.wallpapermoe.wallpaper.WallpaperArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

/** One rotation: ask the source for an image, put it on the screen, record that it happened. */
class Rotator(
    private val prefs: Prefs,
    private val repository: SourceRepository,
    private val applier: WallpaperApplier,
    private val images: ImagePipeline,
    private val providers: Providers,
    private val storage: Storage,
    private val archive: WallpaperArchive,
    private val currentFile: File,
) {

    enum class Trigger { SCREEN_OFF, MANUAL }

    sealed interface Status {
        data object Idle : Status

        data object Working : Status
        data class Failed(val reason: Failure) : Status
    }

    sealed interface Failure {
        data class Sampling(val reason: SampleFailure) : Failure
        data class Applying(val reason: ApplyFailure) : Failure
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

    /** A rotation already under way wins. */
    suspend fun rotate(
        trigger: Trigger,
        background: CoroutineScope,
    ) {
        if (!running.tryLock()) return
        try {
            doRotate(trigger, background)
        } finally {
            running.unlock()
        }
    }

    private suspend fun doRotate(
        trigger: Trigger,
        background: CoroutineScope,
    ) {
        val settings = prefs.current()
        if (!settings.enabled) return

        if (trigger == Trigger.SCREEN_OFF && settings.interval.minutes > 0) {
            val since = System.currentTimeMillis() - prefs.lastRotationAt.first()
            if (since < settings.interval.minutes * 60_000L) return
        }

        val source = repository.source.value ?: return
        val (width, height) = applier.targetSize()
        val ctx = SampleContext(
            widthPx = width,
            heightPx = height,
            images = images,
            providers = providers,
            storage = storage,
            archive = archive,
            background = background,
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

        val targets = ApplyTargets(lock = settings.applyToLock, home = settings.applyToHome)
        _status.value = when (val result = applier.apply(
            sampled.prepared,
            targets,
            onlyWhileScreenOff = trigger == Trigger.SCREEN_OFF,
        )) {
            is ApplyResult.Success -> {
                prefs.markRotated(System.currentTimeMillis())
                rememberCurrent(sampled.prepared)
                runCatching { archive.rememberApplied(sampled) }
                Status.Idle
            }

            is ApplyResult.Skipped -> Status.Idle
            is ApplyResult.Failed -> Status.Failed(Failure.Applying(result.reason))
        }
    }

    /** A copy, because the source that produced the file may overwrite it on its next sample. */
    private suspend fun rememberCurrent(applied: File) = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(currentFile.absolutePath + ".tmp")
            applied.copyTo(tmp, overwrite = true)
            tmp.renameTo(currentFile)
        }
        Unit
    }
}
