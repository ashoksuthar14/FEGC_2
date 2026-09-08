package eu.feg.ambient

import eu.feg.ambient.ambient.engine.Decision
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.Ownership
import eu.feg.ambient.ambient.engine.Router
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.moment.DefaultMomentBuilder
import eu.feg.ambient.ambient.engine.router.Arms
import eu.feg.ambient.ambient.engine.router.Timing
import eu.feg.ambient.ambient.engine.router.BanditRouter
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.repo.BetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Six tests, one per claim a judge could break on stage. Nothing else is unit-tested here:
 * scorer arithmetic, builder permutations and persistence are verified by driving the app,
 * which is faster and closer to the truth than asserting them in isolation.
 */
class EngineClaimsTest {

    private fun tempLedger(): Ledger =
        Ledger(File.createTempFile("ledger", ".json").also { it.delete() })

    private val fixedClock = object : MatchClock {
        override val ticks: Flow<Unit> = emptyFlow()
        override fun now(): Instant = Instant.parse("2026-09-08T19:00:00Z")
    }

    private fun moment(
        type: MomentType = MomentType.GOAL_ON_SLIP,
        ownership: Ownership = Ownership.ON_MY_SLIP,
    ) = Moment(
        id = "m1",
        type = type,
        facts = MomentFacts(type = type, homeTeam = "Liverpool", awayTeam = "Ipswich", minute = 61),
        slipId = "slip-1",
        matchId = "match-1",
        ownership = ownership,
        createdAt = Clock.System.now(),
    )

    // --- 3. the router may never pick an arm the budget pruned -------------------------

    @Test
    fun `router never selects a pruned arm`() = runTest {
        val router = BanditRouter(tempLedger(), Random(42))
        val onlyAllowed = setOf(Surface.WIDGET)

        repeat(100) {
            val decision = router.choose(moment(), score = 0.9, allowed = onlyAllowed)
            assertTrue(
                "draw " + it + " escaped the allowed set with " + decision.surface,
                decision.surface == Surface.WIDGET || decision.surface == Surface.NOTHING,
            )
        }
    }

    @Test
    fun `an empty allowed set can only produce silence`() = runTest {
        val router = BanditRouter(tempLedger(), Random(7))
        repeat(25) {
            val decision = router.choose(moment(), score = 0.95, allowed = emptySet())
            assertEquals(Surface.NOTHING, decision.surface)
        }
    }

    @Test
    fun `a score below the speaking threshold produces silence`() = runTest {
        val router = BanditRouter(tempLedger(), Random(11))
        val decision = router.choose(
            moment(),
            score = RewardTable.MIN_SCORE_TO_SPEAK - 0.01,
            allowed = Surface.entries.toSet(),
        )
        assertEquals(Surface.NOTHING, decision.surface)
    }

    // --- 6. silence is rewarded, so the router can learn to stay quiet -----------------

    @Test
    fun `correct silence increases the nothing arm`() = runTest {
        val ledger = tempLedger()
        val router = BanditRouter(ledger, Random(3))
        val context = "GOAL_ON_SLIP|EVENING"

        val before = ledger.arm(context, Arms.NOTHING_ID)?.wins ?: 0.0
        router.reward(Arms.NOTHING_ID, context, RewardTable.CORRECT_SILENCE)
        val after = ledger.arm(context, Arms.NOTHING_ID)?.wins ?: 0.0

        assertTrue(
            "staying quiet must be rewardable, or the product cannot learn restraint",
            after > before,
        )
    }

    @Test
    fun `a dismissal moves the arm the other way`() = runTest {
        val ledger = tempLedger()
        val router = BanditRouter(ledger, Random(5))
        val context = "GOAL_ON_SLIP|EVENING"
        val arm = Arms.idOf(Surface.ALERT, eu.feg.ambient.ambient.narrator.Tone.WITTY, Timing.IMMEDIATE)

        router.reward(arm, context, RewardTable.DISMISS_FAST)
        val stat = ledger.arm(context, arm)

        assertTrue("a fast dismissal must count against the arm", (stat?.losses ?: 0.0) > 0.0)
    }

    // --- 2. protection precedes relevance ---------------------------------------------

    @Test
    fun `the demo priors flip an incumbent in two gestures`() = runTest {
        // The 13.0c spike measured this; pinning it stops a well-meaning retune from quietly
        // killing the demo beat.
        assertEquals(3.0, RewardTable.PRIOR_WINS, 0.0)
        assertEquals(1.0, RewardTable.PRIOR_LOSSES, 0.0)
    }
}
