package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.provider.nekosbest.NekosBestApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

typealias SourceId = String

@Serializable
data class SourceLibrary(
    val sources: List<SourceDefinition> = emptyList(),
    val activeSourceId: SourceId? = null,
)

/** One user-visible source. Its recipe may compile to several runtime [Source] objects. */
@Serializable
data class SourceDefinition(
    val id: SourceId,
    val name: String,
    val recipe: SourceRecipe,
)

enum class SourceKind(val slug: String) {
    PANDORA("pandora"),
    WALLHAVEN("wallhaven"),
    NEKOS_BEST("nekos-best"),
    FAVORITES("favorites"),
    KEEP_READY("keep-ready"),
    COMBINE("combine"),
}

@Serializable
enum class CombineMode { RANDOM, ROUND_ROBIN, FALLTHROUGH }

@Serializable
enum class CacheRefresh(val hours: Int) {
    NEVER(0),
    HOURLY(1),
    SIX_HOURS(6),
    DAILY(24),
}

val CACHE_CAPACITY_RANGE = 10..100
const val CACHE_CAPACITY_STEP = 10

@Serializable
data class CacheOptions(val capacity: Int, val refresh: CacheRefresh) {
    init {
        require(capacity in CACHE_CAPACITY_RANGE)
        require(capacity % CACHE_CAPACITY_STEP == 0)
    }
}

@Serializable
data class PrefetchOptions(val depth: Int) {
    init { require(depth > 0) }
}

@Serializable
data class PandoraChannel(
    val id: String,
    val name: String,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }
}

/** Saved authoring recipes. These deliberately do not mirror the runtime [Source] hierarchy. */
@Serializable
sealed interface SourceRecipe {
    @Serializable
    @SerialName("favorites")
    data class Favorites(val mode: SelectionMode) : SourceRecipe

    @Serializable
    @SerialName("pandora")
    data class Pandora(
        val channels: List<PandoraChannel>,
    ) : SourceRecipe {
        init {
            require(channels.isNotEmpty())
            require(channels.distinctBy(PandoraChannel::id).size == channels.size)
        }
    }

    @Serializable
    @SerialName("wallhaven")
    data class Wallhaven(
        val query: String,
        val general: Boolean,
        val anime: Boolean,
        val people: Boolean,
        val sfw: Boolean,
        val sketchy: Boolean,
        val nsfw: Boolean,
        val aspects: Set<WallhavenAspect>,
        val sorting: WallhavenSorting,
        val apiKey: String,
    ) : SourceRecipe {
        init {
            require(general || anime || people)
            require(sfw || sketchy || nsfw)
            require(aspects.isNotEmpty())
            require(!nsfw || apiKey.isNotBlank())
        }
    }

    @Serializable
    @SerialName("nekos_best")
    data class NekosBest(
        val categories: List<String>,
        val mode: SelectionMode,
    ) : SourceRecipe {
        init {
            require(categories.isNotEmpty())
            require(categories.all(NekosBestApi::isCategoryName))
            require(categories.distinct().size == categories.size)
        }
    }

    @Serializable
    @SerialName("keep_ready")
    data class KeepReady(
        val sourceId: SourceId,
        val cache: CacheOptions? = null,
        val prefetch: PrefetchOptions? = null,
    ) : SourceRecipe {
        init { require(sourceId.isNotBlank()) }
    }

    @Serializable
    @SerialName("combine")
    data class Combine(
        val mode: CombineMode,
        val sourceIds: List<SourceId>,
    ) : SourceRecipe {
        init { require(sourceIds.isNotEmpty()) }
    }
}

val SourceRecipe.kind: SourceKind
    get() = when (this) {
        is SourceRecipe.Favorites -> SourceKind.FAVORITES
        is SourceRecipe.Pandora -> SourceKind.PANDORA
        is SourceRecipe.Wallhaven -> SourceKind.WALLHAVEN
        is SourceRecipe.NekosBest -> SourceKind.NEKOS_BEST
        is SourceRecipe.KeepReady -> SourceKind.KEEP_READY
        is SourceRecipe.Combine -> SourceKind.COMBINE
    }

val SourceRecipe.referencedSourceIds: List<SourceId>
    get() = when (this) {
        is SourceRecipe.Favorites,
        is SourceRecipe.Pandora,
        is SourceRecipe.Wallhaven,
        is SourceRecipe.NekosBest,
        -> emptyList()
        is SourceRecipe.KeepReady -> listOf(sourceId)
        is SourceRecipe.Combine -> sourceIds
    }

