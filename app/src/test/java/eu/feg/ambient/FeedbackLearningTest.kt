package eu.feg.ambient

import eu.feg.ambient.ambient.engine.AmbientEngine
import eu.feg.ambient.ambient.engine.AttentionBudget
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.MomentBuilder
import eu.feg.ambient.ambient.engine.Ownership
import eu.feg.ambient.ambient.engine.ProtectionEvaluator
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.moment.DefaultRelevanceScorer
import eu.feg.ambient.ambient.engine.router.BanditRouter
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SurfaceController
import eu.feg.ambient.ambient.surfaces.SurfaceDiagnostics
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Does the thing actually learn?
 *
 * The claim on the slide is that the router improves from what the customer does, and until
 * now the only evidence for it was that the code looked like it should. Worse, only one of the
 * four signals was connected: a thumb on the widget. A tap on a notification called nothing, a
 * swipe called nothing, and opening the app after a stretch of silence called nothing -- so
 * three of the four ways a person answers this product were invisible to the part that claims
 * to listen.
 *
 * These tests assert the loop end to end on the JVM: a decision is made, a gesture arrives
 * naming that decision, and the arm's own counters move in the right direction. They fail if
 * anybody unhooks a signal again, which is the failure that is otherwise completely silent --
 * the app carries on working perfectly and simply stops learning.
 */
class FeedbackLearningTest {

    private fun tempLedger() = Ledger(File.createTempFile("feedback", ".json").also { it.delete() })

    private class SilentController : SurfaceController {
        var lastAlertEntryId: String? = null
        override suspend fun startLiveUpdate(state: SlipSurfaceState) = Unit
        override suspend fun updateLiveUpdate(state: SlipSurfaceState) = Unit
        override suspend fun endLiveUpdate(slipId: String, settled: Boolean) = Unit
        override suspend fun refreshWidget(state: WidgetState) = Unit
        override suspend fun refreshWidgets() = Unit
        override suspend fun postAlert(
            headline: String,
            detail: String,
            deepLink: String,
            entryId: String?,
            essential: Boolean,
        ): Boolean {
            lastAlertEntryId = entryId
            return true
        }
        override suspend fun refreshShortcuts(protection: ProtectionState) = Unit
        override fun currentWidgetState(): WidgetState? = null
        override fun refreshDiagnostics() = Unit
        override fun resetAlertBudget() = Unit
        override val diagnostics: StateFlow<SurfaceDiagnostics> =
            MutableStateFlow(SurfaceDiagnostics())
    }

    private fun engine(
        controller: SurfaceController,
        ledger: Ledger,
        allowed: Set<Surface> = setOf(Surface.ALERT),
    ) = AmbientEngine(
        protectionEvaluator = object : ProtectionEvaluator {
            override suspend fun evaluate() = ProtectionState.NORMAL
        },
        momentBuilder = object : MomentBuilder {
            override suspend fun build(event: MatchEvent, protection: ProtectionState) = Moment(
                id = "m-" + event.matchId + "-" + event.minute,
                type = event.type,
                facts = MomentFacts(
                    type = event.type,
                    homeTeam = event.homeTeam,
                    awayTeam = event.awayTeam,
                    homeScore = event.homeScore,
                    awayScore = event.awayScore,
                    minute = event.minute,
                    legsTotal = 3,
                    legsWon = 2,
                    myLegDescription = "Liverpool win",
                    minutesRemaining = (90 - event.minute).coerceAtLeast(0),
                ),
                slipId = "slip-1",
                matchId = event.matchId,
                ownership = Ownership.ON_MY_SLIP,
                createdAt = Clock.System.now(),
            )
        },
        scorer = DefaultRelevanceScorer(),
        attentionBudget = object : AttentionBudget {
            override suspend fun allowedSurfaces(moment: Moment, protection: ProtectionState) = allowed
        },
        router = BanditRouter(ledger, Random(7)),
        narrator = TemplateNarrator(),
        surfaceController = controller,
        ledger = ledger,
    )

    private fun event(minute: Int, matchId: String = "match-1") = MatchEvent(
        matchId = matchId,
        type = MomentType.GOAL_ON_SLIP,
        minute = minute,
        homeTeam = "Liverpool",
        awayTeam = "Ipswich",
        homeScore = 1,
        awayScore = 0,
        at = Clock.System.now(),
    )

