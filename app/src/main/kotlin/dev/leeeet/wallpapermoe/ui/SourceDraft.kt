package dev.leeeet.wallpapermoe.ui

import dev.leeeet.wallpapermoe.source.CacheOptions
import dev.leeeet.wallpapermoe.source.CombineMode
import dev.leeeet.wallpapermoe.source.SelectionMode
import dev.leeeet.wallpapermoe.source.LibraryProblem
import dev.leeeet.wallpapermoe.source.PrefetchOptions
import dev.leeeet.wallpapermoe.source.SourceDefinition
import dev.leeeet.wallpapermoe.source.SourceId
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.SourceLibrary
import dev.leeeet.wallpapermoe.source.SourceRecipe
import dev.leeeet.wallpapermoe.source.WallhavenSorting
import dev.leeeet.wallpapermoe.source.kind
import dev.leeeet.wallpapermoe.source.problem
import dev.leeeet.wallpapermoe.source.referencedSourceIds
import dev.leeeet.wallpapermoe.source.upserting
import kotlin.math.abs

/** Incomplete user input for one visible source. Persistence identity lives in the editor session. */
data class SourceDraft(
    val name: String,
    val recipe: SourceRecipeDraft,
)

/** How often a cache is allowed to pull one more image in. */
enum class CacheRefresh(val hours: Int) {
    HOURLY(1),
    SIX_HOURS(6),
    DAILY(24),
    ;

    companion object {
        /** Snaps a stored interval onto the choices the editor offers. */
        fun nearest(hours: Int): CacheRefresh = entries.minBy { abs(it.hours - hours) }
    }
}

sealed interface SourceRecipeDraft {
    data class Favorites(
        val mode: SelectionMode = SelectionMode.RANDOM,
    ) : SourceRecipeDraft

    data class Pandora(
        val channelIds: List<String> = emptyList(),
        val channelLabels: List<String> = emptyList(),
    ) : SourceRecipeDraft

    data class Wallhaven(
        val query: String = "",
        val general: Boolean = true,
        val anime: Boolean = false,
        val people: Boolean = false,
        val sfw: Boolean = true,
        val sketchy: Boolean = false,
        val nsfw: Boolean = false,
        val sorting: WallhavenSorting = WallhavenSorting.RANDOM,
        val apiKey: String = "",
    ) : SourceRecipeDraft

    data class NekosBest(
        val categories: List<String> = emptyList(),
        val mode: SelectionMode = SelectionMode.RANDOM,
    ) : SourceRecipeDraft

    data class KeepReady(
        val sourceId: SourceId? = null,
        val cacheEnabled: Boolean = false,
        val cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
        val cacheRefresh: CacheRefresh = CacheRefresh.DAILY,
        val prefetchEnabled: Boolean = false,
        val prefetchDepth: Int = DEFAULT_PREFETCH_DEPTH,
    ) : SourceRecipeDraft

    data class Combine(
        val mode: CombineMode = CombineMode.RANDOM,
        val sourceIds: List<SourceId> = emptyList(),
    ) : SourceRecipeDraft
}

val SourceRecipeDraft.kind: SourceKind
    get() = when (this) {
        is SourceRecipeDraft.Favorites -> SourceKind.FAVORITES
        is SourceRecipeDraft.Pandora -> SourceKind.PANDORA
        is SourceRecipeDraft.Wallhaven -> SourceKind.WALLHAVEN
        is SourceRecipeDraft.NekosBest -> SourceKind.NEKOS_BEST
        is SourceRecipeDraft.KeepReady -> SourceKind.KEEP_READY
        is SourceRecipeDraft.Combine -> SourceKind.COMBINE
    }

sealed interface DraftProblem {
    data object BlankName : DraftProblem
    data object DuplicateName : DraftProblem
    data object PandoraChannelMissing : DraftProblem
    data object WallhavenCategoryMissing : DraftProblem
    data object WallhavenPurityMissing : DraftProblem
    data object WallhavenNsfwKeyMissing : DraftProblem
    data object NekosBestCategoryMissing : DraftProblem
    data object CacheCapacityInvalid : DraftProblem
    data object PrefetchDepthInvalid : DraftProblem
    data object KeepReadySourceMissing : DraftProblem
    data object CombineEmpty : DraftProblem
    data object MissingReference : DraftProblem
    data object ReferenceCycle : DraftProblem
}

fun SourceDraft.validationProblem(ownerId: SourceId, library: SourceLibrary): DraftProblem? {
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return DraftProblem.BlankName
    if (library.sources.any { it.id != ownerId && it.name == normalizedName }) {
        return DraftProblem.DuplicateName
    }

    val recipeProblem = when (val value = recipe) {
        is SourceRecipeDraft.Favorites -> null
        is SourceRecipeDraft.Pandora ->
            if (value.channelIds.isEmpty()) DraftProblem.PandoraChannelMissing else null

        is SourceRecipeDraft.Wallhaven -> when {
            !value.general && !value.anime && !value.people -> DraftProblem.WallhavenCategoryMissing
            !value.sfw && !value.sketchy && !value.nsfw -> DraftProblem.WallhavenPurityMissing
            value.nsfw && value.apiKey.isBlank() -> DraftProblem.WallhavenNsfwKeyMissing
            else -> null
        }

        is SourceRecipeDraft.NekosBest ->
            if (value.categories.isEmpty()) DraftProblem.NekosBestCategoryMissing else null

        is SourceRecipeDraft.KeepReady -> when {
            value.sourceId.isNullOrBlank() -> DraftProblem.KeepReadySourceMissing
            value.cacheEnabled && value.cacheCapacity < 1 -> DraftProblem.CacheCapacityInvalid
            value.prefetchEnabled && value.prefetchDepth < 1 -> DraftProblem.PrefetchDepthInvalid
            else -> null
        }
        is SourceRecipeDraft.Combine ->
            if (value.sourceIds.isEmpty()) DraftProblem.CombineEmpty else null
    }
    if (recipeProblem != null) return recipeProblem

    val definition = toDefinition(ownerId)
    return when (library.upserting(definition).problem) {
        null -> null
        LibraryProblem.MissingReference -> DraftProblem.MissingReference
        LibraryProblem.ReferenceCycle -> DraftProblem.ReferenceCycle
        LibraryProblem.BlankName -> DraftProblem.BlankName
        LibraryProblem.DuplicateName -> DraftProblem.DuplicateName
        else -> DraftProblem.MissingReference
    }
}

