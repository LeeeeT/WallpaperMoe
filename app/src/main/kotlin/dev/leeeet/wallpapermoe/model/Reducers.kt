package dev.leeeet.wallpapermoe.model

fun AppData.blobs(): Set<String> = buildSet {
    shown.values.forEach { add(it.image.blob) }
    next.values.forEach { add(it.blob) }
    history.forEach { add(it.blob) }
    favorites.forEach { add(it.blob) }
    states.values.forEach { state ->
        state.saved.forEach { add(it.image.blob) }
        state.prepared.forEach { add(it.blob) }
    }
}

fun AppData.normalized(): AppData {
    val ids = sources.mapTo(HashSet(), Source::id)
    return copy(
        home = home.takeIf { it.sourceId == null || it.sourceId in ids } ?: home.copy(sourceId = null),
        lock = lock?.let { feed -> feed.takeIf { it.sourceId == null || it.sourceId in ids } ?: feed.copy(sourceId = null) },
        shown = if (lock == null) shown - Screen.LOCK else shown,
        next = next.filterKeys { screen -> (if (screen == Screen.HOME) home else lock)?.sourceId?.let { it in ids } == true },
        history = history.take(HISTORY_LIMIT),
        states = states.filterKeys(ids::contains),
    )
}

fun AppData.withSource(draft: Source): AppData {
    val source = draft.copy(name = draft.name.trim().ifEmpty { sources.generatedName(draft.recipe.kind) })
    val existing = source(source.id)
    val next = if (existing == null) sources + source else sources.map { if (it.id == source.id) source else it }
    val stale = if (existing == null || existing.recipe == source.recipe) {
        emptySet()
    } else {
        next.dependents(source.id) + source.id
    }
    return copy(sources = next, states = states - stale, next = if (stale.isEmpty()) this.next else emptyMap()).normalized()
}

fun AppData.withoutSource(id: String): AppData =
    copy(sources = sources.filterNot { it.id == id }).normalized()

fun AppData.withSourceOrder(ids: List<String>): AppData {
    val rank = ids.withIndex().associate { it.value to it.index }
    return copy(sources = sources.sortedBy { rank[it.id] ?: Int.MAX_VALUE })
}

fun AppData.withFeed(screen: Screen, feed: Feed?): AppData = when (screen) {
    Screen.HOME -> copy(home = feed ?: Feed())
    Screen.LOCK -> copy(lock = feed)
}.copy(next = if (this.feed(screen)?.sourceId == feed?.sourceId) next else next - screen).normalized()

enum class Placement { HOME, LOCK, BOTH }

fun AppData.placing(sourceId: String, placement: Placement): AppData {
    val own = lock ?: home
    val split = if (lock == null) copy(shown = shown + listOfNotNull(shown[Screen.HOME]?.let { Screen.LOCK to it })) else this
    return when (placement) {
        Placement.HOME -> split.withFeed(Screen.LOCK, own).withFeed(Screen.HOME, home.copy(sourceId = sourceId))
        Placement.LOCK -> split.withFeed(Screen.LOCK, own.copy(sourceId = sourceId))
        Placement.BOTH -> withFeed(Screen.LOCK, null).withFeed(Screen.HOME, home.copy(sourceId = sourceId))
    }
}

fun AppData.withActiveFeed(screen: Screen, change: (Feed) -> Feed): AppData =
    withFeed(feedOwner(screen), change(activeFeed(screen)))

fun AppData.withAppearance(screen: Screen, appearance: Appearance): AppData = when (screen) {
    Screen.HOME -> copy(homeAppearance = appearance)
    Screen.LOCK -> copy(lockAppearance = appearance)
}

fun AppData.withNext(screen: Screen, image: Image): AppData = copy(next = next + (screen to image)).normalized()

fun AppData.showing(screen: Screen, image: Image, now: Long): AppData = copy(
    shown = shown + (screen to Shown(image, now)),
    next = if (next[screen]?.id == image.id) next - screen else next,
    history = (listOf(image) + history.filterNot { it.id == image.id }).take(HISTORY_LIMIT),
).normalized()

fun AppData.withFavorite(image: Image, favorite: Boolean): AppData = copy(
    favorites = favorites.filterNot { it.id == image.id }.let { if (favorite) listOf(image) + it else it },
)

fun AppData.withFavoriteOrder(ids: List<String>): AppData {
    val rank = ids.withIndex().associate { it.value to it.index }
    return copy(favorites = favorites.sortedBy { rank[it.id] ?: Int.MAX_VALUE })
}

fun AppData.withState(sourceId: String, transform: (SourceState) -> SourceState): AppData =
    if (source(sourceId) == null) this else copy(states = states + (sourceId to transform(state(sourceId))))

fun AppData.due(screen: Screen, now: Long): Boolean {
    val feed = feed(screen) ?: return false
    if (feed.sourceId == null) return false
    val last = shown[screen]?.changedAt ?: return true
    return now < last || now - last >= feed.frequency.minutes * 60_000
}
