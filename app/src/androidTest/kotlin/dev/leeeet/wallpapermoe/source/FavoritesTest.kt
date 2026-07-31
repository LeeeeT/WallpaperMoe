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
            SavedWallpaper("$number", "https://example.com/$number.jpg", file, number.toLong()),
        )
    }

    @Test
    fun emptyFavoritesHasAUsefulFailure() {
        assertEquals(
            SampleFailure.FavoritesEmpty,
            Favorites(FavoritesMode.RANDOM, "favorites").sampleFailure(env.ctx).failure,
        )
    }

    @Test
    fun roundRobinUsesEveryFavoriteInOrderAndRemembersItsPlace() = runBlocking {
        favorite(1)
        favorite(2)
        val first = Favorites(FavoritesMode.ROUND_ROBIN, "favorites")
        assertEquals("https://example.com/2.jpg", first.sample(env.ctx).originalUrl)

        val restarted = Favorites(FavoritesMode.ROUND_ROBIN, "favorites")
        assertEquals("https://example.com/1.jpg", restarted.sample(env.ctx).originalUrl)
        assertEquals("https://example.com/2.jpg", restarted.sample(env.ctx).originalUrl)
    }

    @Test
    fun randomReturnsAnOwnedFavoriteWithoutChangingIt() = runBlocking {
        favorite(1)
        val sample = Favorites(FavoritesMode.RANDOM, "favorites").sample(env.ctx)
        assertEquals("https://example.com/1.jpg", sample.originalUrl)
        assertEquals("1", sample.prepared.readText())
        assertEquals(1, env.archive.favorites.value.size)
    }
}
