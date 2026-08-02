package dev.leeeet.wallpapermoe.source

import dev.leeeet.wallpapermoe.wallpaper.SavedWallpaper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class FavoritesTest {

    private lateinit var env: Env

    @Before fun setUp() { env = Env() }
    @After fun tearDown() { env.close() }

    private suspend fun favorite(number: Int) {
        val file = File(env.storage.dir("favorite-input"), "$number.wp").apply { writeText("$number") }
        env.archive.addFavorite(
            SavedWallpaper(
                "$number",
                WallpaperMetadata(imageUrl = "https://example.com/$number.jpg"),
                file,
                number.toLong(),
            ),
        )
    }

    @Test
    fun emptyFavoritesHasAUsefulFailure() {
        assertEquals(
            SampleFailure.FavoritesEmpty,
            Favorites(SelectionMode.RANDOM, "favorites").sampleFailure(env.ctx).failure,
        )
    }

    @Test
    fun roundRobinUsesEveryFavoriteInOrderAndRemembersItsPlace() = runBlocking {
        favorite(1)
        favorite(2)
        val first = Favorites(SelectionMode.ROUND_ROBIN, "favorites")
        assertEquals("2", first.sample(env.ctx).id)

        val restarted = Favorites(SelectionMode.ROUND_ROBIN, "favorites")
        assertEquals("1", restarted.sample(env.ctx).id)
        assertEquals("2", restarted.sample(env.ctx).id)
    }

    @Test
    fun randomReturnsAnOwnedFavoriteWithoutChangingIt() = runBlocking {
        favorite(1)
        val sample = Favorites(SelectionMode.RANDOM, "favorites").sample(env.ctx)
        assertEquals("1", sample.id)
        assertEquals("https://example.com/1.jpg", sample.metadata.imageUrl)
        assertEquals("1", sample.prepared.readText())
        assertEquals(1, env.archive.favorites.value.size)
    }
}
