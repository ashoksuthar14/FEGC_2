package eu.feg.ambient

import eu.feg.ambient.ambient.engine.AmbientEngine
import eu.feg.ambient.ambient.engine.AttentionBudget
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.MomentBuilder
import eu.feg.ambient.ambient.engine.Ownership
import eu.feg.ambient.ambient.engine.ProtectionEvaluator
import eu.feg.ambient.ambient.engine.RelevanceScorer
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.moment.DefaultRelevanceScorer
import eu.feg.ambient.ambient.engine.router.BanditRouter
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Drives the whole pipeline on the JVM, which is what the spec asks to observe by running the
 * simulator on a phone. Doing it here is faster, deterministic, and asserts the claim rather
 * than eyeballing it: most events produce nothing, and every one of them leaves a row saying
 * why.
 */
class EnginePipelineTest {

    private fun tempLedger() = Ledger(File.createTempFile("pipeline", ".json").also { it.delete() })

    /** Records what the engine asked the surfaces to do, without any Android involved. */
    private class RecordingController : SurfaceController {
        val started = mutableListOf<SlipSurfaceState>()
        val widgets = mutableListOf<WidgetState>()
        var alertsAttempted = 0
        var alertsAllowed = 1

        override suspend fun startLiveUpdate(state: SlipSurfaceState) { started += state }
        override suspend fun updateLiveUpdate(state: SlipSurfaceState) { started += state }
        override suspend fun endLiveUpdate(slipId: String, settled: Boolean) = Unit
        override suspend fun refreshWidget(state: WidgetState) { widgets += state }

        /** Counted, not recorded as a state: a redraw carries nothing to record. */
        var widgetRedraws = 0
        override suspend fun refreshWidgets() { widgetRedraws++ }
        /** entryId is recorded so a test can assert the alert carries its ledger row. */
        var lastAlertEntryId: String? = null
        override suspend fun postAlert(
            headline: String,
            detail: String,
            deepLink: String,
            entryId: String?,
            essential: Boolean,
        ): Boolean {
            lastAlertEntryId = entryId
            alertsAttempted++
            if (alertsAllowed <= 0) return false
            alertsAllowed--
            return true
        }
        override suspend fun refreshShortcuts(protection: ProtectionState) = Unit
        override fun refreshDiagnostics() = Unit
        override fun currentWidgetState(): WidgetState? = widgets.lastOrNull()
        override fun resetAlertBudget() = Unit
        override val diagnostics: StateFlow<SurfaceDiagnostics> = MutableStateFlow(SurfaceDiagnostics())
    }

    private fun engine(
        controller: RecordingController,
        ledger: Ledger,
        protection: ProtectionState = ProtectionState.NORMAL,
        ownEveryEvent: Boolean = true,
        allowed: Set<Surface> = setOf(Surface.LIVE_UPDATE, Surface.WIDGET, Surface.IN_APP, Surface.ALERT),
        /** The real scorer by default: a flat stub would prove the plumbing, not the judgement. */
        scorer: RelevanceScorer = DefaultRelevanceScorer(),
    ) = AmbientEngine(
        protectionEvaluator = object : ProtectionEvaluator {
            override suspend fun evaluate() = protection
        },
        momentBuilder = object : MomentBuilder {
            override suspend fun build(event: MatchEvent, protection: ProtectionState): Moment? {
                if (!ownEveryEvent) return null
                return Moment(
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
            }
        },
        scorer = scorer,
        attentionBudget = object : AttentionBudget {
            override suspend fun allowedSurfaces(moment: Moment, protection: ProtectionState) = allowed
        },
        router = BanditRouter(ledger, Random(99)),
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

    // --- 2. protection precedes relevance ---------------------------------------------

    @Test
    fun `blocked and unverified produce no moment and no surface`() = runTest {
        listOf(ProtectionState.BLOCKED, ProtectionState.UNVERIFIED).forEach { state ->
            val controller = RecordingController()
            val ledger = tempLedger()
            val engine = engine(controller, ledger, protection = state)

            repeat(10) { engine.onEvent(event(60 + it)) }

            assertTrue(state.name + " must render nothing", controller.started.isEmpty())
            assertTrue(state.name + " must not touch the widget", controller.widgets.isEmpty())
            assertEquals("every event still leaves a row", 10, ledger.entries.value.size)
            assertTrue(
                "and the row must say why",
                ledger.entries.value.all { it.reason.contains("Withheld") },
            )
        }
    }

    // --- 4. the alert budget holds under a burst --------------------------------------

    @Test
    fun `a burst of twenty events sends at most one alert`() = runTest {
        val controller = RecordingController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger, allowed = setOf(Surface.ALERT))

        repeat(20) { engine.onEvent(event(60 + it)) }

        val alertsSent = ledger.entries.value.count {
            it.surface == Surface.ALERT.name && it.shownAt != null
        }
        assertTrue("at most one alert may escape a burst, got " + alertsSent, alertsSent <= 1)
        assertEquals("every event is still accounted for", 20, ledger.entries.value.size)
    }

    // --- 1 and 2. the engine drives, and silence is explained -------------------------

    @Test
    fun `an event the user does not own is silent and says so`() = runTest {
        val controller = RecordingController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger, ownEveryEvent = false)

        repeat(15) { engine.onEvent(event(60 + it)) }

        assertTrue(controller.started.isEmpty())
        assertEquals(15, ledger.entries.value.size)
        assertTrue(
            ledger.entries.value.all { it.reason.contains("Not yours") },
        )
    }

    @Test
    fun `a run of events reports how many surfaced and how many were silent`() = runTest {
        val controller = RecordingController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger)

        repeat(20) { engine.onEvent(event(60 + it)) }

        val rows = ledger.entries.value
        val surfaced = rows.count { it.shownAt != null }
        val silent = rows.size - surfaced

        println("PIPELINE events=" + rows.size + " surfaced=" + surfaced + " silent=" + silent)
        println("PIPELINE alerts attempted=" + controller.alertsAttempted)

        assertEquals("every event leaves exactly one row", 20, rows.size)
        assertTrue("every row explains itself", rows.all { it.reason.isNotBlank() })
        assertTrue(
            "restraint is the product: a burst of repeats on one match must mostly stay quiet, " +
                "got " + surfaced + " surfaced of " + rows.size,
            silent > surfaced,
        )
    }

    // --- 6. silence can be rewarded ---------------------------------------------------

    @Test
    fun `opening the app unprompted rewards a recent silence`() = runTest {
        val controller = RecordingController()
        val ledger = tempLedger()
        val engine = engine(controller, ledger, allowed = emptySet())

        engine.onEvent(event(61))
        val silentRow = ledger.entries.value.single()
        assertEquals(Surface.NOTHING.name, silentRow.surface)

        engine.onAppOpenedUnprompted()

        val rewarded = ledger.entries.value.single()
        assertTrue(
            "staying quiet and being visited anyway is the behaviour we pay for",
            (rewarded.reward ?: 0.0) > 0.0,
        )
    }
}
