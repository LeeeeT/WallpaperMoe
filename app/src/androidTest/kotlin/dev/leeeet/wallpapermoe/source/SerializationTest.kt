package dev.leeeet.wallpapermoe.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SerializationTest {

    private fun roundTrip(library: SourceLibrary): SourceLibrary = documentJson.decodeFromString(
        SourceLibrary.serializer(),
        documentJson.encodeToString(SourceLibrary.serializer(), library),
    )

    @Test
    fun groupedAuthoringRecipesSurviveTheTrip() {
        val favorites = SourceDefinition(
            newSourceId(),
            "favorites-1",
            SourceRecipe.Favorites(FavoritesMode.ROUND_ROBIN),
        )
        val pandora = SourceDefinition(
            newSourceId(),
            "pandora-1",
            SourceRecipe.Pandora(listOf("travel"), listOf("Travel")),
        )
        val ready = SourceDefinition(
            newSourceId(),
            "keep-ready-1",
            SourceRecipe.KeepReady(
                pandora.id,
                cache = CacheOptions(capacity = 20, refreshHours = 24),
                prefetch = PrefetchOptions(depth = 3),
            ),
        )
        val combine = SourceDefinition(
            newSourceId(),
            "combine-1",
            SourceRecipe.Combine(CombineMode.RANDOM, listOf(ready.id, pandora.id, pandora.id)),
        )
        val library = SourceLibrary(listOf(favorites, pandora, ready, combine), combine.id)

        assertEquals(library, roundTrip(library))
    }

    @Test
    fun incompleteSavedRecipesCannotBeDecoded() {
        assertThrows(Exception::class.java) {
            documentJson.decodeFromString(
                SourceRecipe.serializer(),
                """{"type":"keep_ready","sourceId":"","cache":{"capacity":0,"refreshHours":24}}""",
            )
        }
    }
}
