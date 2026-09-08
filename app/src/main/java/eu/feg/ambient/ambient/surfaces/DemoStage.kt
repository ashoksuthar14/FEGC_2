package eu.feg.ambient.ambient.surfaces

import android.util.Log
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.BetStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * Numbered, because the stage is armed again each time its matches run out: [track]
     * refuses an id it already holds, so a second slip needs a second id.
     */
    const val BET_ID = "demo-stage"

    private var armCount = 0

    /**
     * One arming at a time.
     *
     * The launch-time call and the watcher's first tick landed together and both passed the
     * "is a slip in play" guard before either had tracked one, so the demo opened with two
     * slips competing for a single Live Update. The guard has to be read and acted on without
     * anything else in between.
     */
    private val gate = Mutex()

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
    suspend fun arm(container: AppContainer): Boolean = gate.withLock { armLocked(container) }

    private suspend fun armLocked(container: AppContainer): Boolean {
        // A SLIP STILL IN PLAY ALWAYS WINS, whoever placed it. A second card would compete
        // for one Live Update and the one that loses could be the customer's own. Settled
        // slips do not count: matches reach full time in under a minute of demo clock, and a
        // stage that could only be armed once would spend the pitch showing a final score.
        val slipInPlay = container.betRepository.placedBets.value.any { bet ->
            bet.status == BetStatus.OPEN && bet.legs.any { leg ->
                container.matchRepository.match(leg.matchId)?.state == MatchState.LIVE
            }
        }
        if (slipInPlay) return false

        // A CLUB THE CARD CAN DRESS BEATS A FIXTURE THAT IS MERELY FURTHER ALONG.
        //
        // LiveMatchCard puts a crest either side of the score and names the competition in
        // its header, and ClubThemes knows eight clubs. For anyone else forTeam falls back to
        // neutral(): both crests become the same flat block with initials on them and the
        // header reads "1. AZERBAIJAN". The card is unchanged and every word on it is true,
        // and it looks like a wireframe. So a themed fixture is taken first, and only then
        // the deepest into its match -- the score is what the surface is for, and a fixture
        // at 86' has more to say than one at 15'.
        val live = container.matchRepository.matches.value.filter { it.state == MatchState.LIVE }
        val (themed, plain) = live.partition {
            ClubThemes.byName(it.home.name) != null || ClubThemes.byName(it.away.name) != null
        }
        val deepestFirst = compareByDescending<Match> { it.minute ?: 0 }
        val ordered = themed.sortedWith(deepestFirst) + plain.sortedWith(deepestFirst)
        if (ordered.size < LEGS) {
            Log.w(TAG, "not arming: only " + ordered.size + " live fixtures, need " + LEGS)
            return false
        }

        val now = container.clock.now()
        val active = ordered[0]
        val landed = ordered[1]
        val running = ordered[2]

        armCount++
        val bet = PlacedBet(
            id = if (armCount == 1) BET_ID else BET_ID + "-" + armCount,
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
     * Puts the stage back on a running match when its own has finished.
     *
     * The mock clock runs three seconds to the match minute, so a slip placed at 74' is
     * settled inside a minute; MatchRepository.promoteKickoffs keeps twelve fixtures on the
     * pitch, and without this the demo watched them go by with a final score on the card.
     * The check is the same one [arm] makes, so nothing is re-armed over a slip in play --
     * the customer's included.
     */
    fun keepArmed(container: AppContainer, scope: CoroutineScope) {
        scope.launch {
            container.clock.ticks.collect { arm(container) }
        }
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
