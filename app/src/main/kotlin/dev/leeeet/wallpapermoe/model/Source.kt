package dev.leeeet.wallpapermoe.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Source(val id: String, val name: String, val recipe: Recipe)

enum class Kind { WALLHAVEN, NEKOS_BEST, PANDORA, FOLDER, FAVORITES, KEEP_READY, COMBINE }

@Serializable
enum class Pick { RANDOM, IN_TURN }

@Serializable
enum class CombineMode { RANDOM, IN_TURN, FIRST_AVAILABLE }

@Serializable
enum class Sorting(val apiValue: String) {
    RANDOM("random"),
    NEWEST("date_added"),
    TOPLIST("toplist"),
    VIEWS("views"),
    FAVORITES("favorites"),
}

@Serializable
enum class Refresh(val hours: Int) { NEVER(0), HOURLY(1), SIX_HOURS(6), DAILY(24) }

@Serializable
data class Save(val count: Int = 10, val refresh: Refresh = Refresh.NEVER) {
    companion object {
        val COUNT = 10..100
        const val STEP = 10
    }
}

@Serializable
data class Prepare(val count: Int = 3) {
    companion object {
        val COUNT = 1..10
    }
}

@Serializable
data class Channel(val id: String, val name: String)

@Serializable
sealed interface Recipe {
    val kind: Kind

    @Serializable
    @SerialName("wallhaven")
    data class Wallhaven(
        val query: String = "",
        val general: Boolean = true,
        val anime: Boolean = true,
        val people: Boolean = false,
        val sfw: Boolean = true,
        val sketchy: Boolean = false,
        val nsfw: Boolean = false,
        val portrait: Boolean = true,
        val landscape: Boolean = false,
        val sorting: Sorting = Sorting.RANDOM,
    ) : Recipe {
        override val kind get() = Kind.WALLHAVEN
    }

    @Serializable
    @SerialName("nekos_best")
    data class NekosBest(
        val categories: List<String> = emptyList(),
        val pick: Pick = Pick.RANDOM,
    ) : Recipe {
        override val kind get() = Kind.NEKOS_BEST
    }

    @Serializable
    @SerialName("pandora")
    data class Pandora(val channels: List<Channel> = emptyList()) : Recipe {
        override val kind get() = Kind.PANDORA
    }

    @Serializable
    @SerialName("folder")
    data class Folder(
        val treeUri: String = "",
        val label: String = "",
        val pick: Pick = Pick.RANDOM,
    ) : Recipe {
        override val kind get() = Kind.FOLDER
    }

    @Serializable
    @SerialName("favorites")
    data class Favorites(val pick: Pick = Pick.RANDOM) : Recipe {
        override val kind get() = Kind.FAVORITES
    }

    @Serializable
    @SerialName("keep_ready")
    data class KeepReady(
        val sourceId: String? = null,
        val save: Save? = null,
        val prepare: Prepare? = Prepare(),
    ) : Recipe {
        override val kind get() = Kind.KEEP_READY
    }

    @Serializable
    @SerialName("combine")
    data class Combine(
        val sourceIds: List<String> = emptyList(),
        val mode: CombineMode = CombineMode.RANDOM,
    ) : Recipe {
        override val kind get() = Kind.COMBINE
    }
}

fun Kind.newRecipe(): Recipe = when (this) {
    Kind.WALLHAVEN -> Recipe.Wallhaven()
    Kind.NEKOS_BEST -> Recipe.NekosBest()
    Kind.PANDORA -> Recipe.Pandora()
    Kind.FOLDER -> Recipe.Folder()
    Kind.FAVORITES -> Recipe.Favorites()
    Kind.KEEP_READY -> Recipe.KeepReady()
    Kind.COMBINE -> Recipe.Combine()
}

val Kind.slug: String
    get() = name.lowercase().replace('_', '-')

val Recipe.inputs: List<String>
    get() = when (this) {
        is Recipe.KeepReady -> listOfNotNull(sourceId)
        is Recipe.Combine -> sourceIds
        else -> emptyList()
    }

@Serializable
data class Candidate(val id: String, val url: String, val pageUrl: String? = null)

