package eu.feg.ambient

import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.Selection
import eu.feg.ambient.data.repo.BetRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The step 7 acceptance path, end to end: add three selections, stake, place, and check
 * that BetPlaced fires and the bet lands in My bets with its legs (Phase 2 seams 1 and 4).
 */
class BetFlowTest {

    private val source = MockDataSource { path -> File("src/main/assets/" + path).readText() }

    private val fixedClock = object : MatchClock {
        override val ticks: Flow<Unit> = emptyFlow()
        override fun now(): Instant = Instant.parse("2026-09-08T18:00:00Z")
    }

    private fun threeSelections(): List<Pair<Selection, String>> =
        source.matches().take(3).map { match ->
            val market = match.markets.first()
            val outcome = market.outcomes.first()
            Selection(match.id, market.id, outcome.id, outcome.odds) to
                (match.home.name + " - " + match.away.name + " · " + outcome.label)
        }

    @Test
    fun `placing a slip emits betPlaced and creates an open bet with legs`() = runTest {
        val repo = BetRepository(fixedClock)
        val picks = threeSelections()
        picks.forEach { (selection, description) -> repo.toggle(selection, description) }
        repo.setStake(10.0)

        assertEquals(3, repo.slip.value.selections.size)

        // betPlaced is a hot SharedFlow with no replay, and runTest is single-threaded, so
        // the collector must reach its subscribe point before place() emits. UNDISPATCHED
        // runs it inline up to that first suspension.
        val emitted = async(start = CoroutineStart.UNDISPATCHED) { repo.betPlaced.first() }
        val id = repo.place()

        assertEquals("betPlaced carried a different id", id, emitted.await())

        val bet = repo.placedBets.value.single()
        assertEquals(BetStatus.OPEN, bet.status)
        assertEquals("legs must mirror the selections", 3, bet.legs.size)
        assertTrue("legs start pending", bet.legs.all { it.status == LegStatus.PENDING })
        assertTrue("every leg keeps its matchId", bet.legs.all { it.matchId.isNotBlank() })
        assertTrue("every leg is described in words", bet.legs.all { it.description.contains(" - ") })
    }

    @Test
    fun `total odds and payout multiply out`() = runTest {
        val repo = BetRepository(fixedClock)
        val picks = threeSelections()
        picks.forEach { (selection, description) -> repo.toggle(selection, description) }
        repo.setStake(20.0)

        val expected = picks.fold(1.0) { acc, (selection, _) -> acc * selection.oddsAtPick }
        assertEquals(expected, repo.slip.value.totalOdds, 0.0001)
        assertEquals(expected * 20.0, repo.slip.value.possiblePayout, 0.0001)
    }

    @Test
    fun `the slip clears once the bet is placed`() = runTest {
        val repo = BetRepository(fixedClock)
        val (selection, description) = threeSelections().first()
        repo.toggle(selection, description)
        repo.setStake(5.0)
        repo.place()

        assertTrue("slip should be empty", repo.slip.value.selections.isEmpty())
        assertEquals(0.0, repo.slip.value.stake, 0.0)
    }

    @Test
    fun `an empty slip or a zero stake places nothing`() = runTest {
        val repo = BetRepository(fixedClock)
        assertNull("empty slip must not place", repo.place())

        val (selection, description) = threeSelections().first()
        repo.toggle(selection, description)
        assertNull("zero stake must not place", repo.place())
        assertTrue(repo.placedBets.value.isEmpty())
    }

    @Test
    fun `tapping the same price twice removes it`() = runTest {
        val repo = BetRepository(fixedClock)
        val (selection, description) = threeSelections().first()
        repo.toggle(selection, description)
        assertEquals(1, repo.slip.value.selections.size)
        repo.toggle(selection, description)
        assertTrue("second tap should remove the pick", repo.slip.value.selections.isEmpty())
    }

    @Test
    fun `a second pick on the same match replaces the first`() = runTest {
        val repo = BetRepository(fixedClock)
        val match = source.matches().first()
        val market = match.markets.first()

        repo.toggle(
            Selection(match.id, market.id, market.outcomes[0].id, market.outcomes[0].odds),
            "first pick",
        )
        repo.toggle(
            Selection(match.id, market.id, market.outcomes[1].id, market.outcomes[1].odds),
            "second pick",
        )

        val only = repo.slip.value.selections.single()
        assertEquals(market.outcomes[1].id, only.outcomeId)
    }
}
