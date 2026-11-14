package dev.leeeet.wallpapermoe.source

import androidx.core.net.toUri
import dev.leeeet.wallpapermoe.data.Blob
import dev.leeeet.wallpapermoe.data.Blobs
import dev.leeeet.wallpapermoe.data.Store
import dev.leeeet.wallpapermoe.model.Candidate
import dev.leeeet.wallpapermoe.model.CombineMode
import dev.leeeet.wallpapermoe.model.Image
import dev.leeeet.wallpapermoe.model.Pick
import dev.leeeet.wallpapermoe.model.Provider
import dev.leeeet.wallpapermoe.model.Recipe
import dev.leeeet.wallpapermoe.model.Saved
import dev.leeeet.wallpapermoe.model.Sorting
import dev.leeeet.wallpapermoe.model.Source
import dev.leeeet.wallpapermoe.model.closure
import dev.leeeet.wallpapermoe.model.withState
import dev.leeeet.wallpapermoe.net.Http
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import okhttp3.HttpUrl.Companion.toHttpUrl

sealed interface Failure {
    data object NoSource : Failure
    data class Unavailable(val provider: Provider) : Failure
    data class Empty(val provider: Provider) : Failure
    data class Download(val provider: Provider) : Failure
    data object FolderDenied : Failure
    data object FavoritesEmpty : Failure
    data object NothingSaved : Failure
    data object Metered : Failure
    data object Unexpected : Failure
}

class SampleException(val failure: Failure, cause: Throwable? = null) : Exception(failure.toString(), cause)

