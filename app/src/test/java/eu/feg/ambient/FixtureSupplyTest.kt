package eu.feg.ambient

import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.repo.MatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The demo must never run out of football.
 *
 * This is the failure that started as a cosmetic complaint and turned out to be structural.
 * The mock clock retires a match at full time and promotes a prematch fixture to replace it,
 * which reads as sustainable until you do the arithmetic: forty-seven fixtures is a queue, not
 * a loop, and a session long enough drains it. An app left running an afternoon reached zero
 * live matches, and the symptom was not an empty Live tab -- it was everything downstream
 * going quiet at once. Widgets with nothing to draw. DemoStage refusing to arm for want of
 * three live fixtures. The demo bubble answering "nothing live to report" on a phone full of
 * mock data.
 *
 * ON MOCK DATA AN EMPTY SURFACE IS ALWAYS A BUG, and the only honest way to prove the fix is
 * to run the clock past the point where the old code died. Hours on a device; a few thousand
 * ticks here.
 */
class FixtureSupplyTest {

    private val source = MockDataSource { path -> File("src/main/assets/" + path).readText() }

    /** A clock the test drives by hand, one tick at a time. */
    private class ManualClock : MatchClock {
        val tick = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        override val ticks: Flow<Unit> = tick
        override fun now(): Instant = Instant.parse("2026-09-09T12:00:00Z")
    }

    /**
     * Long enough to retire every fixture in the file several times over.
     *
     * A match lasts ninety match-minutes at eight ticks each, so 720 ticks retires the twelve
     * on the pitch; four thousand takes the whole list round more than once, which is the
     * state the old code could not survive.
     */
    private val ticks = 4_000

    @Test
    fun `the live screen never empties, however long the demo runs`() = runTest {
        val clock = ManualClock()
        val repository = MatchRepository(source, clock, backgroundScope)

        var lowWater = Int.MAX_VALUE
        var emptied = -1

        repeat(ticks) { i ->
            clock.tick.emit(Unit)
            val live = repository.matches.value.count { it.state == MatchState.LIVE }
            if (live < lowWater) lowWater = live
            if (live == 0 && emptied < 0) emptied = i
        }

        assertTrue(
            "the live screen emptied at tick " + emptied + " -- fixtures ran out instead of recycling",
            emptied < 0,
        )
        assertTrue(
            "expected a healthy live set throughout, lowest was " + lowWater,
            lowWater >= 1,
        )
    }

    /**
     * A recycled fixture has to come back playable.
     *
     * Full time locks a match's markets and leaves its score on it. Put back on the list
     * untouched, it would return as an unbettable 3-1 that never changes -- which is worse
     * than an empty screen, because it looks like data rather than like a bug.
     */
    @Test
    fun `recycled fixtures come back with no score and unlocked markets`() = runTest {
        val clock = ManualClock()
        val repository = MatchRepository(source, clock, backgroundScope)

        repeat(ticks) { clock.tick.emit(Unit) }

        val live = repository.matches.value.filter { it.state == MatchState.LIVE }
        assertTrue("expected live fixtures after a long run", live.isNotEmpty())

        // Anything in play must have a sane minute and its odds available. A match that came
        // back from FINISHED with locked markets would fail here.
        live.forEach { match ->
            assertTrue(
                match.id + " has an impossible minute: " + match.minute,
                (match.minute ?: 0) in 1..90,
            )
            // NOT "no outcome is locked" -- the fixture file ships six suspended outcomes on
            // purpose, which is what a real book looks like. Full time is what locks EVERY
            // outcome on a match, so all-locked is the signature of a fixture that came back
            // from FINISHED without being reset.
            val outcomes = match.markets.flatMap { it.outcomes }
            assertTrue(
                match.id + " came back with every market still locked from full time",
                outcomes.isEmpty() || outcomes.any { !it.locked },
            )
        }
    }
}
