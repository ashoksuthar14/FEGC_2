package eu.feg.ambient.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.League
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.Selection
import eu.feg.ambient.data.model.Sport
import eu.feg.ambient.ui.components.TimeTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class LeagueGroup(
    val league: League,
    val matches: List<Match>,
)

data class HomeUiState(
    val loading: Boolean = true,
    val timeTab: TimeTab = TimeTab.TODAY,
    val sportFilter: String = "football",
    val countryFilter: String? = null,
    val sports: List<Sport> = emptyList(),
    val groups: List<LeagueGroup> = emptyList(),
    val startingSoon: List<Match> = emptyList(),
    val selectedOutcomeIds: Set<String> = emptySet(),
    val slipCount: Int = 0,
    val now: Instant = Instant.fromEpochSeconds(0),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    /** N6. Read-only here; the container owns the combination with protection. */
    val myClubTheme: StateFlow<ClubTheme> = container.myClubTheme

    fun setMyClub(clubId: String?) = container.userStateRepository.setMyClub(clubId)

    private val timeTab = MutableStateFlow(TimeTab.TODAY)
    private val sportFilter = MutableStateFlow("football")
    private val countryFilter = MutableStateFlow<String?>(null)
    private val loading = MutableStateFlow(true)
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<HomeUiState> = combine(
        container.matchRepository.matches,
        container.betRepository.slip,
        timeTab,
        sportFilter,
        combine(countryFilter, loading) { country, isLoading -> country to isLoading },
    ) { matches, slip, tab, sport, countryAndLoading ->
        val (country, isLoading) = countryAndLoading
        val now = container.clock.now()
        val leagues = container.matchRepository.leaguesFor(sport)
        val leagueIds = leagues.map { it.id }.toSet()

        val visible = matches
            .filter { it.leagueId in leagueIds }
            .filter { matchesTab(it, tab, now) }
            .filter { country == null || leagues.first { l -> l.id == it.leagueId }.country == country }

        HomeUiState(
            loading = isLoading,
            timeTab = tab,
            sportFilter = sport,
            countryFilter = country,
            sports = container.matchRepository.sports,
            groups = leagues
                .mapNotNull { league ->
                    val inLeague = visible.filter { it.leagueId == league.id }
                    if (inLeague.isEmpty()) null else LeagueGroup(league, inLeague)
                }
                .sortedByDescending { it.matches.size },
            startingSoon = matches
                .filter { it.state == MatchState.PREMATCH }
                .sortedBy { it.kickoff }
                .take(6),
            selectedOutcomeIds = slip.selections.map { it.matchId + "/" + it.outcomeId }.toSet(),
            slipCount = slip.selections.size,
            now = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val collapsedLeagues: StateFlow<Set<String>> = collapsed

    init {
        // Skeleton shimmer for 400ms so the app reads as if it were fetching (PRD 5.1).
        viewModelScope.launch {
            delay(400)
            loading.value = false
        }
    }

    fun selectTimeTab(tab: TimeTab) {
        timeTab.value = tab
    }

    fun selectSport(sportId: String) {
        sportFilter.value = sportId
    }

    fun selectCountry(country: String?) {
        countryFilter.value = if (countryFilter.value == country) null else country
    }

    fun toggleLeague(leagueId: String) {
        collapsed.value = if (leagueId in collapsed.value) {
            collapsed.value - leagueId
        } else {
            collapsed.value + leagueId
        }
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

    private fun matchesTab(match: Match, tab: TimeTab, now: Instant): Boolean {
        val hoursAway = (match.kickoff - now).inWholeMinutes / 60.0
        return when (tab) {
            TimeTab.LIVE -> match.state == MatchState.LIVE
            TimeTab.TODAY -> match.state == MatchState.LIVE || hoursAway in 0.0..24.0
            TimeTab.ONE_HOUR -> match.state == MatchState.PREMATCH && hoursAway in 0.0..1.0
            TimeTab.THREE_HOURS -> match.state == MatchState.PREMATCH && hoursAway in 0.0..3.0
            TimeTab.TOMORROW -> match.state == MatchState.PREMATCH && hoursAway in 24.0..48.0
            TimeTab.ALL -> true
        }
    }
}
