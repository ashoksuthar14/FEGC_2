package eu.feg.ambient.data.repo

import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.model.League
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * In-memory store over the mock JSON. The live tick advances minutes and, roughly once every
 * [SECONDS_PER_GOAL] seconds, scores a goal and re-prices that match — Phase 2 swaps the
 * clock underneath without touching this class.
 */
class MatchRepository(
    private val source: MockDataSource,
    private val clock: MatchClock,
    scope: CoroutineScope,
    private val random: Random = Random(1867),
) {
    private val _matches = MutableStateFlow(source.matches())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    /** Outcome ids that just moved, and which way. Drives OddsButton's flash. */
    private val _oddsMoves = MutableStateFlow<Map<String, Int>>(emptyMap())
    val oddsMoves: StateFlow<Map<String, Int>> = _oddsMoves.asStateFlow()

    val sports: List<Sport> = source.sports()
    val leagues: List<League> = source.leagues()

    val liveMatches: StateFlow<List<Match>> = _matches
        .map { list -> list.filter { it.state == MatchState.LIVE } }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            _matches.value.filter { it.state == MatchState.LIVE },
        )

    private var tickCount = 0

    init {
        scope.launch {
            clock.ticks.collect { onTick() }
        }
    }

    fun match(id: String): Match? = _matches.value.firstOrNull { it.id == id }

    fun leaguesFor(sportId: String): List<League> = leagues.filter { it.sportId == sportId }

    private fun onTick() {
        tickCount++
        val scoreNow = tickCount % SECONDS_PER_GOAL == 0
        val moves = mutableMapOf<String, Int>()

        val scorerId = if (scoreNow) {
            _matches.value.filter { it.state == MatchState.LIVE }.randomOrNull(random)?.id
        } else {
            null
        }

        _matches.value = _matches.value.map { match ->
            if (match.state != MatchState.LIVE) return@map match

            // A minute of match time per SECONDS_PER_MINUTE ticks keeps the demo watchable.
            val advanced = if (tickCount % SECONDS_PER_MINUTE == 0) {
                (match.minute ?: 0) + 1
            } else {
                match.minute ?: 0
            }

            // Full time. Without this the clock runs to "177m" and nothing ever settles.
            if (advanced > FULL_TIME) {
                return@map match.copy(
                    state = MatchState.FINISHED,
                    minute = FULL_TIME,
                    period = "Kraj",
                    markets = match.markets.map { market ->
                        market.copy(outcomes = market.outcomes.map { it.copy(locked = true) })
                    },
                )
            }

            val period = periodFor(advanced, match.period)

            if (match.id != scorerId) {
                return@map match.copy(minute = advanced, period = period)
            }

            val homeScored = random.nextBoolean()
            val repriced = match.markets.map { market ->
                market.copy(
                    outcomes = market.outcomes.map { outcome ->
                        // Whoever just scored shortens; the other side drifts out.
                        val favoursHome = outcome.label == match.home.name || outcome.label == "1"
                        val shorten = favoursHome == homeScored
                        val factor = if (shorten) 0.88 else 1.14
                        val next = (outcome.odds * factor).coerceIn(1.02, 90.0)
                        moves[outcome.id + "@" + match.id] = if (shorten) -1 else 1
                        outcome.copy(odds = (next * 100).toInt() / 100.0)
                    },
                )
            }
            match.copy(
                minute = advanced,
                period = period,
                homeScore = (match.homeScore ?: 0) + if (homeScored) 1 else 0,
                awayScore = (match.awayScore ?: 0) + if (homeScored) 0 else 1,
                markets = repriced,
            )
        }
        _oddsMoves.value = moves
        promoteKickoffs()
    }

    /**
     * Keeps the Live screen populated for as long as the demo runs: every match that reaches
     * full time hands off to the next prematch fixture, which kicks off at minute 1.
     *
     * THE FIXTURE LIST IS A LOOP, NOT A QUEUE, and it had to become one. Forty-seven fixtures
     * sounds like plenty until you notice the arithmetic: the clock retires a match every
     * ninety match-minutes and promotes one to replace it, so a session long enough burns
     * through the whole file and ends with nothing live at all. That is not hypothetical --
     * an app left running an afternoon reached exactly that state, and the symptom was not an
     * empty Live tab but everything downstream of it going quiet at once: widgets with nothing
     * to show, DemoStage refusing to arm for want of three live fixtures, and the demo bubble
     * answering "nothing live to report". On mock data, an empty surface is always a bug.
     *
     * So when there is nothing left to promote, the oldest finished match is put back to
     * PREMATCH with its score cleared and its markets unlocked, and the ordinary promotion
     * below picks it up on the next tick. The fixtures recycle rather than run out.
     */
    private fun promoteKickoffs() {
        val live = _matches.value.count { it.state == MatchState.LIVE }
        if (live >= TARGET_LIVE) return

        if (_matches.value.none { it.state == MatchState.PREMATCH }) {
            recycleFinished()
        }

        val next = _matches.value
            .filter { it.state == MatchState.PREMATCH }
            .minByOrNull { it.kickoff }
            ?: return

        _matches.value = _matches.value.map { match ->
            if (match.id != next.id) {
                match
            } else {
                match.copy(
                    state = MatchState.LIVE,
                    minute = 1,
                    period = "1. poluvrijeme",
                    homeScore = 0,
                    awayScore = 0,
                )
            }
        }
    }

    /**
     * Finished matches go back on the fixture list.
     *
     * A batch rather than one at a time, because promoteKickoffs runs once per tick and
     * recycling singly would drip the live screen back up over a minute of real time from an
     * empty start. Everything a played match accumulated is cleared: score, minute, period,
     * and the market locks that full time applied, or the fixture would return as an
     * unbettable 3-1.
     */
    private fun recycleFinished() {
        val finished = _matches.value.filter { it.state == MatchState.FINISHED }
        if (finished.isEmpty()) return
        val recycled = finished.take(TARGET_LIVE).map { it.id }.toSet()

        _matches.value = _matches.value.map { match ->
            if (match.id !in recycled) {
                match
            } else {
                match.copy(
                    state = MatchState.PREMATCH,
                    minute = null,
                    period = null,
                    homeScore = null,
                    awayScore = null,
                    markets = match.markets.map { market ->
                        market.copy(outcomes = market.outcomes.map { it.copy(locked = false) })
                    },
                )
            }
        }
    }

    private fun periodFor(minute: Int, current: String?): String = when {
        current == "Pauza" && minute < HALF_TIME + 1 -> "Pauza"
        minute <= HALF_TIME -> "1. poluvrijeme"
        else -> "2. poluvrijeme"
    }

    private companion object {
        /**
         * Ticks of the clock per minute of match time.
         *
         * Eight, not three. At three a match ran its ninety minutes in four and a half, so a
         * fixture was on the pitch for less time than it takes to talk about it -- the widget
         * settled mid-sentence and the recognisable clubs were gone before anyone looked. At
         * eight a match lasts twelve minutes, which outlasts a demo, and the minute still
         * visibly moves.
         */
        const val SECONDS_PER_MINUTE = 8

        /** Ticks between goals, across all live matches. */
        const val SECONDS_PER_GOAL = 45

        const val HALF_TIME = 45

        /** Matches stop here and settle; the offer locks with them. */
        const val FULL_TIME = 90

        /** How many matches the live screen keeps on the pitch at once. */
        const val TARGET_LIVE = 12
    }
}
