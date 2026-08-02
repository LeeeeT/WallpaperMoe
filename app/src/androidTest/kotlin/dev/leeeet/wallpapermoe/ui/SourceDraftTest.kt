package dev.leeeet.wallpapermoe.ui

import dev.leeeet.wallpapermoe.source.CombineMode
import dev.leeeet.wallpapermoe.source.CacheRefresh
import dev.leeeet.wallpapermoe.source.PandoraChannel
import dev.leeeet.wallpapermoe.source.SelectionMode
import dev.leeeet.wallpapermoe.source.SourceDefinition
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.SourceLibrary
import dev.leeeet.wallpapermoe.source.SourceRecipe
import dev.leeeet.wallpapermoe.source.WallhavenAspect
import dev.leeeet.wallpapermoe.source.newSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDraftTest {

    private fun channel(id: String) = PandoraChannel(id, id)

    @Test
    fun wallhavenDefaultsToEveryAspectRatio() {
        val id = newSourceId()
        val draft = newSourceDraft(SourceKind.WALLHAVEN, SourceLibrary())

        assertNull(draft.validationProblem(id, SourceLibrary()))
        val saved = draft.toDefinition(id).recipe as SourceRecipe.Wallhaven
        assertEquals(WallhavenAspect.ALL, saved.aspects)
    }

    @Test
    fun wallhavenRequiresAnAspectRatio() {
        val draft = SourceDraft(
            "wallhaven-1",
            SourceRecipeDraft.Wallhaven(aspects = emptySet()),
        )

        assertEquals(
            DraftProblem.WallhavenAspectMissing,
            draft.validationProblem(newSourceId(), SourceLibrary()),
        )
    }

    @Test
    fun incompleteGroupedRecipesStayDrafts() {
        val library = SourceLibrary()
        listOf(SourceKind.KEEP_READY, SourceKind.COMBINE, SourceKind.NEKOS_BEST).forEach {
            assertNotNull(newSourceDraft(it, library).validationProblem(newSourceId(), library))
        }
    }

    @Test
    fun nekosBestDraftSavesSeveralCategoriesAndItsMode() {
        val id = newSourceId()
        val draft = newSourceDraft(SourceKind.NEKOS_BEST, SourceLibrary()).copy(
            recipe = SourceRecipeDraft.NekosBest(
                categories = listOf("neko", "waifu"),
                mode = SelectionMode.ROUND_ROBIN,
            ),
        )

        assertNull(draft.validationProblem(id, SourceLibrary()))
        assertEquals(
            SourceRecipe.NekosBest(listOf("neko", "waifu"), SelectionMode.ROUND_ROBIN),
            draft.toDefinition(id).recipe,
        )
    }

    @Test
    fun nekosBestCanBePlacedBehindPrefetchIndirectly() {
        val nekosId = newSourceId()
        val combineId = newSourceId()
        val readyId = newSourceId()
        val nekos = SourceDefinition(
            nekosId,
            "nekos-best-1",
            SourceRecipe.NekosBest(listOf("neko"), SelectionMode.RANDOM),
        )
        val combine = SourceDefinition(
            combineId,
            "combine-1",
            SourceRecipe.Combine(CombineMode.RANDOM, listOf(nekosId)),
        )
        val library = SourceLibrary(listOf(nekos, combine))
        val draft = SourceDraft(
            "keep-ready-1",
            SourceRecipeDraft.KeepReady(
                sourceId = combineId,
                prefetchEnabled = true,
                prefetchDepth = 3,
            ),
        )

        assertNull(draft.validationProblem(readyId, library))
    }

    @Test
    fun combineDefaultsToRandom() {
        val recipe = newSourceDraft(SourceKind.COMBINE, SourceLibrary()).recipe
        assertEquals(CombineMode.RANDOM, (recipe as SourceRecipeDraft.Combine).mode)
    }

    @Test
    fun combineKeepsIntentionalDuplicateReferencesInOrder() {
        val first = SourceDefinition(
            newSourceId(),
            "pandora-1",
            SourceRecipe.Pandora(listOf(channel("first"))),
        )
        val second = SourceDefinition(
            newSourceId(),
            "pandora-2",
            SourceRecipe.Pandora(listOf(channel("second"))),
        )
        val draft = SourceDraft(
            "combine-1",
            SourceRecipeDraft.Combine(
                CombineMode.ROUND_ROBIN,
                listOf(first.id, second.id, first.id),
            ),
        )
        val id = newSourceId()

        assertNull(draft.validationProblem(id, SourceLibrary(listOf(first, second))))
        val saved = draft.toDefinition(id).recipe as SourceRecipe.Combine
        assertEquals(listOf(first.id, second.id, first.id), saved.sourceIds)
    }

    @Test
    fun favoritesDefaultsToRandomAndCanSaveImmediately() {
        val id = newSourceId()
        val draft = newSourceDraft(SourceKind.FAVORITES, SourceLibrary())
        assertNull(draft.validationProblem(id, SourceLibrary()))
        assertEquals(SelectionMode.RANDOM, (draft.recipe as SourceRecipeDraft.Favorites).mode)
        assertEquals(
            SourceRecipe.Favorites(SelectionMode.RANDOM),
            draft.toDefinition(id).recipe,
        )
    }

    @Test
    fun keepReadyMaySaveWithBothOptionsOff() {
        val childId = newSourceId()
        val child = SourceDefinition(
            childId,
            "pandora-1",
            SourceRecipe.Pandora(listOf(channel("c"))),
        )
        val ownerId = newSourceId()
        val draft = newSourceDraft(SourceKind.KEEP_READY, SourceLibrary(listOf(child))).copy(
            recipe = SourceRecipeDraft.KeepReady(sourceId = childId),
        )

        assertNull(draft.validationProblem(ownerId, SourceLibrary(listOf(child))))
        val saved = draft.toDefinition(ownerId).recipe as SourceRecipe.KeepReady
        assertNull(saved.cache)
        assertNull(saved.prefetch)
    }

    @Test
    fun enabledKeepReadyOptionsCompileFromDraftInput() {
        val childId = newSourceId()
        val recipe = SourceDraft(
            name = "keep-ready-1",
            recipe = SourceRecipeDraft.KeepReady(
                sourceId = childId,
                cacheEnabled = true,
                cacheCapacity = 40,
                cacheRefresh = CacheRefresh.SIX_HOURS,
                prefetchEnabled = true,
                prefetchDepth = 5,
            ),
        ).toDefinition(newSourceId()).recipe as SourceRecipe.KeepReady

        assertEquals(40, recipe.cache?.capacity)
        assertEquals(CacheRefresh.SIX_HOURS, recipe.cache?.refresh)
        assertEquals(5, recipe.prefetch?.depth)
    }

    @Test
    fun neverRefreshingCacheRoundTrips() {
        val childId = newSourceId()
        val ownerId = newSourceId()
        val draft = SourceDraft(
            name = "keep-ready-1",
            recipe = SourceRecipeDraft.KeepReady(
                sourceId = childId,
                cacheEnabled = true,
                cacheRefresh = CacheRefresh.NEVER,
            ),
        )

        val definition = draft.toDefinition(ownerId)
        val recipe = definition.recipe as SourceRecipe.KeepReady
        assertEquals(CacheRefresh.NEVER, recipe.cache?.refresh)
        val reopened = definition.toDraft().recipe as SourceRecipeDraft.KeepReady
        assertEquals(CacheRefresh.NEVER, reopened.cacheRefresh)
    }

    @Test
    fun namesAreTrimmedUniqueAndCaseSensitive() {
        val saved = SourceDefinition(
            newSourceId(),
            "pandora-1",
            SourceRecipe.Pandora(listOf(channel("c"))),
        )
        val library = SourceLibrary(listOf(saved))
        val duplicateId = newSourceId()
        val duplicate = newSourceDraft(SourceKind.PANDORA, library).copy(name = " pandora-1 ")
        val differentlyCased = duplicate.copy(name = "Pandora-1")

        assertEquals(DraftProblem.DuplicateName, duplicate.validationProblem(duplicateId, library))
        assertNull(
            differentlyCased.copy(
                recipe = SourceRecipeDraft.Pandora(listOf(channel("c"))),
            ).validationProblem(duplicateId, library),
        )
    }

    @Test
    fun generatedNamesUseTheLowestAvailableSuffix() {
        val library = SourceLibrary(
            listOf(
                SourceDefinition(
                    newSourceId(),
                    "combine-1",
                    SourceRecipe.Pandora(listOf(channel("c"))),
                ),
                SourceDefinition(
                    newSourceId(),
                    "combine-3",
                    SourceRecipe.Pandora(listOf(channel("c"))),
                ),
            ),
        )
        assertEquals("combine-2", newSourceDraft(SourceKind.COMBINE, library).name)
    }

    @Test
    fun draftIdentityLivesOutsideUserInput() {
        val fields = SourceDraft::class.java.declaredFields.map { it.name }
        assertFalse("id" in fields)
        assertTrue("name" in fields)
        assertTrue("recipe" in fields)
    }

    @Test
    fun cyclesAreRejectedButRepeatedReferencesAreAllowed() {
        val firstId = newSourceId()
        val secondId = newSourceId()
        val first = SourceDefinition(
            firstId,
            "combine-1",
            SourceRecipe.Combine(CombineMode.RANDOM, listOf(secondId, secondId)),
        )
        val second = SourceDefinition(
            secondId,
            "pandora-1",
            SourceRecipe.Pandora(listOf(channel("c"))),
        )
        val library = SourceLibrary(listOf(first, second))

        val repeated = first.toDraft()
        assertNull(repeated.validationProblem(firstId, library))

        val cycle = second.toDraft().copy(recipe = SourceRecipeDraft.KeepReady(firstId))
        assertEquals(DraftProblem.ReferenceCycle, cycle.validationProblem(secondId, library))
    }
}