fun SourceDraft.toDefinition(id: SourceId): SourceDefinition = SourceDefinition(
    id = id,
    name = name.trim(),
    recipe = recipe.toRecipe(),
)

private fun SourceRecipeDraft.toRecipe(): SourceRecipe = when (this) {
    is SourceRecipeDraft.Favorites -> SourceRecipe.Favorites(mode)
    is SourceRecipeDraft.Pandora -> SourceRecipe.Pandora(channelIds, channelLabels)
    is SourceRecipeDraft.Wallhaven -> SourceRecipe.Wallhaven(
        query, general, anime, people, sfw, sketchy, nsfw, sorting, apiKey,
    )
    is SourceRecipeDraft.NekosBest -> SourceRecipe.NekosBest(categories, mode)
    is SourceRecipeDraft.KeepReady -> SourceRecipe.KeepReady(
        sourceId = requireNotNull(sourceId),
        cache = if (cacheEnabled) CacheOptions(cacheCapacity, cacheRefresh.hours) else null,
        prefetch = if (prefetchEnabled) PrefetchOptions(prefetchDepth) else null,
    )
    is SourceRecipeDraft.Combine -> SourceRecipe.Combine(mode, sourceIds)
}

fun SourceDefinition.toDraft(): SourceDraft = SourceDraft(name, recipe.toDraft())

private fun SourceRecipe.toDraft(): SourceRecipeDraft = when (this) {
    is SourceRecipe.Favorites -> SourceRecipeDraft.Favorites(mode)
    is SourceRecipe.Pandora -> SourceRecipeDraft.Pandora(channelIds.toList(), channelLabels.toList())
    is SourceRecipe.Wallhaven -> SourceRecipeDraft.Wallhaven(
        query, general, anime, people, sfw, sketchy, nsfw, sorting, apiKey,
    )
    is SourceRecipe.NekosBest -> SourceRecipeDraft.NekosBest(categories.toList(), mode)
    is SourceRecipe.KeepReady -> SourceRecipeDraft.KeepReady(
        sourceId = sourceId,
        cacheEnabled = cache != null,
        cacheCapacity = cache?.capacity?.let(::snapCapacity) ?: DEFAULT_CACHE_CAPACITY,
        cacheRefresh = cache?.refreshHours?.let(CacheRefresh::nearest) ?: CacheRefresh.DAILY,
        prefetchEnabled = prefetch != null,
        prefetchDepth = prefetch?.depth ?: DEFAULT_PREFETCH_DEPTH,
    )
    is SourceRecipe.Combine -> SourceRecipeDraft.Combine(mode, sourceIds.toList())
}

fun newSourceDraft(kind: SourceKind, library: SourceLibrary): SourceDraft = SourceDraft(
    name = library.nextName(kind),
    recipe = when (kind) {
        SourceKind.FAVORITES -> SourceRecipeDraft.Favorites()
        SourceKind.PANDORA -> SourceRecipeDraft.Pandora()
        SourceKind.WALLHAVEN -> SourceRecipeDraft.Wallhaven()
        SourceKind.NEKOS_BEST -> SourceRecipeDraft.NekosBest()
        SourceKind.KEEP_READY -> SourceRecipeDraft.KeepReady()
        SourceKind.COMBINE -> SourceRecipeDraft.Combine()
    },
)

private fun SourceLibrary.nextName(kind: SourceKind): String {
    val names = sources.mapTo(mutableSetOf()) { it.name }
    var suffix = 1
    while ("${kind.slug}-$suffix" in names) suffix++
    return "${kind.slug}-$suffix"
}

fun SourceLibrary.canReference(ownerId: SourceId, candidateId: SourceId): Boolean {
    if (ownerId == candidateId) return false
    val byId = sources.associateBy { it.id }
    val visited = mutableSetOf<SourceId>()
    fun reachesOwner(id: SourceId): Boolean {
        if (id == ownerId) return true
        if (!visited.add(id)) return false
        return byId[id]?.recipe?.referencedSourceIds.orEmpty().any(::reachesOwner)
    }
    return !reachesOwner(candidateId)
}

/** The cache size the editor's slider can express, in steps of [CACHE_CAPACITY_STEP]. */
val CACHE_CAPACITY_RANGE = 10..100
const val CACHE_CAPACITY_STEP = 10

/** Rounds a stored capacity onto a slider stop, so the editor always shows a reachable value. */
private fun snapCapacity(capacity: Int): Int {
    val clamped = capacity.coerceIn(CACHE_CAPACITY_RANGE)
    val stops = CACHE_CAPACITY_RANGE.step(CACHE_CAPACITY_STEP)
    return stops.minBy { abs(it - clamped) }
}

private const val DEFAULT_CACHE_CAPACITY = 20
private const val DEFAULT_PREFETCH_DEPTH = 3
