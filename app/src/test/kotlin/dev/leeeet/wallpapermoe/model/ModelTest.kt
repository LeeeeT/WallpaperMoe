package dev.leeeet.wallpapermoe.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    private fun image(id: String) = Image(id, "blob-$id", "image/png", 100, 200, 1000, Provider.WALLHAVEN)

    private val wallhaven = Source("w", "wall", Recipe.Wallhaven())
    private val ready = Source("k", "ready", Recipe.KeepReady(sourceId = "w"))
    private val mix = Source("c", "mix", Recipe.Combine(listOf("k", "w")))
    private val data = AppData(sources = listOf(wallhaven, ready, mix), home = Feed("c"))

    @Test
    fun `a blank name becomes a generated name`() {
        val saved = data.withSource(Source("n", " ", Recipe.Wallhaven()))
        assertEquals("wallhaven-1", saved.source("n")?.name)
        val second = saved.withSource(Source("m", "", Recipe.Wallhaven()))
        assertEquals("wallhaven-2", second.source("m")?.name)
    }

    @Test
    fun `a recipe change resets the source and its dependents`() {
        val state = SourceState(prepared = listOf(image("a")))
        val filled = data.copy(states = mapOf("w" to state, "k" to state, "c" to state))
        val renamed = filled.withSource(wallhaven.copy(name = "other"))
        assertEquals(setOf("w", "k", "c"), renamed.states.keys)
        val edited = filled.withSource(wallhaven.copy(recipe = Recipe.Wallhaven(query = "sky")))
        assertTrue(edited.states.isEmpty())
        val leaf = filled.withSource(mix.copy(recipe = Recipe.Combine(listOf("w", "k"))))
        assertEquals(setOf("w", "k"), leaf.states.keys)
    }

    @Test
    fun `a deleted source leaves the feed without a source`() {
        val deleted = data.withoutSource("c")
        assertNull(deleted.home.sourceId)
        assertNull(deleted.source("c"))
    }

    @Test
    fun `a draft cannot use a source that uses the draft`() {
        val cyclic = wallhaven.copy(recipe = Recipe.KeepReady(sourceId = "c"))
        assertTrue(Problem.INPUT_CYCLE in data.problems(cyclic))
        assertEquals(emptyList<Source>(), data.eligibleInputs(wallhaven))
        assertEquals(listOf("w", "k"), data.eligibleInputs(mix).map(Source::id))
    }

    @Test
    fun `validation reports each recipe problem`() {
        assertEquals(listOf(Problem.NAME_TAKEN), data.problems(Source("n", "wall", Recipe.Favorites())))
        assertEquals(
            listOf(Problem.WALLHAVEN_CATEGORY, Problem.WALLHAVEN_KEY),
            data.problems(Source("n", "", Recipe.Wallhaven(general = false, anime = false, nsfw = true))),
        )
        assertEquals(
            listOf(Problem.KEEP_READY_SOURCE, Problem.KEEP_READY_OPTION),
            data.problems(Source("n", "", Recipe.KeepReady(prepare = null))),
        )
        assertEquals(listOf(Problem.COMBINE_SOURCES), data.problems(Source("n", "", Recipe.Combine(listOf("w", "w")))))
        assertEquals(emptyList<Problem>(), data.problems(mix))
    }

    @Test
    fun `the history keeps the newest images without duplicates`() {
        var current = data
        for (index in 1..25) current = current.showing(Screen.HOME, image("i$index"), index.toLong())
        current = current.showing(Screen.HOME, image("i20"), 26)
        assertEquals(HISTORY_LIMIT, current.history.size)
        assertEquals(listOf("i20", "i25", "i24"), current.history.take(3).map(Image::id))
        assertEquals("i20", current.shown(Screen.HOME)?.image?.id)
    }

    @Test
    fun `the lock screen follows the home screen until it has its own feed`() {
        val shown = data.showing(Screen.HOME, image("home"), 1)
        assertEquals("home", shown.shown(Screen.LOCK)?.image?.id)
        val separate = shown.withFeed(Screen.LOCK, Feed("w")).showing(Screen.LOCK, image("lock"), 2)
        assertEquals("lock", separate.shown(Screen.LOCK)?.image?.id)
        val joined = separate.withFeed(Screen.LOCK, null)
        assertEquals("home", joined.shown(Screen.LOCK)?.image?.id)
        assertFalse("blob-lock" in joined.copy(history = emptyList()).blobs())
    }

    @Test
    fun `a lock screen that follows the home screen changes the home feed`() {
        val shared = data.withActiveFeed(Screen.LOCK) { it.copy(frequency = Frequency.HOURLY) }
        assertNull(shared.lock)
        assertEquals(Frequency.HOURLY, shared.home.frequency)
        val separate = data.withFeed(Screen.LOCK, Feed("w")).withActiveFeed(Screen.LOCK) { it.copy(sourceId = "k") }
        assertEquals("k", separate.lock?.sourceId)
        assertEquals("c", separate.home.sourceId)
    }

    @Test
    fun `the next image stays referenced until it is shown or its feed changes`() {
        val ready = data.withNext(Screen.HOME, image("next"))
        assertTrue("blob-next" in ready.blobs())
        assertEquals("next", ready.showing(Screen.HOME, image("other"), 1).next[Screen.HOME]?.id)
        val shown = ready.showing(Screen.HOME, image("next"), 1)
        assertTrue(shown.next.isEmpty())
        assertEquals("next", shown.shown(Screen.HOME)?.image?.id)
        assertTrue(ready.withFeed(Screen.HOME, Feed("w")).next.isEmpty())
    }

    @Test
    fun `placing a source on one screen separates the lock screen and keeps its image`() {
        val shown = data.showing(Screen.HOME, image("home"), 1)
        val home = shown.placing("w", Placement.HOME)
        assertEquals("w", home.home.sourceId)
        assertEquals("c", home.lock?.sourceId)
        assertEquals("home", home.shown(Screen.LOCK)?.image?.id)
        val lock = shown.placing("w", Placement.LOCK)
        assertEquals("c", lock.home.sourceId)
        assertEquals("w", lock.lock?.sourceId)
        val both = lock.placing("k", Placement.BOTH)
        assertNull(both.lock)
        assertEquals("k", both.home.sourceId)
    }

    @Test
    fun `the next image follows the source of its screen`() {
        val ready = data.withNext(Screen.HOME, image("next"))
        assertEquals("next", ready.withActiveFeed(Screen.HOME) { it.copy(frequency = Frequency.HOURLY) }.next[Screen.HOME]?.id)
        assertTrue(ready.withActiveFeed(Screen.HOME) { it.copy(sourceId = "w") }.next.isEmpty())
        assertTrue(ready.withoutSource("c").next.isEmpty())
    }

    @Test
    fun `favorites take the given order and keep images that the order does not name`() {
        val data = AppData().withFavorite(image("a"), true).withFavorite(image("b"), true).withFavorite(image("c"), true)
        val ordered = data.withFavoriteOrder(listOf("a", "c", "x"))
        assertEquals(listOf("a", "c", "b"), ordered.favorites.map { it.id })
    }

    @Test
    fun `a feed is due after its interval`() {
        val hourly = data.copy(home = Feed("c", Frequency.HOURLY))
        assertTrue(hourly.due(Screen.HOME, 0))
        val shown = hourly.showing(Screen.HOME, image("a"), 1_000_000)
        assertFalse(shown.due(Screen.HOME, 1_000_000 + 59 * 60_000))
        assertTrue(shown.due(Screen.HOME, 1_000_000 + 60 * 60_000))
        assertTrue(shown.due(Screen.HOME, 500_000))
        assertFalse(shown.due(Screen.LOCK, Long.MAX_VALUE))
        assertFalse(data.copy(home = Feed()).due(Screen.HOME, 0))
    }

    @Test
    fun `the referenced blobs cover every place that keeps an image`() {
        val state = SourceState(saved = listOf(Saved(image("s"), 1)), prepared = listOf(image("p")))
        val full = data.showing(Screen.HOME, image("h"), 1)
            .withFavorite(image("f"), true)
            .copy(states = mapOf("k" to state))
        assertEquals(setOf("blob-h", "blob-f", "blob-s", "blob-p"), full.blobs())
        assertEquals(setOf("blob-h", "blob-s", "blob-p"), full.withFavorite(image("f"), false).blobs())
    }

    @Test
    fun `sources survive serialization`() {
        val json = Json { encodeDefaults = true }
        assertEquals(data, json.decodeFromString<AppData>(json.encodeToString(data)))
    }

    @Test
    fun `the initial data feeds both screens from a ready nekos best source`() {
        val initial = initialData("Keep ready", "Nekos.best")
        val home = initial.source(initial.home.sourceId)!!.recipe as Recipe.KeepReady
        assertEquals(Prepare(3), home.prepare)
        assertNull(home.save)
        assertEquals(Recipe.NekosBest(listOf("neko", "waifu"), Pick.RANDOM), initial.source(home.sourceId)!!.recipe)
        assertNull(initial.lock)
        assertEquals(initial, initial.normalized())
    }
}
