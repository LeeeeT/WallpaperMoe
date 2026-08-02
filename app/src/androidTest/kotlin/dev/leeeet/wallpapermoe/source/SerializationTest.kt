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
            SourceRecipe.Favorites(SelectionMode.ROUND_ROBIN),
        )
        val pandora = SourceDefinition(
            newSourceId(),
            "pandora-1",
            SourceRecipe.Pandora(listOf(PandoraChannel("travel", "Travel"))),
        )
        val ready = SourceDefinition(
            newSourceId(),
            "keep-ready-1",
            SourceRecipe.KeepReady(
                pandora.id,
                cache = CacheOptions(capacity = 20, refresh = CacheRefresh.DAILY),
                prefetch = PrefetchOptions(depth = 3),
            ),
        )
        val nekosBest = SourceDefinition(
            newSourceId(),
            "nekos-best-1",
            SourceRecipe.NekosBest(listOf("neko", "waifu"), SelectionMode.ROUND_ROBIN),
        )
        val combine = SourceDefinition(
            newSourceId(),
            "combine-1",
            SourceRecipe.Combine(CombineMode.RANDOM, listOf(ready.id, pandora.id, pandora.id)),
        )
        val library = SourceLibrary(listOf(favorites, pandora, nekosBest, ready, combine), combine.id)

        assertEquals(library, roundTrip(library))
    }

    @Test
    fun incompleteSavedRecipesCannotBeDecoded() {
        assertThrows(Exception::class.java) {
            documentJson.decodeFromString(
                SourceRecipe.serializer(),
                """{"type":"keep_ready","sourceId":"","cache":{"capacity":0,"refresh":"DAILY"}}""",
            )
        }
    }

    @Test
    fun savedDocumentsRejectUnknownFields() {
        assertThrows(Exception::class.java) {
            documentJson.decodeFromString(
                SourceLibrary.serializer(),
                """{"sources":[],"obsolete":true}""",
            )
        }
    }

    @Test
    fun authoringOptionsOnlyRepresentEditorChoices() {
        assertThrows(IllegalArgumentException::class.java) {
            CacheOptions(capacity = 21, refresh = CacheRefresh.DAILY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceRecipe.Pandora(
                listOf(
                    PandoraChannel("travel", "Travel"),
                    PandoraChannel("travel", "Travel again"),
                ),
            )
        }
    }
}
