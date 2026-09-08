package eu.feg.ambient.ambient.engine.moment

import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.repo.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * "Hajduk kick off in 40 minutes" — the one pre-match moment, for the club the customer
 * chose to follow.
 *
 * It rides the app's existing [MatchClock] rather than a scheduler: the clock already ticks
 * while the app is alive, precision to the minute is all a forty-minute lead needs, and a
 * job scheduler would be a new dependency with a full rebuild behind it. Like the goal
 * events in SurfaceCoordinator, this only *reports* — the engine decides whether anything
 * is shown and on which surface, and this class never touches a SurfaceController.
 */
class KickoffMomentSource(
    private val scope: CoroutineScope,
    private val clock: MatchClock,
    private val matchRepository: MatchRepository,
    private val myClubId: () -> String?,
    private val protection: suspend () -> ProtectionState,
    private val onEvent: suspend (MatchEvent) -> Unit,
) {

    /**
     * Match ids already announced. A match is announced ONCE, ever: the window below is
     * two minutes wide and the clock ticks every second, so without this the same fact would
     * be re-emitted a hundred and twenty times — which is exactly the notification spam
     * this product exists to remove. The engine's budget would catch most of it; the point
     * is that it never has to.
     */
    private val announced = mutableSetOf<String>()

    fun start() {
        scope.launch {
            clock.ticks.collect { onTick() }
        }
    }

    private suspend fun onTick() {
        val clubId = myClubId() ?: return
        val now = clock.now()
        val due = matchRepository.matches.value.filter { match ->
            match.state == MatchState.PREMATCH &&
                match.id !in announced &&
                match.kickoff - now in WINDOW &&
                followedTeamIn(match, clubId) != null
        }
        if (due.isEmpty()) return

        // Protection is asked only once something is actually due — it may consult the
        // exclusion register, and a once-a-second tick is not the place for that.
        //
        // Nothing is emitted unless protection is NORMAL. UNVERIFIED and BLOCKED are the
        // engine's rule anyway. CALM is withheld here, specifically for this moment: a
        // kick-off notice is an invitation to attend, however plainly it is worded, and
        // Calm Mode is the customer closing invitations. A goal on a slip they already hold
        // is still theirs to know about; a match they hold nothing on is not.
        //
        // The match is not marked announced when withheld. If the customer leaves Calm Mode
        // while the window is still open, the fact is still true and still theirs.
        if (protection() != ProtectionState.NORMAL) return

        due.forEach { match ->
            announced += match.id
            onEvent(
                MatchEvent(
                    matchId = match.id,
                    type = MomentType.KICKOFF_FOLLOWED,
                    minute = 0,
                    homeTeam = match.home.name,
                    awayTeam = match.away.name,
                    homeScore = 0,
                    awayScore = 0,
                    period = match.period,
                    kickoffInMinutes = (match.kickoff - now).inWholeMinutes.toInt(),
                    at = now,
                ),
            )
        }
    }

    /**
     * Resolved by name, as the widget does, because fixtures carry team names and the club
     * store carries ids; [ClubThemes.byName] already folds the diacritics ("Varaždin" and
     * "Varazdin" are the same club).
     */
    private fun followedTeamIn(match: Match, clubId: String): String? = when (clubId) {
        ClubThemes.byName(match.home.name)?.clubId -> match.home.name
        ClubThemes.byName(match.away.name)?.clubId -> match.away.name
        else -> null
    }

    companion object {
        /** How far ahead of kick-off the moment lands. */
        val LEAD: Duration = 40.minutes

        /**
         * One minute either side of [LEAD]. The clock is expected to tick far more often
         * than that, so the window is a tolerance for a paused process rather than a
         * schedule — a tick that lands anywhere inside it fires, and [announced] makes sure
         * the next tick does not.
         */
        val WINDOW: ClosedRange<Duration> = (LEAD - 1.minutes)..(LEAD + 1.minutes)
    }
}
