package dev.leeeet.wallpapermoe.ui

import dev.leeeet.wallpapermoe.source.CombineMode
import dev.leeeet.wallpapermoe.source.FavoritesMode
import dev.leeeet.wallpapermoe.source.SourceDefinition
import dev.leeeet.wallpapermoe.source.SourceKind
import dev.leeeet.wallpapermoe.source.SourceLibrary
import dev.leeeet.wallpapermoe.source.SourceRecipe
import dev.leeeet.wallpapermoe.source.newSourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDraftTest {

    @Test
    fun incompleteGroupedRecipesStayDrafts() {
        val library = SourceLibrary()
        listOf(SourceKind.KEEP_READY, SourceKind.COMBINE).forEach {
            assertNotNull(newSourceDraft(it, library).validationProblem(newSourceId(), library))
        }
    }

    @Test
    fun combineDefaultsToRandom() {
        val recipe = newSourceDraft(SourceKind.COMBINE, SourceLibrary()).recipe
        assertEquals(CombineMode.RANDOM, (recipe as SourceRecipeDraft.Combine).mode)
    }

    @Test
    fun favoritesDefaultsToRandomAndCanSaveImmediately() {
        val id = newSourceId()
        val draft = newSourceDraft(SourceKind.FAVORITES, SourceLibrary())
        assertNull(draft.validationProblem(id, SourceLibrary()))
        assertEquals(FavoritesMode.RANDOM, (draft.recipe as SourceRecipeDraft.Favorites).mode)
        assertEquals(
            SourceRecipe.Favorites(FavoritesMode.RANDOM),
            draft.toDefinition(id).recipe,
        )
    }

    @Test
    fun keepReadyMaySaveWithBothOptionsOff() {
        val childId = newSourceId()
        val child = SourceDefinition(
            childId,
            "pandora-1",
            SourceRecipe.Pandora(listOf("c"), emptyList()),
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
        assertEquals(6, recipe.cache?.refreshHours)
        assertEquals(5, recipe.prefetch?.depth)
    }

    @Test
    fun namesAreTrimmedUniqueAndCaseSensitive() {
        val saved = SourceDefinition(
            newSourceId(),
            "pandora-1",
            SourceRecipe.Pandora(listOf("c"), emptyList()),
        )
        val library = SourceLibrary(listOf(saved))
        val duplicateId = newSourceId()
        val duplicate = newSourceDraft(SourceKind.PANDORA, library).copy(name = " pandora-1 ")
        val differentlyCased = duplicate.copy(name = "Pandora-1")

        assertEquals(DraftProblem.DuplicateName, duplicate.validationProblem(duplicateId, library))
        assertNull(
            differentlyCased.copy(
                recipe = SourceRecipeDraft.Pandora(listOf("c")),
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
                    SourceRecipe.Pandora(listOf("c"), emptyList()),
                ),
                SourceDefinition(
                    newSourceId(),
                    "combine-3",
                    SourceRecipe.Pandora(listOf("c"), emptyList()),
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
            SourceRecipe.Pandora(listOf("c"), emptyList()),
        )
        val library = SourceLibrary(listOf(first, second))

        val repeated = first.toDraft()
        assertNull(repeated.validationProblem(firstId, library))

        val cycle = second.toDraft().copy(recipe = SourceRecipeDraft.KeepReady(firstId))
        assertEquals(DraftProblem.ReferenceCycle, cycle.validationProblem(secondId, library))
    }
}
