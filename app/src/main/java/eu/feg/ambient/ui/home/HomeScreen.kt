package eu.feg.ambient.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.ui.components.LeagueSection
import eu.feg.ambient.ui.components.MatchRow
import eu.feg.ambient.ui.components.MyClubRow
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.components.SectionHeader
import eu.feg.ambient.ui.components.ShimmerRow
import eu.feg.ambient.ui.components.TimeTabs
import eu.feg.ambient.ui.mapping.primaryMarket
import eu.feg.ambient.ui.mapping.slipLegsFor
import eu.feg.ambient.ui.mapping.toRowUi
import eu.feg.ambient.ui.theme.LocalPskColors

/**
 * PRD section 5.1 — the twelve blocks, top to bottom, in a single LazyColumn with the
 * carousels as LazyRow items.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    promos: List<Promo>,
    arenaTips: List<eu.feg.ambient.data.model.ArenaTip>,
    modifier: Modifier = Modifier,
    onMatchClick: (String) -> Unit = {},
    onCopyTip: (String) -> Unit = {},
    onOpenResponsibleGaming: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsedLeagues.collectAsStateWithLifecycle()
    val club by viewModel.myClubTheme.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1 — promo carousel
        item(key = "promos") { PromoCarousel(promos) }

        // 2 — quick links
        item(key = "quicklinks") { QuickLinkRow() }

        // N6 — the customer's club. High enough to be found, small enough that the app still
        // reads as the operator's rather than as a fan page.
        item(key = "myclub") {
            MyClubRow(selected = club, onSelect = viewModel::setMyClub)
        }

        // 3 — time tabs
        item(key = "timetabs") {
            TimeTabs(selected = state.timeTab, onSelect = viewModel::selectTimeTab)
        }

        // 4 — starts in 3 hours
        item(key = "soon-header") { SectionHeader("Starts in 3 hours") }
        item(key = "soon") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.startingSoon, key = { it.id }) { match ->
                    StartingSoonCard(match, onClick = { onMatchClick(match.id) })
                }
            }
        }

        // 5 — country chips
        item(key = "countries") {
            val countries = state.groups.map { it.league.country }.distinct()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(countries, key = { it }) { country ->
                    PskChip(
                        label = country,
                        selected = state.countryFilter == country,
                        onClick = { viewModel.selectCountry(country) },
                    )
                }
            }
        }

        // 6 — player props
        item(key = "props-header") { SectionHeader("Player props", onSeeAll = {}) }
        item(key = "props") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PLAYER_PROPS, key = { it.player }) { card ->
                    PlayerPropsCard(card.player, card.fixture, card.kickoff, card.props)
                }
            }
        }

        // 7 — sport filter chips
        item(key = "sports") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sports, key = { it.id }) { sport ->
                    PskChip(
                        label = sport.name,
                        selected = state.sportFilter == sport.id,
                        count = sport.totalCount,
                        // The one in-app place the accent lands, besides the club strip.
                        selectedFill = club.primary,
                        selectedContent = club.onPrimary,
                        onClick = { viewModel.selectSport(sport.id) },
                    )
                }
            }
        }

        // 8 — league sections (or the loading shimmer)
        if (state.loading) {
            items(4, key = { "shimmer-" + it }) { ShimmerRow() }
        } else {
            items(state.groups, key = { it.league.id }) { group ->
                LeagueSection(
                    name = group.league.name,
                    count = group.matches.size,
                    flag = group.league.flag,
                    expanded = group.league.id !in collapsed,
                    onToggle = { viewModel.toggleLeague(group.league.id) },
                ) {
                    group.matches.forEach { match ->
                        MatchRow(
                            match = match.toRowUi(
                                now = state.now,
                                selectedOutcomeIds = state.selectedOutcomeIds,
                                market = match.primaryMarket(),
                                legsWon = state.openBets.slipLegsFor(match.id)?.first,
                                legsTotal = state.openBets.slipLegsFor(match.id)?.second,
                            ),
                            onClick = { onMatchClick(match.id) },
                            onOddClick = { index ->
                                val market = match.primaryMarket() ?: return@MatchRow
                                market.outcomes.getOrNull(index)?.let {
                                    viewModel.toggleSelection(match, market.id, it.id)
                                }
                            },
                        )
                    }
                }
            }
        }

        // 9 — all events link
        item(key = "all-events") { AllEventsLink(state.sportFilter) }

        // 10 — special bets
        item(key = "special-header") { SectionHeader("Special bets") }
        item(key = "special") { SpecialBetsCard() }

        // 11 — arena preview
        item(key = "arena-header") { SectionHeader("PSK Arena", onSeeAll = {}) }
        items(arenaTips.take(5), key = { "arena-" + it.id }) { tip ->
            ArenaPreviewRow(tip, onCopy = { onCopyTip(tip.id) })
        }

        // 12 — footer
        item(key = "footer") {
            HomeFooter(onLink = { link ->
                if (link == "Responsible gaming" || link == "Self-exclusion form") {
                    onOpenResponsibleGaming()
                }
            })
        }
    }
}

@Composable
private fun AllEventsLink(sportId: String) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(eu.feg.ambient.ui.theme.PskShapes.card)
            .background(psk.surface)
            .clickable(role = Role.Button) { }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ALL EVENTS IN THE CATEGORY ",
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = sportId.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.positive,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = psk.textSecondary,
        )
    }
}

private data class PropsCard(
    val player: String,
    val fixture: String,
    val kickoff: String,
    val props: List<Pair<String, Double>>,
)

/** Lifted verbatim from home_page_00.jpg so the carousel reads as the real offer. */
private val PLAYER_PROPS = listOf(
    PropsCard(
        "Kylian Mbappe", "Betis — Real Madrid", "tomorrow 00:30",
        listOf("assists" to 3.60, "scores a goal" to 1.50, "3+ shots on goal" to 1.45),
    ),
    PropsCard(
        "Paris SG — player scores a goal", "Paris SG — Monaco", "tomorrow 00:35",
        listOf("Ousmane Dembele" to 1.85, "Ferran Torres" to 1.95, "Khvicha Kvaratskhelia" to 2.05),
    ),
    PropsCard(
        "Real Madrid — player scores a goal", "Betis — Real Madrid", "tomorrow 00:30",
        listOf("Vinicius Junior" to 2.30, "Jude Bellingham" to 3.20, "Yan Diomande" to 3.00),
    ),
)
