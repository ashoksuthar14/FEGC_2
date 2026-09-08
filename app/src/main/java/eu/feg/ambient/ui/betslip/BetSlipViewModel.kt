package eu.feg.ambient.ui.betslip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.BetSlip
import eu.feg.ambient.data.model.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SlipRow(
    val selection: Selection,
    val fixture: String,
    val market: String,
    val pick: String,
)

data class BetSlipUiState(
    val slip: BetSlip = BetSlip(id = "slip-1"),
    val rows: List<SlipRow> = emptyList(),
    val activeTab: Int = 1,
    val placedId: String? = null,
)

class BetSlipViewModel(private val container: AppContainer) : ViewModel() {

    private val activeTab = MutableStateFlow(1)
    private val placedId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BetSlipUiState> = combine(
        container.betRepository.slip,
        container.matchRepository.matches,
        activeTab,
        placedId,
    ) { slip, matches, tab, placed ->
        BetSlipUiState(
            slip = slip,
            rows = slip.selections.mapNotNull { selection ->
                val match = matches.firstOrNull { it.id == selection.matchId } ?: return@mapNotNull null
                val market = match.markets.firstOrNull { it.id == selection.marketId }
                val outcome = market?.outcomes?.firstOrNull { it.id == selection.outcomeId }
                SlipRow(
                    selection = selection,
                    fixture = match.home.name + " - " + match.away.name,
                    market = market?.name.orEmpty(),
                    pick = outcome?.label.orEmpty(),
                )
            },
            activeTab = tab,
            placedId = placed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BetSlipUiState())

    fun selectTab(tab: Int) {
        activeTab.value = tab
    }

    fun setStake(stake: Double) = container.betRepository.setStake(stake)

    fun remove(selection: Selection) = container.betRepository.remove(selection)

    fun clear() = container.betRepository.clear()

    /** Emits on BetRepository.betPlaced — Phase 2 seam 1. Nothing listens yet, by design. */
    fun place() {
        viewModelScope.launch {
            placedId.value = container.betRepository.place()
        }
    }

    fun dismissSuccess() {
        placedId.value = null
    }
}
