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
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Stubbed until step 13A brings the real age proof. */
    private val ageVerified: () -> Boolean = { true },
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
        observePlacements()
        observeTicks()
        observeProtection()
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
        scope.launch {
            userStateRepository.state.collect { user ->
                val protection = user.toProtectionStateTemp(ageVerified())
                controller.refreshShortcuts(protection)

                val slipId = _liveSlipId.value
                if (slipId == null) {
                    controller.refreshWidget(WidgetState.Protected(protection, lastRegisterCheck = clock.now()))
                    return@collect
                }

                val bet = betRepository.placedBets.value.firstOrNull { it.id == slipId } ?: return@collect
                val state = surfaceState(bet) ?: return@collect
                if (!protection.allowsLiveUpdate()) {
                    controller.endLiveUpdate(slipId, settled = false)
                    _liveSlipId.value = null
                }
                controller.updateLiveUpdate(state)
                controller.refreshWidget(WidgetState.Live(state))
            }
        }
    }

    // ---- mapping ---------------------------------------------------------------------

    private fun hasLiveLeg(bet: PlacedBet): Boolean =
        bet.legs.any { leg -> matchRepository.match(leg.matchId)?.state == MatchState.LIVE }

    /** Domain to surface. The surface type has no money field, so nothing about price crosses. */
    private fun surfaceState(bet: PlacedBet): SlipSurfaceState? {
        val user = userStateRepository.state.value
        val protection = user.toProtectionStateTemp(ageVerified())
        val active: Match? = bet.legs
            .asSequence()
            .mapNotNull { matchRepository.match(it.matchId) }
            .firstOrNull { it.state == MatchState.LIVE }

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
            settled = bet.status != BetStatus.OPEN,
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
    }
}
