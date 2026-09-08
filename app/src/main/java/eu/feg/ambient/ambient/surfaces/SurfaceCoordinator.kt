package eu.feg.ambient.ambient.surfaces

import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import eu.feg.ambient.data.model.LegStatus as DomainLegStatus

/**
 * Step 14E: makes the surfaces appear from ordinary app use rather than only from the Lab.
 *
 * This is the only place that knows both the app's repositories and the surfaces. The Surface
 * Lab still drives [SurfaceController] directly for testing; step 13B's Moment Engine will sit
 * here instead, deciding *whether* to narrate rather than narrating everything that moves.
 */
class SurfaceCoordinator(
    private val scope: CoroutineScope,
    private val controller: SurfaceController,
    private val betRepository: BetRepository,
    private val matchRepository: MatchRepository,
    private val userStateRepository: UserStateRepository,
    private val narrator: Narrator,
    private val clock: MatchClock,
    /**
     * Step 13's real evaluator: register check plus age assurance, not a derivation from
     * UserState. Nothing here may decide protection for itself any more.
     */
    private val protection: suspend () -> ProtectionState,
    /** Step 13: every event goes through the engine, which decides whether it surfaces at all. */
    private val onMatchEvent: suspend (MatchEvent) -> Unit,
    /** N6: the customer's club, already combined with protection by the container. */
    private val clubTheme: StateFlow<ClubTheme>,
    /**
     * The evaluator's own state.
     *
     * The coordinator used to watch UserState for protection changes, which quietly meant it
     * only saw the half of protection the app itself writes. A block that arrives from the
     * exclusion register — the case the whole compliance argument rests on, and the one the
     * demo performs on stage — changes no UserState field, so it reached no surface. Both
     * inputs are watched now.
     */
    private val protectionState: StateFlow<ProtectionState>,
) {

    /**
     * Set when a slip has just been placed on a live match and we would like to track it.
     * The UI observes this and asks for POST_NOTIFICATIONS with the "track this slip"
     * rationale — never at launch, because a denied notification permission is close to
     * permanent and opt-in retention is being judged.
     */
    private val _permissionWanted = MutableStateFlow<String?>(null)
    val permissionWanted: StateFlow<String?> = _permissionWanted.asStateFlow()

    /** Slip currently carrying a Live Update, so My Bets can say so honestly. */
    val liveSlipId: StateFlow<String?>
        get() = _liveSlipId.asStateFlow()
    private val _liveSlipId = MutableStateFlow<String?>(null)

    private var lastPushedAt: Long = 0
    private var lastSignature: String? = null

    fun start() {
        resumeOpenSlip()
        observePlacements()
        observeTicks()
        observeProtection()
        observeMatchEvents()
        observeClubTheme()
    }

    /**
     * A club change repaints every surface at once, on the same path a protection change
     * takes — not on the next tick.
     *
     * This is a demo beat: the club is picked on the phone and the lock screen and the home
     * screen are expected to follow while the audience is still looking at them. Twenty
     * seconds of throttle later would read as the feature not working.
     */
    private fun observeClubTheme() {
        scope.launch {
            // drop(1): the first value is the current theme, and repainting on start-up would
            // fight resumeOpenSlip for the same surfaces.
            clubTheme.drop(1).collect {
                val protection = protection()
                controller.refreshShortcuts(protection)

                val slipId = _liveSlipId.value
                if (slipId == null) {
                    // Repaint what is on the home screen; do not decide again what ought to
                    // be there. Deciding again is how picking a club replaced a live card
                    // with an idle one — the coordinator does not own every surface that can
                    // be showing, and a restyle must never be able to take content away.
                    controller.refreshWidget(repaintTarget(protection))
                    return@collect
                }
                val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return@collect
                val state = surfaceState(bet) ?: return@collect
                // Bypasses the throttle deliberately: nothing about the slip changed, but
                // everything about how it looks did.
                lastSignature = null
                controller.updateLiveUpdate(state)
                controller.refreshWidget(WidgetState.Live(state))
            }
        }
    }

    /**
     * Picks an open slip back up after a restart.
     *
     * The live slip id lives in memory, so without this a process death — or simply swiping
     * the app away — silently ended a Live Update the user could still see a match for. The
     * bet is the durable thing; the card should follow it rather than the process.
     */
    private fun resumeOpenSlip() {
        scope.launch {
            val open = betRepository.placedBets.value.firstOrNull {
                it.status == BetStatus.OPEN && hasLiveLeg(it)
            } ?: return@launch

            val state = surfaceState(open) ?: return@launch
            if (!state.protection.allowsLiveUpdate()) return@launch

            _liveSlipId.value = open.id
            controller.startLiveUpdate(state)
            controller.refreshWidget(WidgetState.Live(state))
        }
    }

    /**
     * Every scoring change in a live match becomes a MatchEvent for the engine, which decides
     * whether it is worth saying anything at all. This is what replaces a hand on the Surface
     * Lab buttons: the coordinator reports what happened, the engine chooses what to do.
     *
     * Only changes are emitted. A tick where nothing moved is not an event.
     */
    private fun observeMatchEvents() {
        scope.launch {
            val lastScores = mutableMapOf<String, String>()
            clock.ticks.collect {
                matchRepository.matches.value
                    .filter { it.state == MatchState.LIVE }
                    .forEach { match ->
                        val signature = match.homeScore.toString() + ":" + match.awayScore
                        if (lastScores[match.id] == signature) return@forEach
                        val first = lastScores.put(match.id, signature) == null
                        // The first sighting of a match is its current state, not a goal.
                        if (first) return@forEach

                        onMatchEvent(
                            MatchEvent(
                                matchId = match.id,
                                type = MomentType.GOAL_ON_SLIP,
                                minute = match.minute ?: 0,
                                homeTeam = match.home.name,
                                awayTeam = match.away.name,
                                homeScore = match.homeScore ?: 0,
                                awayScore = match.awayScore ?: 0,
                                period = match.period,
                                at = clock.now(),
                            ),
                        )
                    }
            }
        }
    }

    /** A live bet is the trigger point; everything else follows from it. */
    private fun observePlacements() {
        scope.launch {
            betRepository.betPlaced.collect { slipId ->
                val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return@collect
                val state = surfaceState(bet) ?: return@collect

                // The widget needs no permission, so it is refreshed either way: if the user
                // declines notifications it stays our path to them.
                controller.refreshWidget(WidgetState.Live(state))

                if (!state.protection.allowsLiveUpdate()) return@collect
                if (!hasLiveLeg(bet)) return@collect

                _permissionWanted.value = slipId
            }
        }
    }

    /** Called by the UI once the permission dialog has been answered. */
    fun onNotificationPermissionResult(granted: Boolean) {
        val slipId = _permissionWanted.value
        _permissionWanted.value = null
        if (!granted || slipId == null) return

        scope.launch {
            val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return@launch
            val state = surfaceState(bet) ?: return@launch
            controller.startLiveUpdate(state)
            _liveSlipId.value = slipId
        }
    }

    /**
     * The match clock drives the card. Throttled to one push every [THROTTLE_MS] unless the
     * score or a leg status actually changed — a lock screen redrawn every second is noise,
     * and noise is the thing this product exists to remove.
     */
    private fun observeTicks() {
        scope.launch {
            clock.ticks.collect {
                val slipId = _liveSlipId.value ?: return@collect
                val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return@collect
                val state = surfaceState(bet) ?: return@collect

                if (bet.status != BetStatus.OPEN || state.settled) {
                    settle(bet, state)
                    return@collect
                }

                val signature = state.homeScore.toString() + ":" + state.awayScore +
                    ":" + bet.legs.joinToString(",") { it.status.name }
                val now = System.currentTimeMillis()
                val changed = signature != lastSignature
                if (!changed && now - lastPushedAt < THROTTLE_MS) return@collect

                lastSignature = signature
                lastPushedAt = now
                val narrated = narrate(state, if (changed) MomentType.GOAL_ON_SLIP else MomentType.MINUTES_REMAINING)
                controller.updateLiveUpdate(state.copy(narrated = narrated))
                controller.refreshWidget(WidgetState.Live(state.copy(narrated = narrated)))
            }
        }
    }

    private suspend fun settle(bet: PlacedBet, state: SlipSurfaceState) {
        val settled = state.copy(settled = true, narrated = narrate(state, MomentType.SLIP_SETTLED))
        controller.endLiveUpdate(bet.id, settled = true)
        controller.refreshWidget(WidgetState.Settled(settled))
        _liveSlipId.value = null

        // One attempt, and the budget's answer is respected rather than retried.
        settled.narrated?.let {
            controller.postAlert(it.headline, it.detail, DEEP_LINK_MY_BETS)
        }
    }

    /**
     * A protection change refreshes every surface at once rather than on the next tick. This
     * is the Calm Mode moment in the demo and a second of lag would undercut the whole claim.
     */
    private fun observeProtection() {
        // Two sources, one handler: the register can move protection without UserState
        // moving, and UserState (panic, limits) can move it without the register moving.
        scope.launch { protectionState.collect { applyProtection() } }
        scope.launch { userStateRepository.state.collect { applyProtection() } }
    }

    private suspend fun applyProtection() {
        val protection = protection()
        controller.refreshShortcuts(protection)

        // UNCONDITIONAL, and before anything else.
        //
        // The renderer already refuses to POST under UNVERIFIED or BLOCKED, but nothing was
        // taking down a card that had already been posted, and this used to run only when
        // the coordinator happened to know which slip was on screen. A card put up by the
        // Surface Lab, by a demo broadcast, or by any future caller therefore survived the
        // account being excluded — an excluded customer left looking at a live betting card
        // on their lock screen. The cancel is tied to the protection state, not to our
        // bookkeeping.
        if (!protection.allowsLiveUpdate()) {
            controller.endLiveUpdate(_liveSlipId.value ?: ANY_SLIP, settled = false)
            _liveSlipId.value = null
        }

        val slipId = _liveSlipId.value
        if (slipId == null) {
            controller.refreshWidget(repaintTarget(protection))
            return
        }

        val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return
        val state = surfaceState(bet) ?: return
        controller.updateLiveUpdate(state)
        controller.refreshWidget(WidgetState.Live(state))
    }

    /**
     * The state to redraw when only the styling changed.
     *
     * Whatever the widget already holds, unless that is a card whose content depends on the
     * club — Idle names a fixture, so a club change has to recompute it rather than repaint
     * the old club's match in the new club's colours.
     */
    private fun repaintTarget(protection: ProtectionState): WidgetState {
        // Protection outranks everything, including "leave what is there alone". A card that
        // was live a second ago must not be repainted as live once the account is blocked.
        if (protection != ProtectionState.NORMAL && protection != ProtectionState.CALM) {
            return quietWidgetState(protection)
        }
        val current = controller.currentWidgetState()
        return when (current) {
            null, is WidgetState.Idle, is WidgetState.Protected -> quietWidgetState(protection)
            else -> current
        }
    }

    /**
     * What the widget shows with no slip in flight.
     *
     * N6 gives this state something to say on a day with nothing running, which was the
     * widget's weakest moment: a followed club's next fixture instead of a card that admits
     * it has nothing. Under protection it goes back to the neutral operator card — the club
     * is cosmetic, and cosmetics do not outrank a protection message.
     */
    private fun quietWidgetState(protection: ProtectionState): WidgetState {
        if (protection != ProtectionState.NORMAL && protection != ProtectionState.CALM) {
            return WidgetState.Protected(protection, lastRegisterCheck = clock.now())
        }
        val fixture = clubFixture()
            ?: return WidgetState.Protected(protection, lastRegisterCheck = clock.now())
        return WidgetState.Idle(nextFixture = fixture.first, kickoff = fixture.second)
    }

    /** The followed club's next match, as a label and a kickoff time. */
    private fun clubFixture(): Pair<String, kotlinx.datetime.Instant?>? {
        val theme = clubTheme.value
        if (theme.clubId.isEmpty()) return null
        val match = matchRepository.matches.value
            .filter {
                it.home.name.equals(theme.name, true) || it.away.name.equals(theme.name, true)
            }
            .minByOrNull { if (it.state == MatchState.LIVE) 0 else 1 }
            ?: return null
        return (match.home.name + " – " + match.away.name) to match.kickoff
    }

    // ---- mapping ---------------------------------------------------------------------

    private fun hasLiveLeg(bet: PlacedBet): Boolean =
        bet.legs.any { leg -> matchRepository.match(leg.matchId)?.state == MatchState.LIVE }

    /** Domain to surface. The surface type has no money field, so nothing about price crosses. */
    private suspend fun surfaceState(bet: PlacedBet): SlipSurfaceState? {
        val protection = protection()
        // Prefer a live match, but fall back to any match on the slip. Insisting on LIVE
        // meant that the moment a match reached full time every field went null and the
        // surfaces were rewritten with an empty card — the score and teams are still the
        // truth after the whistle, and a settled slip should show them, not nothing.
        val matches = bet.legs.mapNotNull { matchRepository.match(it.matchId) }
        val active: Match? = matches.firstOrNull { it.state == MatchState.LIVE }
            ?: matches.firstOrNull()

        // No leg still in play means the slip is done, whatever the bet's stored status says.
        val anyLive = matches.any { it.state == MatchState.LIVE }
        val league = active?.let { m -> matchRepository.leagues.firstOrNull { it.id == m.leagueId } }

        return SlipSurfaceState(
            slipId = bet.id,
            protection = protection,
            legs = bet.legs.map { leg ->
                val match = matchRepository.match(leg.matchId)
                LegState(
                    description = leg.description,
                    match = match?.let { it.home.name + " – " + it.away.name } ?: leg.description,
                    status = when (leg.status) {
                        DomainLegStatus.WON -> LegStatus.WON
                        DomainLegStatus.LOST -> LegStatus.LOST
                        DomainLegStatus.VOID -> LegStatus.VOID
                        DomainLegStatus.PENDING -> LegStatus.PENDING
                    },
                )
            },
            activeMatch = active?.let { it.home.name + " – " + it.away.name },
            homeTeam = active?.home?.name,
            awayTeam = active?.away?.name,
            homeScore = active?.homeScore,
            awayScore = active?.awayScore,
            minute = active?.minute,
            period = active?.period,
            minutesRemaining = active?.minute?.let { (90 - it).coerceAtLeast(0) },
            competition = league?.name,
            region = league?.country,
            settled = bet.status != BetStatus.OPEN || !anyLive,
        )
    }

    private suspend fun narrate(state: SlipSurfaceState, type: MomentType) = runCatching {
        narrator.narrate(
            MomentFacts(
                type = type,
                homeTeam = state.homeTeam,
                awayTeam = state.awayTeam,
                homeScore = state.homeScore,
                awayScore = state.awayScore,
                minute = state.minute,
                period = state.period,
                legsTotal = state.legsTotal,
                legsWon = state.legsWon,
                legsLost = state.legsLost,
                myLegDescription = state.legs.firstOrNull { it.status == LegStatus.PENDING }?.description,
                minutesRemaining = state.minutesRemaining,
            ),
            Tone.PLAIN,
            NarratorLanguage.EN,
        )
    }.getOrNull()

    private companion object {
        /** One push per 20s unless something actually changed. */
        const val THROTTLE_MS = 20_000L
        const val DEEP_LINK_MY_BETS = "mybets"

        /**
         * Stands in for "whatever is showing" when protection has to take a card down and we
         * do not know whose it was. An unsettled end cancels by notification id, so the id
         * only has to be non-null.
         */
        const val ANY_SLIP = "*"
    }
}