class Sampler(
    private val store: Store,
    private val blobs: Blobs,
    private val http: Http,
    private val wallhaven: Wallhaven,
    private val nekosBest: NekosBest,
    private val pandora: Pandora,
    private val folders: Folders,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Request(val pins: Blobs.Pins, val online: Boolean, val usePrepared: Boolean)

    suspend fun sample(sourceId: String, pins: Blobs.Pins, online: Boolean): Image =
        guarded { sample(sourceId, Request(pins, online, usePrepared = true), depth = 0) }

    suspend fun refill(roots: Collection<String>, online: Boolean) {
        val nodes = store.current().sources.closure(roots).filter { (it.recipe as? Recipe.KeepReady)?.prepare != null }
        for (node in nodes.reversed()) refill(node.id, online)
    }

    private suspend fun refill(sourceId: String, online: Boolean) {
        while (true) {
            val added = store.pinned { pins ->
                val data = store.current()
                val prepare = (data.source(sourceId)?.recipe as? Recipe.KeepReady)?.prepare ?: return@pinned false
                if (data.state(sourceId).prepared.size >= prepare.count) return@pinned false
                val image = guarded { sample(sourceId, Request(pins, online, usePrepared = false), depth = 0) }
                store.transact(pins) { current ->
                    val queue = current.state(sourceId).prepared
                    val fresh = queue.none { it.id == image.id } && queue.size < prepare.count
                    (if (fresh) current.withState(sourceId) { it.copy(prepared = queue + image) } else current) to fresh
                }
            }
            if (!added) return
        }
    }

    private suspend fun sample(sourceId: String, request: Request, depth: Int): Image {
        if (depth > MAX_DEPTH) throw SampleException(Failure.Unexpected)
        val source = store.current().source(sourceId) ?: throw SampleException(Failure.NoSource)
        return when (val recipe = source.recipe) {
            is Recipe.Wallhaven -> wallhaven(source, recipe, request)
            is Recipe.NekosBest -> nekosBest(source, recipe, request)
            is Recipe.Pandora -> pandora(recipe, request)
            is Recipe.Folder -> folder(source, recipe, request)
            is Recipe.Favorites -> favorites(source, recipe, request)
            is Recipe.KeepReady -> keepReady(source, recipe, request, depth)
            is Recipe.Combine -> combine(source, recipe, request, depth)
        }
    }

    private suspend fun wallhaven(source: Source, recipe: Recipe.Wallhaven, request: Request): Image {
        if (!request.online) throw SampleException(Failure.Metered)
        val queued = store.transact { data ->
            val candidates = data.state(source.id).candidates
            data.withState(source.id) { it.copy(candidates = candidates.drop(1)) } to candidates.firstOrNull()
        }
        val candidate = queued ?: wallhavenPage(source, recipe)
        val blob = download(candidate.url, request, Provider.WALLHAVEN)
        return image("wallhaven:${candidate.id}", blob, Provider.WALLHAVEN, pageUrl = candidate.pageUrl)
    }

    private suspend fun wallhavenPage(source: Source, recipe: Recipe.Wallhaven): Candidate {
        val data = store.current()
        var number = if (recipe.sorting == Sorting.RANDOM) 1 else data.state(source.id).page
        var page = provider(Provider.WALLHAVEN) { wallhaven.search(recipe, data.wallhavenKey, number) }
        if (page.candidates.isEmpty() && number > 1) {
            number = 1
            page = provider(Provider.WALLHAVEN) { wallhaven.search(recipe, data.wallhavenKey, number) }
        }
        val first = page.candidates.firstOrNull() ?: throw SampleException(Failure.Empty(Provider.WALLHAVEN))
        val next = if (recipe.sorting == Sorting.RANDOM || number >= page.lastPage) 1 else number + 1
        store.update { it.withState(source.id) { state -> state.copy(candidates = page.candidates.drop(1), page = next) } }
        return first
    }

    private suspend fun nekosBest(source: Source, recipe: Recipe.NekosBest, request: Request): Image {
        if (!request.online) throw SampleException(Failure.Metered)
        if (recipe.categories.isEmpty()) throw SampleException(Failure.Empty(Provider.NEKOS_BEST))
        val category = recipe.categories[pick(source.id, recipe.categories.size, recipe.pick)]
        val art = provider(Provider.NEKOS_BEST) { nekosBest.art(category) }
            ?: throw SampleException(Failure.Empty(Provider.NEKOS_BEST))
        val blob = download(art.url, request, Provider.NEKOS_BEST)
        return image("nekos-best:${art.url}", blob, Provider.NEKOS_BEST, pageUrl = art.pageUrl, artist = art.artist, artistUrl = art.artistUrl)
    }

    private suspend fun pandora(recipe: Recipe.Pandora, request: Request): Image {
        if (!request.online) throw SampleException(Failure.Metered)
        val candidate = provider(Provider.PANDORA) { pandora.image(recipe.channels) }
            ?: throw SampleException(Failure.Empty(Provider.PANDORA))
        val blob = download(candidate.url, request, Provider.PANDORA)
        return image("pandora:${candidate.id}", blob, Provider.PANDORA)
    }

    private suspend fun folder(source: Source, recipe: Recipe.Folder, request: Request): Image {
        val entries = try {
            folders.images(recipe.treeUri.toUri())
        } catch (e: SecurityException) {
            throw SampleException(Failure.FolderDenied, e)
        }
        if (entries.isEmpty()) throw SampleException(Failure.Empty(Provider.FOLDER))
        val entry = entries[pick(source.id, entries.size, recipe.pick)]
        val blob = try {
            folders.open(entry.uri)?.use { blobs.install(request.pins, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SampleException(Failure.Download(Provider.FOLDER), e)
        } ?: throw SampleException(Failure.Download(Provider.FOLDER))
        return image("folder:${entry.id}", blob, Provider.FOLDER, title = entry.name)
    }

    private suspend fun favorites(source: Source, recipe: Recipe.Favorites, request: Request): Image {
        val favorites = store.current().favorites
        if (favorites.isEmpty()) throw SampleException(Failure.FavoritesEmpty)
        return held(favorites[pick(source.id, favorites.size, recipe.pick)], request)
    }

    private suspend fun keepReady(source: Source, recipe: Recipe.KeepReady, request: Request, depth: Int): Image {
        val input = recipe.sourceId ?: throw SampleException(Failure.NoSource)
        if (request.usePrepared && recipe.prepare != null) {
            val prepared = store.transact(request.pins) { data ->
                val queue = data.state(source.id).prepared
                data.withState(source.id) { it.copy(prepared = queue.drop(1)) } to queue.firstOrNull()
            }
            if (prepared != null) return prepared
        }
        val save = recipe.save ?: return sample(input, request, depth + 1)
        val saved = store.current().state(source.id).saved
        val newest = saved.maxOfOrNull(Saved::savedAt)
        val time = now()
        val refreshMs = save.refresh.hours * 3_600_000L
        val due = refreshMs > 0 && newest != null && (time < newest || time - newest >= refreshMs)
        if (saved.size < save.count || due) {
            val fresh = try {
                sample(input, request, depth + 1)
            } catch (e: SampleException) {
                if (e.failure == Failure.Unexpected || saved.isEmpty()) throw e
                null
            }
            if (fresh != null) {
                store.update { data ->
                    data.withState(source.id) { state ->
                        val kept = state.saved.filterNot { it.image.id == fresh.id } + Saved(fresh, time)
                        state.copy(saved = kept.takeLast(save.count))
                    }
                }
                return fresh
            }
        }
        if (saved.isEmpty()) throw SampleException(Failure.NothingSaved)
        return held(saved[turn(source.id, saved.size)].image, request)
    }

    private suspend fun combine(source: Source, recipe: Recipe.Combine, request: Request, depth: Int): Image {
        val inputs = recipe.sourceIds
        if (inputs.isEmpty()) throw SampleException(Failure.NoSource)
        return when (recipe.mode) {
            CombineMode.RANDOM -> sample(inputs.random(), request, depth + 1)
            CombineMode.IN_TURN -> sample(inputs[turn(source.id, inputs.size)], request, depth + 1)
            CombineMode.FIRST_AVAILABLE -> {
                var first: SampleException? = null
                for (input in inputs) {
                    try {
                        return sample(input, request, depth + 1)
                    } catch (e: SampleException) {
                        if (e.failure == Failure.Unexpected) throw e
                        if (first == null) first = e
                    }
                }
                throw checkNotNull(first)
            }
        }
    }

    private suspend fun pick(sourceId: String, size: Int, pick: Pick): Int = when (pick) {
        Pick.RANDOM -> Random.nextInt(size)
        Pick.IN_TURN -> turn(sourceId, size)
    }

    private suspend fun turn(sourceId: String, size: Int): Int = store.transact { data ->
        val turn = data.state(sourceId).turn.mod(size)
        data.withState(sourceId) { it.copy(turn = (turn + 1).mod(size)) } to turn
    }

    private fun held(image: Image, request: Request): Image =
        if (blobs.hold(request.pins, image.blob)) image else throw SampleException(Failure.Unexpected)

    private suspend fun download(url: String, request: Request, provider: Provider): Blob = try {
        http.download(url.toHttpUrl(), blobs, request.pins)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SampleException(Failure.Download(provider), e)
    }

    private suspend fun <T> provider(provider: Provider, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw SampleException(Failure.Unavailable(provider), e)
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SampleException) {
        throw e
    } catch (e: Exception) {
        throw SampleException(Failure.Unexpected, e)
    }

    private fun image(
        id: String,
        blob: Blob,
        provider: Provider,
        title: String? = null,
        pageUrl: String? = null,
        artist: String? = null,
        artistUrl: String? = null,
    ) = Image(id, blob.hash, blob.mime, blob.width, blob.height, blob.bytes, provider, title, pageUrl, artist, artistUrl)

    private companion object {
        const val MAX_DEPTH = 16
    }
}
