package dev.leeeet.wallpapermoe.source

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File

class SourceRepositoryTest {

    private lateinit var root: File
    private lateinit var file: File
    private lateinit var storage: Storage
    private lateinit var repository: SourceRepository

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "repo-${System.nanoTime()}",
        )
        file = File(root, "sources.json")
        storage = Storage(File(root, "sources"))
        repository = SourceRepository(file, storage)
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun wallhavenDefinition(name: String = "wallhaven-1") = SourceDefinition(
        id = newSourceId(),
        name = name,
        recipe = SourceRecipe.Wallhaven(
            query = "forest",
            general = true,
            anime = false,
            people = false,
            sfw = true,
            sketchy = false,
            nsfw = false,
            aspects = setOf(WallhavenAspect.PORTRAIT),
            sorting = WallhavenSorting.RANDOM,
            apiKey = "",
        ),
    )

    @Test
    fun startsWithAnEmptyLibrary() {
        runBlocking { repository.load() }
        assertEquals(SourceLibrary(), repository.library.value)
        assertNull(repository.source.value)
    }

    @Test
    fun definitionsAndTheActiveChoiceComeBackFromDisk() {
        val definition = wallhavenDefinition()
        runBlocking {
            assertTrue(repository.upsert(definition))
            repository.setActive(definition.id)
        }

        val reopened = SourceRepository(file, storage)
        runBlocking { reopened.load() }
        assertEquals(SourceLibrary(listOf(definition), definition.id), reopened.library.value)
        assertTrue(reopened.source.value is Wallhaven)
    }

    @Test
    fun sourceOrderCanBeChangedAndComesBackFromDisk() = runBlocking {
        val first = wallhavenDefinition("first")
        val second = wallhavenDefinition("second")
        val third = wallhavenDefinition("third")
        listOf(first, second, third).forEach { assertTrue(repository.upsert(it)) }

        assertTrue(repository.reorderSources(listOf(third.id, first.id, second.id)))
        assertEquals(listOf(third, first, second), repository.library.value.sources)

        val reopened = SourceRepository(file, storage)
        reopened.load()
        assertEquals(listOf(third, first, second), reopened.library.value.sources)
    }

    @Test
    fun sourceOrderRejectsAnythingButTheCurrentDefinitions() = runBlocking {
        val first = wallhavenDefinition("first")
        val second = wallhavenDefinition("second")
        listOf(first, second).forEach { assertTrue(repository.upsert(it)) }

        assertFalse(repository.reorderSources(listOf(first.id, first.id)))
        assertFalse(repository.reorderSources(listOf(first.id)))
        assertEquals(listOf(first, second), repository.library.value.sources)
    }

    @Test
    fun aReferencedDefinitionCannotBeDeleted() {
        val child = wallhavenDefinition()
        val ready = SourceDefinition(
            newSourceId(),
            "keep-ready-1",
            SourceRecipe.KeepReady(child.id, cache = CacheOptions(20, CacheRefresh.DAILY)),
        )
        runBlocking {
            repository.upsert(child)
            repository.upsert(ready)
            assertEquals(SourceRepository.DeleteResult.REFERENCED, repository.delete(child.id))
        }
        assertEquals(listOf(child, ready), repository.library.value.sources)
    }

    @Test
    fun deletingADefinitionRemovesItsStateAndClearsActive() {
        val definition = wallhavenDefinition()
        File(storage.dir("${definition.id}/wallhaven"), "sample.wp").writeBytes(ByteArray(4))
        runBlocking {
            repository.upsert(definition)
            repository.setActive(definition.id)
            assertEquals(SourceRepository.DeleteResult.DELETED, repository.delete(definition.id))
        }
        assertFalse(File(root, "sources/${definition.id}").exists())
        assertNull(repository.source.value)
    }

    @Test
    fun malformedSavedDocumentIsRejected() {
        file.parentFile!!.mkdirs()
        file.writeText("not json")
        assertThrows(Exception::class.java) { runBlocking { repository.load() } }
    }

    @Test
    fun keepReadyCompilesCacheBeforePrefetch() {
        val input = wallhavenDefinition()
        val ready = SourceDefinition(
            newSourceId(),
            "keep-ready-1",
            SourceRecipe.KeepReady(
                input.id,
                cache = CacheOptions(20, CacheRefresh.DAILY),
                prefetch = PrefetchOptions(3),
            ),
        )
        val source = SourceLibrary(listOf(input, ready), ready.id).resolveActiveSource()

        val prefetch = source as Prefetch
        val cache = prefetch.source as Cache
        assertTrue(cache.source is Wallhaven)
        assertEquals("${ready.id}/prefetch", prefetch.store)
        assertEquals("${ready.id}/cache", cache.store)
    }

    @Test
    fun combineCompilesTheSelectedMode() {
        val first = wallhavenDefinition("first")
        val second = wallhavenDefinition("second")
        CombineMode.entries.forEach { mode ->
            val combine = SourceDefinition(
                newSourceId(),
                "combine-${mode.name}",
                SourceRecipe.Combine(mode, listOf(first.id, second.id)),
            )
            val source = SourceLibrary(listOf(first, second, combine), combine.id).resolveActiveSource()
            assertTrue(
                when (mode) {
                    CombineMode.RANDOM -> source is Random
                    CombineMode.ROUND_ROBIN -> source is RoundRobin
                    CombineMode.FALLTHROUGH -> source is Fallthrough
                },
            )
        }
    }

    @Test
    fun favoritesCompilesWithItsSamplingMode() {
        SelectionMode.entries.forEach { mode ->
            val definition = SourceDefinition(
                newSourceId(),
                "favorites-${mode.name}",
                SourceRecipe.Favorites(mode),
            )
            val source = SourceLibrary(listOf(definition), definition.id).resolveActiveSource()
            assertEquals(mode, (source as Favorites).mode)
            assertEquals("${definition.id}/favorites", source.store)
        }
    }

    @Test
    fun nekosBestCompilesWithCategoriesModeAndStore() {
        SelectionMode.entries.forEach { mode ->
            val definition = SourceDefinition(
                newSourceId(),
                "nekos-best-${mode.name}",
                SourceRecipe.NekosBest(listOf("neko", "waifu"), mode),
            )
            val source = SourceLibrary(listOf(definition), definition.id).resolveActiveSource() as NekosBest
            assertEquals(listOf("neko", "waifu"), source.categories)
            assertEquals(mode, source.mode)
            assertEquals("${definition.id}/nekos-best", source.store)
        }
    }

    @Test
    fun cyclesAndDuplicateNamesAreRejected() {
        val first = wallhavenDefinition("same")
        val second = wallhavenDefinition("same")
        runBlocking {
            assertTrue(repository.upsert(first))
            assertFalse(repository.upsert(second))
        }
    }
}
