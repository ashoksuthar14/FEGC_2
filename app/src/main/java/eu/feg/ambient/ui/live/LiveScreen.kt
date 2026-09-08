package eu.feg.ambient.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ui.components.LeagueSection
import eu.feg.ambient.ui.components.MatchRow
import eu.feg.ambient.ui.components.TimeTabs
import eu.feg.ambient.ui.mapping.primaryMarket
import eu.feg.ambient.ui.mapping.toRowUi
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/** PRD section 5.2 — sport accordions over league sections over live rows. */
@Composable
fun LiveScreen(
    viewModel: LiveViewModel,
    modifier: Modifier = Modifier,
    onMatchClick: (String) -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val collapsedSports by viewModel.collapsedSportIds.collectAsStateWithLifecycle()
    val collapsedLeagues by viewModel.collapsedLeagueIds.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Live betting",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }
        item(key = "tabs") {
            TimeTabs(selected = state.timeTab, onSelect = viewModel::selectTimeTab)
        }

        state.sportGroups.forEach { group ->
            item(key = "sport-" + group.sport.id) {
                SportAccordionHeader(
                    name = group.sport.name,
                    count = group.liveCount,
                    expanded = group.sport.id !in collapsedSports,
                    onToggle = { viewModel.toggleSport(group.sport.id) },
                )
            }

            if (group.sport.id !in collapsedSports) {
                items(
                    count = group.leagues.size,
                    key = { index -> "league-" + group.leagues[index].league.id },
                ) { index ->
                    val leagueGroup = group.leagues[index]
                    // The tail of the list arrives collapsed, as on the live web page.
                    val defaultCollapsed = index >= 3
                    val explicitly = leagueGroup.league.id in collapsedLeagues
                    val expanded = if (defaultCollapsed) explicitly else !explicitly

                    LeagueSection(
                        name = leagueGroup.league.name,
                        count = leagueGroup.matches.size,
                        flag = leagueGroup.league.flag,
                        expanded = expanded,
                        onToggle = { viewModel.toggleLeague(leagueGroup.league.id) },
                    ) {
                        leagueGroup.matches.forEach { match ->
                            MatchRow(
                                match = match.toRowUi(
                                    now = state.now,
                                    selectedOutcomeIds = state.selectedOutcomeIds,
                                    oddsMoves = state.oddsMoves,
                                    market = match.primaryMarket(),
                                ),
                                onClick = { onMatchClick(match.id) },
                                onOddClick = { oddIndex ->
                                    val market = match.primaryMarket() ?: return@MatchRow
                                    market.outcomes.getOrNull(oddIndex)?.let {
                                        viewModel.toggleSelection(match, market.id, it.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportAccordionHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val psk = LocalPskColors.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "sportChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
        )
        Box(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = psk.textSecondary,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
    }
}
