package eu.feg.ambient.data.repo

import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.model.BetSlip
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Leg
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.model.Selection
import eu.feg.ambient.data.model.SlipType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bet slip and the bets that came out of it.
 *
 * [betPlaced] is Phase 2 seam 1 (PRD section 11): the Ambient engine subscribes here to
 * create the Live Update. Nothing collects it in Phase 1, and that is correct.
 */
class BetRepository(private val clock: MatchClock) {

    private val _slip = MutableStateFlow(BetSlip(id = "slip-1"))
    val slip: StateFlow<BetSlip> = _slip.asStateFlow()

    private val _placedBets = MutableStateFlow<List<PlacedBet>>(emptyList())
    val placedBets: StateFlow<List<PlacedBet>> = _placedBets.asStateFlow()

    private val _betPlaced = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val betPlaced: SharedFlow<String> = _betPlaced.asSharedFlow()

    /** Selection labels, kept beside the slip so My bets can describe each leg in words. */
    private val descriptions = mutableMapOf<String, String>()

    fun isSelected(outcomeId: String, matchId: String): Boolean =
        _slip.value.selections.any { it.outcomeId == outcomeId && it.matchId == matchId }

    /** Tapping the same price twice removes it, which is how the web slip behaves. */
    fun toggle(selection: Selection, description: String) {
        val current = _slip.value
        val existing = current.selections.firstOrNull {
            it.matchId == selection.matchId && it.outcomeId == selection.outcomeId
        }
        _slip.value = if (existing != null) {
            current.copy(selections = current.selections - existing)
        } else {
            // One pick per match, as on psk.hr — a new pick replaces the old one.
            val withoutMatch = current.selections.filterNot { it.matchId == selection.matchId }
            descriptions[key(selection)] = description
            current.copy(selections = withoutMatch + selection)
        }
    }

    fun remove(selection: Selection) {
        _slip.value = _slip.value.copy(selections = _slip.value.selections - selection)
    }

    fun clear() {
        _slip.value = _slip.value.copy(selections = emptyList(), stake = 0.0)
    }

    fun setStake(stake: Double) {
        _slip.value = _slip.value.copy(stake = stake)
    }

    fun setType(type: SlipType) {
        _slip.value = _slip.value.copy(type = type)
    }

    /** Places the current slip and emits its id. Returns null when there is nothing to place. */
    suspend fun place(): String? {
        val slip = _slip.value
        if (slip.selections.isEmpty() || slip.stake <= 0.0) return null

        val id = "bet-" + (_placedBets.value.size + 1)
        val bet = PlacedBet(
            id = id,
            legs = slip.selections.map {
                Leg(
                    matchId = it.matchId,
                    description = descriptions[key(it)] ?: "Selection",
                    odds = it.oddsAtPick,
                    status = LegStatus.PENDING,
                )
            },
            stake = slip.stake,
            totalOdds = slip.totalOdds,
            placedAt = clock.now(),
            status = BetStatus.OPEN,
        )
        _placedBets.value = _placedBets.value + bet
        _slip.value = BetSlip(id = "slip-1")
        _betPlaced.emit(id)
        return id
    }

    /** Live legs settle as their match finishes; My bets shows the progress meanwhile. */
    fun updateLegStatus(betId: String, matchId: String, status: LegStatus) {
        _placedBets.value = _placedBets.value.map { bet ->
            if (bet.id != betId) return@map bet
            bet.copy(legs = bet.legs.map { if (it.matchId == matchId) it.copy(status = status) else it })
        }
    }

    private fun key(s: Selection) = s.matchId + "/" + s.outcomeId
}