@Serializable
data class Saved(val image: Image, val savedAt: Long)

@Serializable
data class SourceState(
    val turn: Int = 0,
    val page: Int = 1,
    val candidates: List<Candidate> = emptyList(),
    val saved: List<Saved> = emptyList(),
    val prepared: List<Image> = emptyList(),
)

fun List<Source>.reaches(from: String, to: String): Boolean {
    val byId = associateBy(Source::id)
    val seen = HashSet<String>()
    val pending = ArrayDeque(listOf(from))
    while (pending.isNotEmpty()) {
        val id = pending.removeFirst()
        if (id == to) return true
        if (seen.add(id)) byId[id]?.recipe?.inputs?.let(pending::addAll)
    }
    return false
}

fun List<Source>.users(id: String): List<Source> = filter { id in it.recipe.inputs }

fun List<Source>.dependents(id: String): Set<String> =
    filter { it.id != id && reaches(it.id, id) }.mapTo(HashSet(), Source::id)

fun List<Source>.closure(roots: Collection<String>): List<Source> {
    val byId = associateBy(Source::id)
    val seen = LinkedHashSet<String>()
    fun visit(id: String) {
        val source = byId[id] ?: return
        if (!seen.add(id)) return
        source.recipe.inputs.forEach(::visit)
    }
    roots.forEach(::visit)
    return seen.map(byId::getValue)
}

fun List<Source>.generatedName(kind: Kind): String {
    val taken = mapTo(HashSet(), Source::name)
    return generateSequence(1) { it + 1 }.map { "${kind.slug}-$it" }.first { it !in taken }
}

enum class Problem {
    NAME_TAKEN,
    WALLHAVEN_CATEGORY,
    WALLHAVEN_PURITY,
    WALLHAVEN_ASPECT,
    WALLHAVEN_KEY,
    NEKOS_BEST_CATEGORY,
    PANDORA_CHANNEL,
    FOLDER_MISSING,
    KEEP_READY_SOURCE,
    KEEP_READY_OPTION,
    COMBINE_SOURCES,
    INPUT_MISSING,
    INPUT_CYCLE,
}

fun AppData.problems(draft: Source): List<Problem> = buildList {
    val name = draft.name.trim()
    if (name.isNotEmpty() && sources.any { it.id != draft.id && it.name == name }) add(Problem.NAME_TAKEN)
    when (val recipe = draft.recipe) {
        is Recipe.Wallhaven -> {
            if (!recipe.general && !recipe.anime && !recipe.people) add(Problem.WALLHAVEN_CATEGORY)
            if (!recipe.sfw && !recipe.sketchy && !recipe.nsfw) add(Problem.WALLHAVEN_PURITY)
            if (!recipe.portrait && !recipe.landscape) add(Problem.WALLHAVEN_ASPECT)
            if (recipe.nsfw && wallhavenKey.isBlank()) add(Problem.WALLHAVEN_KEY)
        }
        is Recipe.NekosBest -> if (recipe.categories.isEmpty()) add(Problem.NEKOS_BEST_CATEGORY)
        is Recipe.Pandora -> if (recipe.channels.isEmpty()) add(Problem.PANDORA_CHANNEL)
        is Recipe.Folder -> if (recipe.treeUri.isEmpty()) add(Problem.FOLDER_MISSING)
        is Recipe.Favorites -> Unit
        is Recipe.KeepReady -> {
            if (recipe.sourceId == null) add(Problem.KEEP_READY_SOURCE)
            if (recipe.save == null && recipe.prepare == null) add(Problem.KEEP_READY_OPTION)
        }
        is Recipe.Combine -> if (recipe.sourceIds.distinct().size < 2) add(Problem.COMBINE_SOURCES)
    }
    val inputs = draft.recipe.inputs
    if (inputs.any { source(it) == null }) add(Problem.INPUT_MISSING)
    if (inputs.any { it == draft.id || sources.reaches(it, draft.id) }) add(Problem.INPUT_CYCLE)
}

fun AppData.eligibleInputs(draft: Source): List<Source> =
    sources.filter { it.id != draft.id && !sources.reaches(it.id, draft.id) }
