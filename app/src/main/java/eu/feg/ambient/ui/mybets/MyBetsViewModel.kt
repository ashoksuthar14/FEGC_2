package eu.feg.ambient.ui.mybets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.PlacedBet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BetRow(
    val bet: PlacedBet,
    /** "2/3 · 61'" when any leg is still live, else null. */
    val liveProgress: String?,
)

data class MyBetsUiState(
    val showSettled: Boolean = false,
    val openBets: List<BetRow> = emptyList(),
    val settledBets: List<BetRow> = emptyList(),
)

class MyBetsViewModel(container: AppContainer) : ViewModel() {

    private val settledTab = MutableStateFlow(false)

    val uiState: StateFlow<MyBetsUiState> = combine(
        container.betRepository.placedBets,
        container.matchRepository.matches,
        settledTab,
    ) { bets, matches, settled ->
        val rows = bets.map { bet ->
            val liveLegs = bet.legs.filter { leg ->
                matches.firstOrNull { it.id == leg.matchId }?.state == MatchState.LIVE
            }
            // Minute comes from the ticking clock, so these rows move with the Live screen.
            val minute = liveLegs.firstNotNullOfOrNull { leg ->
                matches.firstOrNull { it.id == leg.matchId }?.minute
            }
            BetRow(
                bet = bet,
                liveProgress = if (liveLegs.isEmpty() || minute == null) {
                    null
                } else {
                    bet.legs.count { it.status != LegStatus.PENDING }.toString() +
                        "/" + bet.legs.size + " · " + minute + "'"
                },
            )
        }
        MyBetsUiState(
            showSettled = settled,
            openBets = rows.filter { it.bet.status == BetStatus.OPEN },
            settledBets = rows.filter { it.bet.status != BetStatus.OPEN },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyBetsUiState())

    fun showSettled(value: Boolean) {
        settledTab.value = value
    }
}
