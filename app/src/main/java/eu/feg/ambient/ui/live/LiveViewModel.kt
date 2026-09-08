package eu.feg.ambient.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.League
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.Selection
import eu.feg.ambient.data.model.Sport
import eu.feg.ambient.ui.components.TimeTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import eu.feg.ambient.data.model.PlacedBet
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant

data class LiveLeagueGroup(val league: League, val matches: List<Match>)

data class LiveSportGroup(
    val sport: Sport,
    val leagues: List<LiveLeagueGroup>,
    val liveCount: Int,
)

data class LiveUiState(
    val timeTab: TimeTab = TimeTab.LIVE,
    val sportGroups: List<LiveSportGroup> = emptyList(),
    val selectedOutcomeIds: Set<String> = emptySet(),
    val oddsMoves: Map<String, Int> = emptyMap(),
    val now: Instant = Instant.fromEpochSeconds(0),
    val openBets: List<PlacedBet> = emptyList(),
)

/**
 * Minutes and scores come from [AppContainer.clock] via the repository, never from static
 * text — Phase 2 replaces that clock with the scripted simulator (PRD section 5.2).
 */
class LiveViewModel(private val container: AppContainer) : ViewModel() {

    private val timeTab = MutableStateFlow(TimeTab.LIVE)
    private val collapsedSports = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedLeagues = MutableStateFlow<Set<String>>(emptySet())

    val collapsedSportIds: StateFlow<Set<String>> = collapsedSports
    val collapsedLeagueIds: StateFlow<Set<String>> = collapsedLeagues

    val uiState: StateFlow<LiveUiState> = combine(
        container.matchRepository.liveMatches,
        container.matchRepository.oddsMoves,
        container.betRepository.slip,
        timeTab,
        container.betRepository.placedBets,
    ) { live, moves, slip, tab, openBets ->
        val leagues = container.matchRepository.leagues.associateBy { it.id }

        val groups = container.matchRepository.sports.mapNotNull { sport ->
            val leagueGroups = leagues.values
                .filter { it.sportId == sport.id }
                .mapNotNull { league ->
                    val inLeague = live.filter { it.leagueId == league.id }
                    if (inLeague.isEmpty()) null else LiveLeagueGroup(league, inLeague)
                }
            if (leagueGroups.isEmpty() && sport.liveCount == 0) {
                null
            } else {
                // Counts come from the fixture file so the accordion matches the screenshots
                // even for sports we have no live rows for.
                LiveSportGroup(sport, leagueGroups, sport.liveCount)
            }
        }

        LiveUiState(
            openBets = openBets,
            timeTab = tab,
            sportGroups = groups,
            selectedOutcomeIds = slip.selections.map { it.matchId + "/" + it.outcomeId }.toSet(),
            oddsMoves = moves,
            now = container.clock.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    fun selectTimeTab(tab: TimeTab) {
        timeTab.value = tab
    }

    fun toggleSport(sportId: String) {
        collapsedSports.value = collapsedSports.value.toggle(sportId)
    }

    fun toggleLeague(leagueId: String) {
        collapsedLeagues.value = collapsedLeagues.value.toggle(leagueId)
    }

    fun toggleSelection(match: Match, marketId: String, outcomeId: String) {
        val market = match.markets.firstOrNull { it.id == marketId } ?: return
        val outcome = market.outcomes.firstOrNull { it.id == outcomeId } ?: return
        if (outcome.locked) return
        container.betRepository.toggle(
            Selection(match.id, marketId, outcomeId, outcome.odds),
            match.home.name + " - " + match.away.name + " · " + market.name + " · " + outcome.label,
        )
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id
}