    /**
     * The alert has to name the decision it came from, or no gesture on it can be attributed.
     *
     * This is the plumbing the other two tests stand on: before it existed, the ledger row was
     * minted after the notification was posted, so the notification could not carry an id that
     * did not yet exist.
     */
    @Test
    fun `an alert carries the id of the ledger row it is`() = runTest {
        val controller = SilentController()
        val ledger = tempLedger()
        engine(controller, ledger).onEvent(event(61))

        val row = ledger.entries.value.single { it.surface == Surface.ALERT.name }
        assertNotNull("the alert must carry an entry id", controller.lastAlertEntryId)
        assertEquals("and it must be THIS row's id", row.id, controller.lastAlertEntryId)
    }

    /**
     * A SWIPE IS NEGATIVE, and the arm that sent it takes the loss.
     *
     * The important half of the assertion is the arm, not the row: recording a reward against
     * a ledger entry is bookkeeping, and moving the Beta counters is the part that changes
     * what the app does next time.
     */
    @Test
    fun `swiping an alert away teaches the arm that sent it`() = runTest {
        val controller = SilentController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger)
        engine.onEvent(event(61))

        val row = ledger.entries.value.single { it.surface == Surface.ALERT.name }
        assertNull("nothing has answered yet", row.reward)
        val before = ledger.arm(row.contextBucket, row.armId)

        engine.onDismissed(row.id)

        val after = ledger.arm(row.contextBucket, row.armId)
        assertNotNull("the arm must exist once it has been answered", after)
        assertTrue(
            "a swipe must move losses up: " + before?.losses + " -> " + after?.losses,
            (after!!.losses) > (before?.losses ?: 0.0),
        )
        assertEquals(
            "and the wins must not move",
            before?.wins ?: 0.0,
            after.wins,
            0.0001,
        )
        val marked = ledger.entries.value.single { it.id == row.id }
        assertNotNull("the row carries the reward it earned", marked.reward)
        assertTrue("a dismissal is a negative reward", marked.reward!! < 0.0)
        assertNotNull("and the row records that it was dismissed", marked.dismissedAt)
    }

    /** A tap is the positive half, and it moves the other counter. */
    @Test
    fun `tapping an alert teaches the arm that sent it`() = runTest {
        val controller = SilentController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger)
        engine.onEvent(event(61))

        val row = ledger.entries.value.single { it.surface == Surface.ALERT.name }
        val before = ledger.arm(row.contextBucket, row.armId)

        engine.onTapped(row.id)

        val after = ledger.arm(row.contextBucket, row.armId)!!
        assertTrue(
            "a tap must move wins up: " + before?.wins + " -> " + after.wins,
            after.wins > (before?.wins ?: 0.0),
        )
        val marked = ledger.entries.value.single { it.id == row.id }
        assertEquals("a prompt tap is worth the full reward", RewardTable.TAP, marked.reward!!, 0.0001)
    }

    /**
     * Silence is rewarded too, or the router only ever learns from the times it spoke.
     *
     * That bias is the subtle one: an engine that is never told its silences were right will
     * drift toward speaking, because speaking is the only thing that can be scored. The
     * customer opening the app unprompted within the hour is the evidence, and it is free.
     */
    @Test
    fun `opening the app unprompted rewards the silences`() = runTest {
        val controller = SilentController()
        val ledger = tempLedger()
        // Nothing is allowed, so every decision is NOTHING and every row is a silence.
        val engine = engine(controller, ledger, allowed = emptySet())
        repeat(3) { engine.onEvent(event(60 + it)) }

        val silences = ledger.entries.value.filter { it.surface == Surface.NOTHING.name }
        assertEquals(3, silences.size)
        assertTrue("no silence has been paid for yet", silences.all { it.reward == null })

        engine.onAppOpenedUnprompted()

        val paid = ledger.entries.value.filter { it.surface == Surface.NOTHING.name }
        assertTrue(
            "every recent silence must be rewarded",
            paid.all { it.reward == RewardTable.CORRECT_SILENCE },
        )
    }
}
