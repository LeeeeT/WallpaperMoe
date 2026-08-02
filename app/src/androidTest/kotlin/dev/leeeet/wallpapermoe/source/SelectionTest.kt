package dev.leeeet.wallpapermoe.source

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The sources that choose between other sources, and the ones that recover when a choice fails. */
class SelectionTest {

    private lateinit var env: Env

    @Before fun setUp() { env = Env() }

    @After fun tearDown() { env.close() }

    // ------------------------------------------------------------ round robin

    @Test
    fun roundRobinTakesFromEachInTurn() {
        val source = roundRobin(listOf(wallhaven("one"), wallhaven("two"), wallhaven("three")))
        assertEquals("one", source.take(env.ctx).store)
        assertEquals("two", source.take(env.ctx).store)
        assertEquals("three", source.take(env.ctx).store)
        assertEquals("one", source.take(env.ctx).store)
    }

    /** The position lives in the store folder, so a fresh instance carries on where the last stopped. */
    @Test
    fun roundRobinRemembersItsPositionAcrossInstances() {
        val store = newStoreName()
        roundRobin(listOf(wallhaven("one"), wallhaven("two")), store = store).take(env.ctx)

        val reopened = roundRobin(listOf(wallhaven("one"), wallhaven("two")), store = store)
        assertEquals("two", reopened.take(env.ctx).store)
        assertEquals("one", reopened.take(env.ctx).store)
    }

    @Test
    fun roundRobinAdvancesPastASourceThatFailed() {
        val source = roundRobin(listOf(wallhaven(Env.FAILS), wallhaven("two")))
        source.sampleFailure(env.ctx)
        assertEquals("two", source.take(env.ctx).store)
    }

    @Test
    fun roundRobinFailsWhenTheSourceItsTurnLandedOnFails() {
        val source = roundRobin(listOf(wallhaven(Env.FAILS), wallhaven("two")))
        assertEquals(
            SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN),
            source.sampleFailure(env.ctx).failure,
        )
        assertEquals("a round robin must not quietly try another", 0, env.searches("two"))
    }

    /** Stale state — say, the source list shrank since it was written — must wrap, not crash. */
    @Test
    fun roundRobinSurvivesAPositionLeftOutOfRange() {
        val farStore = newStoreName()
        env.stateFile(farStore).writeText("""{"next":99}""")
        val far = roundRobin(listOf(wallhaven("one"), wallhaven("two")), store = farStore)
        assertEquals("two", far.take(env.ctx).store)

        val negativeStore = newStoreName()
        env.stateFile(negativeStore).writeText("""{"next":-3}""")
        val negative = roundRobin(listOf(wallhaven("one"), wallhaven("two")), store = negativeStore)
        assertEquals("two", negative.take(env.ctx).store)
    }

    // ----------------------------------------------------------------- random

    @Test
    fun randomTakesFromOneOfTheSourcesItHolds() {
        val source = Random(listOf(wallhaven("one"), wallhaven("two")))
        repeat(6) { assertTrue(source.take(env.ctx).store in setOf("one", "two")) }
    }

    @Test
    fun randomEventuallyReachesEveryOneOfThem() {
        val source = Random(listOf(wallhaven("one"), wallhaven("two"), wallhaven("three")))
        val seen = (1..40).map { source.take(env.ctx).store }.toSet()
        assertEquals(setOf("one", "two", "three"), seen)
    }

    @Test
    fun randomFailsWhenTheSourceItPickedFails() {
        val source = Random(listOf(wallhaven(Env.FAILS), wallhaven(Env.FAILS)))
        assertEquals(
            SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN),
            source.sampleFailure(env.ctx).failure,
        )
    }

    // ------------------------------------------------------------ fallthrough

    @Test
    fun fallthroughTakesFromTheFirstThatWorks() {
        val source = Fallthrough(listOf(wallhaven("one"), wallhaven("two")))
        assertEquals("one", source.take(env.ctx).store)
        assertEquals(0, env.searches("two"))
    }

    @Test
    fun fallthroughMovesPastOneThatFails() {
        val source = Fallthrough(listOf(wallhaven(Env.FAILS), wallhaven(Env.EMPTY), wallhaven("three")))
        assertEquals("three", source.take(env.ctx).store)
        assertEquals(1, env.searches(Env.FAILS))
    }

    @Test
    fun fallthroughReportsTheFirstFailureWhenEveryOneOfThemFails() {
        val source = Fallthrough(listOf(wallhaven(Env.FAILS), wallhaven(Env.EMPTY)))
        assertEquals(
            SampleFailure.ProviderUnavailable(SourceProvider.WALLHAVEN),
            source.sampleFailure(env.ctx).failure,
        )
    }

    // ------------------------------------------------------------ composition

    @Test
    fun theyCompose() {
        val source = roundRobin(
            listOf(
                Fallthrough(listOf(wallhaven(Env.FAILS), wallhaven("standby"))),
                wallhaven("primary"),
            ),
        )
        assertEquals("standby", source.take(env.ctx).store)
        assertEquals("primary", source.take(env.ctx).store)
        assertNotEquals(0, env.searches(Env.FAILS))
    }
}
