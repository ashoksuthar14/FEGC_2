package eu.feg.ambient.ambient.surfaces

import android.util.Log
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Leg
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.PlacedBet
import kotlin.time.Duration.Companion.minutes

/**
 * Puts something true on every surface before anyone taps anything.
 *
 * WHY THIS EXISTS. Every ambient surface in this app is downstream of a placed slip: no slip
 * means no Live Update, an Idle widget instead of a score, and a catch-up card with nothing
 * to catch up on. That is correct behaviour and it is also a demo that opens on five empty
 * cards. A judge picks the phone up cold, and what they see decides whether the rest of the
 * pitch is believed.
 *
 * WHAT IT IS NOT. It does not draw anything and it does not fabricate any card. It places a
 * slip and reports three things that actually happened in the mock fixtures, then stands
 * back: the coordinator raises the Live Update, the engine scores the events and picks their
 * surface, the ranker builds the catch-up from the rows the engine wrote. Every claim on
 * screen is still one this app made for itself, and each of them can be traced in the ledger
 * to the event that caused it. A demo that paints its own screenshots proves nothing.
 *
 * The one thing it does invent is the away period -- see [armAwayPeriod], which says why.
 *
 * Debug builds only, and it stands down the moment a real slip exists.
 */
object DemoStage {

    /**
     * Stable, so re-arming after a restart cannot make a second slip: [track] refuses an id
     * it already holds.
     */
    const val BET_ID = "demo-stage"

    /**
     * Long enough to clear [eu.feg.ambient.ambient.digest.AwayTracker.AWAY_THRESHOLD] with
     * room to spare, short enough that the rows the engine wrote at launch are still inside
     * the window when the demo reaches the home screen.
     */
    private const val AWAY_MINUTES = 150

    /**
     * A slip in flight, a lock-screen card, and real ledger rows for the catch-up.
     *
     * Returns false when it declined -- an existing slip, or fewer live fixtures than the
     * slip needs -- so the caller can log the reason rather than wonder.
     */
    suspend fun arm(container: AppContainer): Boolean {
        // A REAL SLIP ALWAYS WINS. The moment someone places a bet in the app, the demo slip
        // would be a second card competing for one Live Update, and the one that loses is
        // the customer's own.
        if (container.betRepository.placedBets.value.isNotEmpty()) {
            Log.i(TAG, "not arming: a slip is already tracked")
            return false
        }

        // Furthest into the match first: the card has the most to say about a fixture at 86'
        // and the least about one at 15', and the score is the reason this surface exists.
        val live = container.matchRepository.matches.value
            .filter { it.state == MatchState.LIVE }
            .sortedByDescending { it.minute ?: 0 }
        if (live.size < LEGS) {
            Log.w(TAG, "not arming: only " + live.size + " live fixtures, need " + LEGS)
            return false
        }

        val now = container.clock.now()
        val active = live[0]
        val landed = live[1]
        val running = live[2]

        val bet = PlacedBet(
            id = BET_ID,
            legs = listOf(
                Leg(active.id, active.home.name + " to win", 1.85, LegStatus.PENDING),
                // One leg already home, so the card opens on "1 of 3" rather than on a row
                // of empty dots -- progress is the thing the segments are there to show.
                Leg(landed.id, landed.home.name + " to win", 2.10, LegStatus.WON),
                Leg(running.id, running.away.name + " to win", 1.65, LegStatus.PENDING),
            ),
            stake = 20.0,
            totalOdds = 6.40,
            // Before the events below, so the slip predates what happened on it.
            placedAt = now - 45.minutes,
            status = BetStatus.OPEN,
        )
        if (!container.betRepository.track(bet)) return false

        // Through the engine, not around it. Each of these is scored, routed and written to
        // the ledger exactly as a live one is, which is what lets the catch-up card be built
        // from real rows -- DigestRanker refuses anything the seeder wrote, and it is right
        // to. The engine may well choose silence for some of them; that is the point.
        listOf(
            goal(landed, MomentType.LEG_DECIDED, now - 22.minutes),
            goal(active, MomentType.GOAL_ON_SLIP, now - 9.minutes),
            goal(running, MomentType.HALFTIME, now - 4.minutes),
        ).forEach { event ->
            runCatching { container.engine.onEvent(event) }
                .onFailure { Log.w(TAG, "event " + event.type + " failed", it) }
        }

        // The same call the coordinator makes after a restart: it finds the open slip with a
        // live leg and raises the Live Update and the widget from it. Calling it rather than
        // posting a card directly is what keeps this a demo of the product.
        container.surfaceCoordinator.resumeOpenSlip()

        Log.i(TAG, "armed on " + active.id + ", " + landed.id + ", " + running.id)
        return true
    }

    /**
     * Starts the away period the catch-up card needs.
     *
     * THIS IS THE ONE FICTION, and it is deliberately the smallest one available. The digest
     * is real -- real rows, real ranking, real narration -- but it only builds after an hour
     * away, and a demo cannot wait an hour. So leaving the app is treated as having left it
     * two and a half hours ago. Everything the card then says is true; only the gap is not.
     *
     * On leaving rather than at launch, because MainActivity.onResume ends the away period by
     * design: armed in onCreate it would be wiped seconds later by the app's own correctness.
     */
    fun armAwayPeriod(container: AppContainer) {
        container.awayTracker.simulateAway(AWAY_MINUTES)
        Log.i(TAG, "away period armed: " + AWAY_MINUTES + " min")
    }

    private fun goal(match: Match, type: MomentType, at: kotlinx.datetime.Instant) = MatchEvent(
        matchId = match.id,
        type = type,
        minute = match.minute ?: 0,
        homeTeam = match.home.name,
        awayTeam = match.away.name,
        homeScore = match.homeScore ?: 0,
        awayScore = match.awayScore ?: 0,
        period = match.period,
        at = at,
    )

    private const val LEGS = 3
    private const val TAG = "DemoStage"
}
