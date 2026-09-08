package eu.feg.ambient.ambient.gaming

import android.util.Log
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.SessionFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/** What the game screen draws: how long this session has run, and whether one is running. */
data class GameSession(
    val gameId: String? = null,
    val gameName: String? = null,
    val startedAt: Instant? = null,
    val minutes: Int = 0,
) {
    val running: Boolean get() = startedAt != null
}

/**
 * How long the customer has been playing, and the one moment casino gets.
 *
 * THE SAME ENGINE ON THE OTHER HALF OF THE PRODUCT. Everything the Ambient engine did until
 * now was about sport. The obvious way to extend an attention engine into casino is the wrong
 * one -- a nudge to come back and spin, in the highest-harm vertical there is -- so the moment
 * this raises is the one a slot machine never volunteers: how long you have been sitting
 * there. It goes through AmbientEngine.onEvent like everything else and is scored, budgeted,
 * narrated, guarded and written to the ledger by exactly the same code.
 *
 * THE INTERVAL IS THE CUSTOMER'S OWN. UserState.realityCheckMinutes was a control on the
 * Responsible Gaming screen that nothing read -- the second dead control this app turned out
 * to have. It is what this counts to, and it repeats: an hour in, two hours in.
 *
 * WHAT IT NEVER CARRIES: a stake, a balance, a result, a spin count. SessionFacts has fields
 * for minutes and a name, and that is the whole vocabulary. A reality check that mentioned how
 * the session was going would be a comment on the gambling rather than on the time.
 */
class GameSessionTracker(
    private val userStateRepository: UserStateRepository,
    private val clock: MatchClock,
    /** AmbientEngine.onEvent, passed as a function so this package never imports the engine. */
    private val raise: suspend (MatchEvent) -> Unit,
) {
    private val _session = MutableStateFlow(GameSession())
    val session: StateFlow<GameSession> = _session.asStateFlow()

    private var ticker: Job? = null

    /**
     * Starts a session, or leaves a running one alone.
     *
     * Re-entering the same game does not restart the clock: a customer who backs out to the
     * lobby and taps the tile again has not had a break, and a tracker that reset there would
     * be a reality check that can be dodged by accident.
     */
    fun start(scope: CoroutineScope, gameId: String, gameName: String?) {
        if (_session.value.running && _session.value.gameId == gameId) return
        stop()
        _session.value = GameSession(gameId, gameName, clock.now(), 0)
        Log.i(TAG, "session started on " + gameId)

        ticker = scope.launch {
            var announced = 0
            clock.ticks.collect {
                val started = _session.value.startedAt ?: return@collect
                val minutes = (clock.now() - started).inWholeMinutes.toInt()
                if (minutes != _session.value.minutes) {
                    _session.value = _session.value.copy(minutes = minutes)
                }
                val every = userStateRepository.state.value.realityCheckMinutes.coerceAtLeast(1)
                // Repeats: an hour in, two hours in. `announced` rather than a modulo on the
                // minute, so a tick that arrives late cannot skip a check entirely.
                val due = minutes / every
                if (due > announced) {
                    announced = due
                    check(minutes)
                }
            }
        }
    }

    /** Ends the session. Leaving the game is the end of it; there is nothing to resume. */
    fun stop() {
        ticker?.cancel()
        ticker = null
        if (_session.value.running) Log.i(TAG, "session ended at " + _session.value.minutes + " min")
        _session.value = GameSession()
    }

    /** For the Surface Lab, so the beat can be shown without waiting an hour. */
    suspend fun checkNow() {
        check(_session.value.minutes.coerceAtLeast(userStateRepository.state.value.realityCheckMinutes))
    }

    private suspend fun check(minutes: Int) {
        val current = _session.value
        val event = MatchEvent(
            // Prefixed like the loyalty subjects, so the scorer's and the budget's
            // match-plus-type keys cannot collide with a fixture and the ledger's compliance
            // view can tell a reality check from a goal at a glance.
            matchId = SUBJECT_PREFIX + (current.gameId ?: "unknown"),
            type = MomentType.SESSION_LENGTH,
            minute = 0,
            homeTeam = "",
            awayTeam = "",
            homeScore = 0,
            awayScore = 0,
            session = SessionFacts(minutes = minutes, gameName = current.gameName),
            at = clock.now(),
        )
        Log.i(TAG, "reality check at " + minutes + " min")
        runCatching { raise(event) }.onFailure { Log.w(TAG, "reality check failed", it) }
    }

    companion object {
        const val SUBJECT_PREFIX = "game:"
        private const val TAG = "GameSession"

        /** Below this a check is noise; the RG screen's own options start at 15. */
        val MINIMUM_INTERVAL = 15.minutes
    }
}
