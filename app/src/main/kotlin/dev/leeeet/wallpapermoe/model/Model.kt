package dev.leeeet.wallpapermoe.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class Screen { HOME, LOCK }

@Serializable
enum class Provider { WALLHAVEN, NEKOS_BEST, PANDORA, FOLDER }

@Serializable
data class Image(
    val id: String,
    val blob: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val provider: Provider,
    val title: String? = null,
    val pageUrl: String? = null,
    val artist: String? = null,
    val artistUrl: String? = null,
)

@Serializable
enum class Frequency(val minutes: Long) {
    EVERY_SCREEN_OFF(0),
    HOURLY(60),
    SIX_HOURS(360),
    DAILY(1440),
}

@Serializable
data class Appearance(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val blur: Float = 0f,
) {
    companion object {
        val BRIGHTNESS = 0f..2f
        val CONTRAST = 0f..2f
        val BLUR = 0f..1f
    }
}

@Serializable
data class Feed(
    val sourceId: String? = null,
    val frequency: Frequency = Frequency.EVERY_SCREEN_OFF,
)

@Serializable
data class Shown(val image: Image, val changedAt: Long)

@Serializable
data class AppData(
    val sources: List<Source> = emptyList(),
    val home: Feed = Feed(),
    val lock: Feed? = null,
    val homeAppearance: Appearance = Appearance(),
    val lockAppearance: Appearance = Appearance(),
    val unmeteredOnly: Boolean = false,
    val wallhavenKey: String = "",
    val installId: String = "",
    val shown: Map<Screen, Shown> = emptyMap(),
    val next: Map<Screen, Image> = emptyMap(),
    val history: List<Image> = emptyList(),
    val favorites: List<Image> = emptyList(),
    val states: Map<String, SourceState> = emptyMap(),
) {
    fun source(id: String?): Source? = sources.firstOrNull { it.id == id }

    fun feed(screen: Screen): Feed? = when (screen) {
        Screen.HOME -> home
        Screen.LOCK -> lock
    }

    fun feedOwner(screen: Screen): Screen = if (screen == Screen.LOCK && lock == null) Screen.HOME else screen

    fun activeFeed(screen: Screen): Feed = feed(feedOwner(screen)) ?: home

    fun shown(screen: Screen): Shown? = when (screen) {
        Screen.HOME -> shown[Screen.HOME]
        Screen.LOCK -> if (lock == null) shown[Screen.HOME] else shown[Screen.LOCK] ?: shown[Screen.HOME]
    }

    fun appearance(screen: Screen): Appearance = when (screen) {
        Screen.HOME -> homeAppearance
        Screen.LOCK -> lockAppearance
    }

    fun isFavorite(image: Image): Boolean = favorites.any { it.id == image.id }

    fun state(sourceId: String): SourceState = states[sourceId] ?: SourceState()

    val feeds: List<Screen> get() = if (lock == null) listOf(Screen.HOME) else listOf(Screen.HOME, Screen.LOCK)
}

const val HISTORY_LIMIT = 20

fun initialData(keepReadyName: String, nekosBestName: String): AppData {
    val nekosBest = Source(UUID.randomUUID().toString(), nekosBestName, Recipe.NekosBest(listOf("neko", "waifu"), Pick.RANDOM))
    val keepReady = Source(UUID.randomUUID().toString(), keepReadyName, Recipe.KeepReady(nekosBest.id, prepare = Prepare(3)))
    return AppData(sources = listOf(keepReady, nekosBest), home = Feed(keepReady.id))
}