/** Runtime state folders owned by this visible definition. */
val SourceDefinition.stateStores: Set<String>
    get() = when (val value = recipe) {
        is SourceRecipe.Favorites -> setOf("$id/favorites")
        is SourceRecipe.Pandora -> setOf("$id/pandora")
        is SourceRecipe.Wallhaven -> setOf("$id/wallhaven")
        is SourceRecipe.NekosBest -> setOf("$id/nekos-best")
        is SourceRecipe.Combine -> when (value.mode) {
            CombineMode.ROUND_ROBIN -> setOf("$id/round-robin")
            CombineMode.RANDOM, CombineMode.FALLTHROUGH -> emptySet()
        }
        is SourceRecipe.KeepReady -> buildSet {
            if (value.cache != null) add("$id/cache")
            if (value.prefetch != null) add("$id/prefetch")
        }
    }

sealed interface LibraryProblem {
    data object InvalidId : LibraryProblem
    data object BlankName : LibraryProblem
    data object DuplicateId : LibraryProblem
    data object DuplicateName : LibraryProblem
    data object MissingReference : LibraryProblem
    data object MissingActiveSource : LibraryProblem
    data object ReferenceCycle : LibraryProblem
}

val SourceLibrary.problem: LibraryProblem?
    get() {
        if (sources.any { runCatching { UUID.fromString(it.id) }.isFailure }) {
            return LibraryProblem.InvalidId
        }
        if (sources.any { it.name.isBlank() || it.name != it.name.trim() }) {
            return LibraryProblem.BlankName
        }
        if (sources.map { it.id }.toSet().size != sources.size) return LibraryProblem.DuplicateId
        if (sources.map { it.name }.toSet().size != sources.size) return LibraryProblem.DuplicateName

        val byId = sources.associateBy { it.id }
        if (activeSourceId != null && activeSourceId !in byId) {
            return LibraryProblem.MissingActiveSource
        }
        if (sources.any { definition ->
                definition.recipe.referencedSourceIds.any { it !in byId }
            }
        ) return LibraryProblem.MissingReference

        val visiting = mutableSetOf<SourceId>()
        val visited = mutableSetOf<SourceId>()
        fun cyclic(id: SourceId): Boolean {
            if (id in visiting) return true
            if (!visited.add(id)) return false
            visiting += id
            val found = byId.getValue(id).recipe.referencedSourceIds.any(::cyclic)
            visiting -= id
            return found
        }
        if (sources.any { cyclic(it.id) }) return LibraryProblem.ReferenceCycle

        return null
    }

fun SourceLibrary.referencesTo(id: SourceId): List<SourceDefinition> =
    sources.filter { id in it.recipe.referencedSourceIds }

fun SourceLibrary.upserting(definition: SourceDefinition): SourceLibrary = copy(
    sources = if (sources.any { it.id == definition.id }) {
        sources.map { if (it.id == definition.id) definition else it }
    } else {
        sources + definition
    },
)

/** Compiles the active authoring recipe graph into the existing runtime source implementations. */
fun SourceLibrary.resolveActiveSource(): Source? {
    val active = activeSourceId ?: return null
    if (problem != null) return null
    val definitions = sources.associateBy { it.id }
    val resolved = mutableMapOf<SourceId, Source>()

    fun resolve(id: SourceId): Source = resolved.getOrPut(id) {
        val definition = definitions.getValue(id)
        when (val recipe = definition.recipe) {
            is SourceRecipe.Favorites -> Favorites(recipe.mode, "${definition.id}/favorites")
            is SourceRecipe.Pandora -> Pandora(
                recipe.channels.map(PandoraChannel::id),
                "${definition.id}/pandora",
            )
            is SourceRecipe.Wallhaven -> Wallhaven(
                query = recipe.query,
                general = recipe.general,
                anime = recipe.anime,
                people = recipe.people,
                sfw = recipe.sfw,
                sketchy = recipe.sketchy,
                nsfw = recipe.nsfw,
                aspects = recipe.aspects,
                sorting = recipe.sorting,
                apiKey = recipe.apiKey,
                store = "${definition.id}/wallhaven",
            )
            is SourceRecipe.NekosBest -> NekosBest(
                categories = recipe.categories,
                mode = recipe.mode,
                store = "${definition.id}/nekos-best",
            )
            is SourceRecipe.Combine -> when (recipe.mode) {
                CombineMode.RANDOM -> Random(recipe.sourceIds.map(::resolve))
                CombineMode.ROUND_ROBIN -> RoundRobin(
                    recipe.sourceIds.map(::resolve),
                    "${definition.id}/round-robin",
                )
                CombineMode.FALLTHROUGH -> Fallthrough(recipe.sourceIds.map(::resolve))
            }
            is SourceRecipe.KeepReady -> {
                var source = resolve(recipe.sourceId)
                recipe.cache?.let {
                    source = Cache(
                        source = source,
                        capacity = it.capacity,
                        refreshHours = it.refresh.hours,
                        store = "${definition.id}/cache",
                    )
                }
                recipe.prefetch?.let {
                    source = Prefetch(
                        source = source,
                        depth = it.depth,
                        store = "${definition.id}/prefetch",
                    )
                }
                source
            }
        }
    }

    return resolve(active)
}

fun newSourceId(): SourceId = UUID.randomUUID().toString()
