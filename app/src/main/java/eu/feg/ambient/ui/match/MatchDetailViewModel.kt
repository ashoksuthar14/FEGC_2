package eu.feg.ambient.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.Selection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant

data class MatchDetailUiState(
    val match: Match? = null,
    val leagueName: String? = null,
    val selectedOutcomeIds: Set<String> = emptySet(),
    val slipCount: Int = 0,
    val now: Instant = Instant.fromEpochSeconds(0),
)

class MatchDetailViewModel(
    private val container: AppContainer,
    private val matchId: String,
) : ViewModel() {

    val uiState: StateFlow<MatchDetailUiState> = combine(
        container.matchRepository.matches,
        container.betRepository.slip,
    ) { matches, slip ->
        val match = matches.firstOrNull { it.id == matchId }
        MatchDetailUiState(
            match = match,
            leagueName = container.matchRepository.leagues
                .firstOrNull { it.id == match?.leagueId }?.name,
            selectedOutcomeIds = slip.selections.map { it.matchId + "/" + it.outcomeId }.toSet(),
            slipCount = slip.selections.size,
            now = container.clock.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchDetailUiState())

    fun toggleSelection(marketId: String, outcomeId: String) {
        val match = uiState.value.match ?: return
        val market = match.markets.firstOrNull { it.id == marketId } ?: return
        val outcome = market.outcomes.firstOrNull { it.id == outcomeId } ?: return
        if (outcome.locked) return
        container.betRepository.toggle(
            Selection(match.id, marketId, outcomeId, outcome.odds),
            match.home.name + " - " + match.away.name + " · " + market.name + " · " + outcome.label,
        )
    }
}
